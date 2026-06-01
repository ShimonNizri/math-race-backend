package com.example.math_race.race.bot_simulation;

import com.example.math_race.dto.wsMessage.response.ChangeTrackDTO;
import com.example.math_race.dto.wsMessage.response.MathQuestionDTO;
import com.example.math_race.race.PlayerTrackState;
import com.example.math_race.race.RaceManager;
import com.example.math_race.race.RacePlayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class BotRaceHandler extends StompSessionHandlerAdapter {

    private final RacePlayer player;
    private final RaceManager race;
    private final int skillLevel;

    private StompSession session;
    private StompSession.Subscription updatesSubscription;
    private StompSession.Subscription feedbackSubscription;

    private volatile boolean isTemporarilyUnsubscribed = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper mapper = new ObjectMapper();

    public BotRaceHandler(RaceManager race, RacePlayer player, int skillLevel) {
        this.race = race;
        this.player = player;
        this.skillLevel = skillLevel;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        this.session = session;
        subscribeToChannels();
        session.send("/app/race/" + race.getRoomCode() + "/player/sync", new HashMap<>());
    }

    private void subscribeToChannels() {
        if (session == null || !session.isConnected()) return;

        updatesSubscription = session.subscribe("/topic/race/" + race.getRoomCode() + "/updates", this);

        StompHeaders feedbackHeaders = new StompHeaders();
        feedbackHeaders.setDestination("/user/queue/race/feedback");
        if (player.getJoinToken() != null) {
            feedbackHeaders.add("Join-Token", player.getJoinToken());
        }
        feedbackSubscription = session.subscribe(feedbackHeaders, this);

        isTemporarilyUnsubscribed = false;
    }

    private void unsubscribeFromChannels() {
        isTemporarilyUnsubscribed = true;
        if (updatesSubscription != null) {
            updatesSubscription.unsubscribe();
            updatesSubscription = null;
        }
        if (feedbackSubscription != null) {
            feedbackSubscription.unsubscribe();
            feedbackSubscription = null;
        }
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return JsonNode.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        if (payload == null || isTemporarilyUnsubscribed) {
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
            case "RACE_RESUMED":
                handleRaceResumed();
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

        if (ThreadLocalRandom.current().nextInt(15) == 0) {
            unsubscribeFromChannels();

            int reconnectDelay = ThreadLocalRandom.current().nextInt(2, 6);

            scheduler.schedule(() -> {
                subscribeToChannels();
                handleRaceResumed();
            }, reconnectDelay, TimeUnit.SECONDS);

            return;
        }

        if (player.isCanAskHint() || player.isGotHint()) {
            if (ThreadLocalRandom.current().nextInt(6) == 0) {
                int hintDelaySeconds = ThreadLocalRandom.current().nextInt(1, 3);
                scheduler.schedule(() -> {
                    if (session != null && session.isConnected() && !isTemporarilyUnsubscribed) {
                        session.send("/app/race/" + race.getRoomCode() + "/player/hint", new HashMap<>());
                    }
                }, hintDelaySeconds, TimeUnit.SECONDS);
            }
        }

        JsonNode options = data.get("options");
        if (options.isArray() && options.size() > 0) {

            String selectedAnswer = "";
            int roll = ThreadLocalRandom.current().nextInt(100);

            if (roll < skillLevel && player.getCurrentQuestion() != null) {
                selectedAnswer = player.getCurrentQuestion().getCorrectAnswer();
            } else {
                int randomIndex = ThreadLocalRandom.current().nextInt(options.size());
                selectedAnswer = options.get(randomIndex).asText();
            }

            int delaySeconds = ThreadLocalRandom.current().nextInt(3, 7);
            final String finalAnswer = selectedAnswer;

            scheduler.schedule(() -> {
                if (session != null && session.isConnected() && !isTemporarilyUnsubscribed) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("answer", finalAnswer);
                    session.send("/app/race/" + race.getRoomCode() + "/player/submit", payload);
                }
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void handleJunction(JsonNode data) {
        if (data == null) return;

        String offer1 = data.has("offer1") ? data.get("offer1").asText() : null;
        String offer2 = data.has("offer2") ? data.get("offer2").asText() : null;

        if (offer1 != null && offer2 != null) {
            String choice = ThreadLocalRandom.current().nextBoolean() ? offer1 : offer2;
            int delaySeconds = ThreadLocalRandom.current().nextInt(1, 4);

            scheduler.schedule(() -> {
                if (session != null && session.isConnected() && !isTemporarilyUnsubscribed) {
                    Map<String, String> payload = new HashMap<>();
                    payload.put("choice", choice);
                    session.send("/app/race/" + race.getRoomCode() + "/player/junction/choose", payload);
                }
            }, delaySeconds, TimeUnit.SECONDS);
        }
    }

    private void handleRaceResumed() {
        if (race.getStatus().isRunning()) {
            if (player.getCurrentQuestion() != null) {
                MathQuestionDTO questionDTO = new MathQuestionDTO(race, player, player.getCurrentQuestion());
                JsonNode dataNode = mapper.valueToTree(questionDTO);
                handleNewQuestion(dataNode);
            } else if (player.getTrackState() != null && player.getTrackState().equals(PlayerTrackState.WAITING_FOR_CHOICE)) {
                ChangeTrackDTO junctionChoose = new ChangeTrackDTO(player);
                JsonNode dataNode = mapper.valueToTree(junctionChoose);
                handleJunction(dataNode);
            }
        }
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
        // ok
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        scheduler.shutdown();
    }
}
