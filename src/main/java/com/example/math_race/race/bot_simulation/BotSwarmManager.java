package com.example.math_race.race.bot_simulation;

import com.example.math_race.dto.http.request.JoinRaceRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.race.RaceManager;
import com.example.math_race.race.RacePlayer;
import com.example.math_race.service.AuthService;
import com.example.math_race.service.RaceService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BotSwarmManager {

    private final WebSocketStompClient stompClient;
    private final String serverUrl = "ws://10.36.92.56:8085";

    private final AtomicInteger guestCounter = new AtomicInteger(0);
    private final AtomicInteger registeredCounter = new AtomicInteger(0);

    private final AuthService authService;
    private final RaceService raceService;

    public BotSwarmManager(@Lazy AuthService authService, @Lazy RaceService raceService) {
        this.authService = authService;
        this.raceService = raceService;

        this.stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    public void deployBotsToRoom(RaceManager race, int guestCount, int registeredCount) {
        for (int i = 0; i < guestCount; i++) {
            try {
                int currentCount = guestCounter.getAndIncrement();
                String botName = "Bot_" + currentCount;

                String guestToken = authService.createGuestToken().getGuestToken();

                RequestMetadata metadata = new RequestMetadata();
                metadata.setGuestToken(guestToken);

                raceService.joinRace(race.getRoomCode(), new JoinRaceRequest(botName), metadata);
                RacePlayer player = (RacePlayer) raceService.findAccountByIdInOpenRace(authService.getGuestIdByToken(guestToken));

                int skillLevel = ThreadLocalRandom.current().nextInt(10, 101);

                launchSingleBot(race, guestToken, null, player, skillLevel);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        for (int i = 0; i < registeredCount; i++) {

            try {
                int currentCount = registeredCounter.getAndIncrement();
                int botIndex = (currentCount % BotDataSeeder.TOTAL_BOTS_TO_CREATE) + 1;

                String botName = "Bot_" + botIndex;
                TokenEntity authToken = authService.generateTokenForBot(botIndex);

                RequestMetadata metadata = new RequestMetadata();
                metadata.setAuthorization(authToken.getToken());

                raceService.joinRace(race.getRoomCode(), new JoinRaceRequest(botName), metadata);
                RacePlayer player = (RacePlayer) raceService.findAccountByIdInOpenRace(authToken.getUser().getId().toString());

                int skillLevel = ThreadLocalRandom.current().nextInt(10, 101);

                launchSingleBot(race, null, authToken.getToken(), player, skillLevel);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void launchSingleBot(RaceManager race, String guestToken, String authToken, RacePlayer player, int skillLevel) {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Is-Recovery", "false");

        if (authToken != null) {
            connectHeaders.add("Authorization", "Bearer " + authToken);
        } else if (guestToken != null) {
            connectHeaders.add("GuestToken", guestToken);
        }

        WebSocketHttpHeaders webSocketHttpHeaders = new WebSocketHttpHeaders();
        BotRaceHandler botHandler = new BotRaceHandler(race, player, skillLevel);

        stompClient.connect(serverUrl + "/api/ws-race", webSocketHttpHeaders, connectHeaders, botHandler);
    }
}
