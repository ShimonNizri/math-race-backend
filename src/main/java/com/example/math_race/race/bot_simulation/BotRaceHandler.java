package com.example.math_race.race.bot_simulation;

import com.example.math_race.race.RacePlayer;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotRaceHandler extends StompSessionHandlerAdapter {

    private final String roomCode;
    private final String joinToken;
    private final RacePlayer player;
    private final int skillLevel; // אחוז ההצלחה של הבוט (10-100)

    private StompSession session;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    public BotRaceHandler(String roomCode, String joinToken, RacePlayer player, int skillLevel) {
        this.roomCode = roomCode;
        this.joinToken = joinToken;
        this.player = player;
        this.skillLevel = skillLevel;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        this.session = session;

        session.subscribe("/topic/race/" + roomCode + "/updates", this);

        StompHeaders feedbackHeaders = new StompHeaders();
        feedbackHeaders.setDestination("/user/queue/race/feedback");
        if (joinToken != null) {
            feedbackHeaders.add("Join-Token", joinToken);
        }
        session.subscribe(feedbackHeaders, this);

        session.send("/app/race/" + roomCode + "/player/sync", new HashMap<>());
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return JsonNode.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        if (payload == null) {
            return;
        }

        JsonNode jsonNode = (JsonNode) payload;

        if (!jsonNode.has("type")) {
            return;
        }

        String type = jsonNode.get("type").asText();
        JsonNode data = jsonNode.has("data") ? jsonNode.get("data") : null;

        switch (type) {
            case "NEW_QUESTION":
                handleNewQuestion(data);
                break;
            case "JUNCTION_OFFERED":
                handleJunction(data);
                break;
            case "RACE_CANCELLED":
            case "RACE_COMPLETED":
                scheduler.shutdown();
                break;
            default:
                break;
        }
    }

    private void handleNewQuestion(JsonNode data) {
        if (data == null || !data.has("options")) return;

        JsonNode options = data.get("options");
        if (options.isArray() && options.size() > 0) {

            String selectedAnswer = "";

            int roll = random.nextInt(100);

            if (roll < skillLevel && player.getCurrentQuestion() != null) {
                selectedAnswer = player.getCurrentQuestion().getCorrectAnswer();
            } else {
                int randomIndex = random.nextInt(options.size());
                selectedAnswer = options.get(randomIndex).asText();
            }

            int delaySeconds = 2 + random.nextInt(4);
            final String finalAnswer = selectedAnswer;

            scheduler.schedule(() -> {
                if (session != null && session.isConnected()) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("answer", finalAnswer);
                    session.send("/app/race/" + roomCode + "/player/submit", payload);
                }
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void handleJunction(JsonNode data) {
        if (data == null) return;

        String offer1 = data.has("offer1") ? data.get("offer1").asText() : null;
        String offer2 = data.has("offer2") ? data.get("offer2").asText() : null;

        if (offer1 != null && offer2 != null) {
            String choice = random.nextBoolean() ? offer1 : offer2;
            int delaySeconds = 1 + random.nextInt(3);

            scheduler.schedule(() -> {
                if (session != null && session.isConnected()) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("choice", choice);
                    session.send("/app/race/" + roomCode + "/player/junction/choose", payload);
                }
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        //ok
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        //ok
        scheduler.shutdown();
    }
}
