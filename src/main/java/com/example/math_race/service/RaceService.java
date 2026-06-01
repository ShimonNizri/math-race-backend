package com.example.math_race.service;

import com.example.math_race.dto.http.request.CreateRaceRequest;
import com.example.math_race.dto.http.request.JoinRaceRequest;
import com.example.math_race.dto.http.request.PublicRacesListRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.dto.http.response.CreateRaceResponse;
import com.example.math_race.dto.http.response.JoinRaceResponse;
import com.example.math_race.dto.http.response.PlayerRemoveDTO;
import com.example.math_race.dto.http.response.RaceInfoResponse;
import com.example.math_race.dto.wsMessage.request.*;
import com.example.math_race.dto.wsMessage.response.*;
import com.example.math_race.entities.UserEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.race.*;
import com.example.math_race.race.bot_simulation.BotRaceHandler;
import com.example.math_race.race.bot_simulation.BotSwarmManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.example.math_race.service.WebSocketService.*;

@Service
public class RaceService {

    private final AuthService authService;
    private final WebSocketService webSocketService;
    private final RaceEngineService raceEngineService;
    private final BotSwarmManager botSwarmManager;
    private final Map<String, RaceManager> allRaces =  new ConcurrentHashMap<>();
    private final Map<String, String> accountIdToOpenRoomCode = new ConcurrentHashMap<>();

    @Autowired
    public RaceService(BotSwarmManager botSwarmManager, AuthService authService, WebSocketService webSocketService, RaceEngineService raceEngineService) {
        this.authService = authService;
        this.webSocketService = webSocketService;
        this.raceEngineService = raceEngineService;
        this.botSwarmManager = botSwarmManager;
    }

    public CreateRaceResponse creatRace(CreateRaceRequest request, RequestMetadata metadata){
        UserEntity user = authService.getValidUser(metadata);

        if (findAccountByIdInOpenRace(user.getId().toString()) != null) {
            throw new LogicException(ErrorCode.USER_ALREADY_IN_RACE);
        }

        String nickname = request.getNickname() != null && !request.getNickname().isEmpty() &&
                request.getNickname().trim().length() >= 3 && request.getNickname().trim().length() <= 20 ?
                request.getNickname() : user.getUsername();

        RaceSettings raceSettings = new RaceSettings(request.getName(), request.getTargetScore(),request.isPrivate());

        String joinToken = UUID.randomUUID().toString().substring(0, 10);

        RaceManager raceManager = new RaceManager(raceSettings);
        raceManager.setHost(new RaceHost(user,null,joinToken,nickname));

        while (allRaces.containsKey(raceManager.getRoomCode())){
            raceManager.updateRoomCode();
        }
        accountIdToOpenRoomCode.put(user.getId().toString(), raceManager.getRoomCode());
        allRaces.put(raceManager.getRoomCode(), raceManager);


        if (user.getRole().equals(UserEntity.UserRole.ADMIN) && raceManager.getSettings().getRaceName().equals("test_bot")){
            try {
                botSwarmManager.deployBotsToRoom(raceManager, 6, 6);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return new CreateRaceResponse(
                raceSettings.getRaceName(),
                raceManager.getRoomCode(),
                raceSettings.getTargetScore(),
                nickname,
                joinToken
        );
    }

    public List<RaceInfoResponse> getActivePublicRaces(PublicRacesListRequest request) {
        long skipCount = (long) request.getPage() * request.getSize();

        return allRaces.values().stream()
                .filter(race -> !race.getSettings().isPrivate() && race.getStatus().equals(RaceStatus.PENDING))
                .sorted(Comparator.comparingLong(RaceManager::getCreatedAtMs).reversed())
                .skip(skipCount)
                .limit(request.getSize())
                .map(RaceInfoResponse::new)
                .collect(Collectors.toList());
    }

    public JoinRaceResponse joinRace(String roomCode, JoinRaceRequest request, RequestMetadata metadata){
        String accountId, nickname;
        UserEntity user = authService.getActiveUserByToken(metadata);

        if (user == null) {
            accountId = getGuestIdByToken(metadata);
            nickname = StringUtils.hasText(request.getNickname()) ? request.getNickname() : createNickname();
        } else {
            accountId =  user.getId().toString();
            nickname = StringUtils.hasText(request.getNickname()) ?
                    request.getNickname() : user.getUsername();
        }

        RaceManager raceManager = findOpenRaceByAccountId(accountId);
        boolean isHost = false;
        String joinToken = UUID.randomUUID().toString().substring(0, 10);

        if (raceManager == null){
            raceManager = findRaceByRoomCode(roomCode);
            if (raceManager == null || raceManager.getStatus().isClosed()){
                throw new LogicException(ErrorCode.RACE_NOT_FOUND);
            }
            if (!raceManager.getStatus().equals(RaceStatus.PENDING)){
                throw new LogicException(ErrorCode.RACE_ALREADY_STARTED);
            }

            raceManager.joinRace(new RacePlayer(accountId,user,null,joinToken,nickname));
            accountIdToOpenRoomCode.put(accountId, raceManager.getRoomCode());

        } else if (raceManager.getRoomCode().equals(roomCode)){
            RaceAccount account = raceManager.getAccount(accountId);

            if (StringUtils.hasText(request.getNickname())) {
                account.setNickname(nickname);
            }

            account.setJoinToken(joinToken);
            isHost = raceManager.isHost(accountId);

        }else {
            throw new LogicException(ErrorCode.USER_ALREADY_IN_RACE);
        }

        if (!isHost) {
            sendPlayerJoined(raceManager, accountId);
        }

        return new JoinRaceResponse(nickname,joinToken,raceManager.getSettings().getRaceName(),
                raceManager.getRoomCode(),isHost ? "HOST" : "PLAYER",raceManager.getSettings().getTargetScore());
    }

    public void handleJunctionChoose(String roomCode, JunctionChooseRequest request, StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (race.getStatus().isRunning()){
            raceEngineService.handleJunctionChoice(race,race.getPlayer(accessor.getUser().getName()),request.getChoice());
        }
    }

    public void handleSubmitQuestion(String roomCode, SubmitQuestionRequest request, StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (race.getStatus().isRunning()){
            raceEngineService.processPlayerAnswer(race,race.getPlayer(accessor.getUser().getName()),request.getAnswer());
        }
    }

    public void handleStartRace(String roomCode, StompHeaderAccessor accessor){
        RaceManager raceManager = findOpenRaceByRoomCode(roomCode);
        if (!raceManager.getStatus().equals(RaceStatus.PENDING)){
            webSocketService.sendErrorToQueueSession(QUEUE_RACE_HOST,ErrorCode.RACE_ALREADY_INITIALIZED,accessor);
        }

        if (raceManager.getPlayers().size() < 2){
            webSocketService.sendErrorToQueueSession(QUEUE_RACE_HOST,ErrorCode.NOT_ENOUGH_PLAYERS_TO_START,accessor);
        }

        raceEngineService.startRace(raceManager);
    }

    public void checkJoinedRace(StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByAccountId(accessor.getUser().getName());

        if (race != null) {
            webSocketService.sendSuccessToQueueSession(QUEUE_NOTIFICATIONS, "JOINED_RACE_FOUND",
                    new RaceInfoResponse(race,race.getAccount(accessor.getUser().getName())), accessor);
        } else {
            webSocketService.sendSuccessToQueueSession(QUEUE_NOTIFICATIONS, "NO_JOINED_RACE_FOUND",
                    null,accessor);
        }
    }

    public void sendRaceState(String roomCode, StompHeaderAccessor accessor){
        RaceManager raceManager = findRaceByRoomCode(roomCode);
        boolean isHost = raceManager.isHost(accessor.getUser().getName());

        webSocketService.sendSuccessToQueueSession(isHost ? QUEUE_RACE_HOST : QUEUE_RACE_FEEDBACK, "RACE_FULL_STATE",
                new RaceStateDTO(raceManager, raceManager.getAccount(accessor.getUser().getName())),accessor);
    }

    public void removeRace(RaceManager raceManager){
        allRaces.remove(raceManager.getRoomCode());
        accountIdToOpenRoomCode.remove(raceManager.getHost().getId());
        for (RacePlayer player : raceManager.getPlayers().values()){
            accountIdToOpenRoomCode.remove(player.getId());
        }
    }

    public void sendPlayerJoined(RaceManager race, String accountId){
        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                "PLAYER_JOINED",new PlayerJoinedDTO(race,race.getPlayer(accountId)));
    }

    public void kickPlayerFromRace(String roomCode, KickPlayerRequest kickPlayer){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (!race.getStatus().isClosed()){
            RacePlayer player = race.getPlayers().remove(kickPlayer.getPlayerId());
            if (player != null){
                raceEngineService.removeTimerForPlayer(player);
                accountIdToOpenRoomCode.remove(player.getId());
                webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                        "PLAYER_KICKED",new PlayerRemoveDTO(player));

                webSocketService.removeSession(player.getId(),player.getSessionActive(),ErrorCode.PLAYER_KICKED);
            }
        }
    }

    public void playerLeftFromRace(String roomCode, StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (!race.getStatus().isClosed()){
            RacePlayer player = race.getPlayers().remove(accessor.getUser().getName());

            if (player != null){
                raceEngineService.removeTimerForPlayer(player);
                accountIdToOpenRoomCode.remove(player.getId());
                webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                        "PLAYER_LEFT",new PlayerRemoveDTO(player));

                webSocketService.removeSession(player.getId(),player.getSessionActive(),ErrorCode.PLAYER_LEFT);
            }
        }
    }

    public void changeNickname(String roomCode, ChangeNicknameRequest request, StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (!race.getStatus().isClosed()){
            RaceAccount account = race.getAccount(accessor.getUser().getName());

            if (account != null){
                account.setNickname(request.getNickname());
                webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                        "CHANGE_NICKNAME",  new AccountConnectionDTO(account));
            }
        }
    }

    public void changeRaceName(String roomCode, ChangeRaceNameRequest request) {
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (!race.getStatus().isClosed()) {
            race.getSettings().setRaceName(request.getRaceName());

            webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                    "CHANGE_RACE_NAME", new ChangeRaceNameDTO(race));
        }
    }


    public void playerAskForHint(String roomCode, StompHeaderAccessor accessor){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        RacePlayer player = race.getPlayer(accessor.getUser().getName());

        if (player != null && race.getStatus().isRunning()){
            if (player.isCanAskHint() || player.isGotHint()){
                HintReceivedDTO hintReceivedDTO = new HintReceivedDTO(player);

                webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK,"QUESTION_HINT",
                        hintReceivedDTO,
                        player.getId(), player.getSessionActive());

                webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST,"QUESTION_HINT",
                        hintReceivedDTO,
                        race.getHost().getId(), race.getHost().getSessionActive());

                player.setCanAskHint(false);
                if (!player.isGotHint()) {
                    player.setGotHint(true);
                    player.addHintsReceived();
                }
            }
        }
    }

    public void sendMessageToPlayer(String roomCode, MessageToPlayerRequest sendMessage){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        RacePlayer player = race.getPlayer(sendMessage.getPlayerId());
        if (player != null){
            MessageDTO message =  new MessageDTO("HOST",player.getId(),sendMessage.getMessage());
            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK,"HOST_MESSAGE",
                    message,
                    player.getId(), player.getSessionActive());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "NEW_HOST_MESSAGE",
                    message,
                    race.getHost().getId(), race.getHost().getSessionActive());
        }
    }

    public void pauseRace(String roomCode){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (race.getStatus().isRunning()){
            raceEngineService.pauseRace(race);
        }
    }

    public void resumeRace(String roomCode){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (race.getStatus().equals(RaceStatus.PAUSED)){
            raceEngineService.resumeRace(race);
        }
    }

    public void cancelRace(String roomCode){
        RaceManager race = findOpenRaceByRoomCode(roomCode);
        if (!race.getStatus().isClosed()){
            raceEngineService.cancelledRace(race);
        }
    }

    public RaceAccount findAccountByIdInOpenRace(String accountId) {
        if (accountId == null) return null;

        String roomCode = accountIdToOpenRoomCode.get(accountId);
        if (roomCode == null) return null;

        RaceManager race = allRaces.get(roomCode);

        if (race != null && race.getStatus().isOpen()) {
            return race.getAccount(accountId);
        }

        return null;
    }

    public List<RaceAccount> findAccountByIdInClosedRace(String accountId) {
        if (accountId == null) return Collections.emptyList();

        return allRaces.values().stream()
                .filter(race -> race.getStatus().isClosed())
                .map(race -> race.getAccount(accountId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<RaceAccount> findAccountById(String accountId) {
        if (accountId == null) return Collections.emptyList();

        return allRaces.values().stream()
                .map(race -> race.getAccount(accountId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public RaceManager findRaceByAccountId(String accountId) {
        if (accountId == null) return null;

        return allRaces.values().stream()
                .filter(race ->
                        Objects.equals(race.getHost().getId(), accountId) ||
                                race.getPlayer(accountId) != null
                )
                .findFirst()
                .orElse(null);
    }

    public RaceManager findOpenRaceByAccountId(String accountId) {
        if (accountId == null) return null;

        String roomCode = accountIdToOpenRoomCode.get(accountId);
        if (roomCode == null) return null;

        RaceManager race = allRaces.get(roomCode);
        return (race != null && race.getStatus().isOpen()) ? race : null;
    }

    public List<RaceManager> findClosedRacesByAccountId(String accountId) {
        if (accountId == null) return Collections.emptyList();

        return allRaces.values().stream()
                .filter(race -> race.getStatus().isClosed())
                .filter(race -> race.isAccountIn(accountId))
                .collect(Collectors.toList());
    }

    public List<RaceManager> findAllRacesByAccountId(String accountId) {
        if (accountId == null) return Collections.emptyList();

        return allRaces.values().stream()
                .filter(race -> race.isAccountIn(accountId))
                .collect(Collectors.toList());
    }

    public RaceManager findOpenRaceByRoomCode(String roomCode) {
        RaceManager race = allRaces.get(roomCode);
        return (race != null && race.getStatus().isOpen()) ? race : null;
    }

    public RaceManager findClosedRaceByRoomCode(String roomCode) {
        RaceManager race = allRaces.get(roomCode);
        return (race != null && race.getStatus().isClosed()) ? race : null;
    }

    public RaceManager findRaceByRoomCode(String roomCode) {
        return allRaces.get(roomCode);
    }

    public String getGuestIdByToken(RequestMetadata metadata) {
        String guestToken = metadata.getGuestToken();
        if (!StringUtils.hasText(guestToken)) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        String guestId = authService.getGuestIdByToken(guestToken);
        if (guestId == null) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        return guestId;
    }

    public String createNickname(){
        return "player " + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
