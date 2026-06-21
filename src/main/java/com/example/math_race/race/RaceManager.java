package com.example.math_race.race;

import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.questionGenerator.question.TemplateResult;
import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Data
public class RaceManager {
    public static final int MAX_PLAYERS = 20;

    private final RaceSettings settings;
    private String roomCode;

    private Map<String, TemplateResult<MathQuestion>> allMathQuestions = new ConcurrentHashMap<>();

    private RaceHost host;
    private final Map<String, RacePlayer> players;
    private RaceStatistics statistics;

    private UUID id;
    private RaceStatus status;
    private long remainingTimeMs;     // כמה זמן יש לשמשחק
    private long lastResumedAtMs;// חותמת הזמן של הפעלת השעון האחרונה
    private long lastPausedAtMs; // חותמת הזמן של תחילת העצירה הנוכחית
    private final long createdAtMs;
    private long endedAtMs;
    private long totalPausedDurationTimeMs;

    public RaceManager(RaceSettings raceSettings) {
        this.id = UUID.randomUUID();
        this.status = RaceStatus.PENDING;

        this.settings = raceSettings;
        updateRoomCode();

        this.players = new ConcurrentHashMap<>();
        this.statistics = new RaceStatistics(this);

        this.remainingTimeMs = raceSettings.getTotalDurationTimeMs();
        this.lastResumedAtMs = 0;
        this.lastPausedAtMs = 0;
        this.totalPausedDurationTimeMs = 0;
        this.createdAtMs = System.currentTimeMillis();
    }

    public void joinRace(RacePlayer player) {
        if (players.size() >= MAX_PLAYERS && !players.containsKey(player.getId())) {
            throw new LogicException(ErrorCode.RACE_MAX_PLAYERS_EXCEEDED);
        }

        RacePlayer existingPlayer = players.get(player.getId());

        if (existingPlayer != null) {
            existingPlayer.setSessionActive(player.getSessionActive());
            existingPlayer.setJoinToken(player.getJoinToken());
            existingPlayer.setNickname(player.getNickname());
        } else {
            players.put(player.getId(), player);
        }
    }

    public RacePlayer getPlayer(String playerId) {
        return players.get(playerId);
    }

    public RaceAccount getAccount(String accountId) {
        if (accountId == null) return null;

        if (isHost(accountId)) {
            return host;
        }
        return getPlayer(accountId);
    }

    public long getCalculatedRemainingTime() {
        if (this.status != RaceStatus.IN_PROGRESS) {
            return this.remainingTimeMs;
        }

        long timeElapsedSinceResume = System.currentTimeMillis() - this.lastResumedAtMs;
        long actualRemaining = this.remainingTimeMs - timeElapsedSinceResume;

        return Math.max(0, actualRemaining);
    }

    public boolean isAccountIn(String accountId) {
        if (accountId == null) return false;
        return players.containsKey(accountId) || isHost(accountId);
    }

    public void finalizeCurrentPause() {
        if (this.lastPausedAtMs > 0) {
            long duration = System.currentTimeMillis() - this.lastPausedAtMs;
            this.totalPausedDurationTimeMs += duration;
            this.lastPausedAtMs = 0;
        }
    }

    public void updateStatistics() {
        this.statistics = new RaceStatistics(this);
    }

    public List<RacePlayer> getRankedPlayers() {
        return this.players.values().stream()
                .sorted(Comparator.comparingInt(RacePlayer::getCurrentScore).reversed())
                .collect(Collectors.toList());
    }

    public boolean isHost(String accountId) {
        return host != null && Objects.equals(host.getId(), accountId);
    }

    public boolean isRoomCode(String roomCode) {
        return this.roomCode.equals(roomCode);
    }

    public void updateRoomCode(){
        this.roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
