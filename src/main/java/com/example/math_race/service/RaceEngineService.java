package com.example.math_race.service;

import com.example.math_race.dto.wsMessage.response.*;
import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.questionGenerator.QuestionEngine;
import com.example.math_race.questionGenerator.question.TemplateResult;
import com.example.math_race.race.*;
import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.repositories.QuestionErrorReportRepository;
import com.example.math_race.repositories.RaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static com.example.math_race.config.GameSchedulerConfig.GAME_SCHEDULER_BEAN_NAME;
import static com.example.math_race.service.WebSocketService.*;

@Service
public class RaceEngineService {

    private final ThreadPoolTaskScheduler scheduler;
    private final WebSocketService webSocketService;
    private final QuestionEngine questionEngine;
    private final QuestionTemplateService  questionTemplateService;
    private final RaceRepository raceRepository;
    private final RandomEventEngine randomEventEngine;
    private final RaceService raceService;
    private final QuestionErrorReportRepository  questionErrorReportRepository;

    private final Map<String, ScheduledFuture<?>> raceEndTimers = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> playerQuestionTimers = new ConcurrentHashMap<>();

    @Autowired
    public RaceEngineService(
            @Qualifier(GAME_SCHEDULER_BEAN_NAME) ThreadPoolTaskScheduler scheduler, WebSocketService webSocketService,
            QuestionEngine questionEngine, RaceRepository raceRepository, RandomEventEngine randomEventEngine,
            QuestionErrorReportRepository questionErrorReportRepository,
            QuestionTemplateService  questionTemplateService, @Lazy RaceService raceService) {

        this.scheduler = scheduler;
        this.webSocketService = webSocketService;
        this.raceRepository = raceRepository;
        this.randomEventEngine = randomEventEngine;
        this.raceService = raceService;
        this.questionEngine = questionEngine;
        this.questionTemplateService = questionTemplateService;
        this.questionErrorReportRepository = questionErrorReportRepository;
    }

    public void startRace(RaceManager race) {
        if (race == null || !race.getStatus().equals(RaceStatus.PENDING)) return;

        race.setStatus(RaceStatus.IN_PROGRESS);
        race.setLastResumedAtMs(System.currentTimeMillis());

        Instant endTime = Instant.now().plusMillis(race.getRemainingTimeMs());
        ScheduledFuture<?> endTask = scheduler.schedule(() -> finishRace(race), Date.from(endTime));

        raceEndTimers.put(race.getId().toString(), endTask);

        StatusChangedDTO statusChangedDTO = new StatusChangedDTO(race);
        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),
                "RACE_START", statusChangedDTO);

        for (RacePlayer player : race.getPlayers().values()) {
            processNextStep(race, player);
        }
    }

    public void processNextStep(RaceManager race, RacePlayer player) {
        if (!race.isAccountIn(player.getId()) || !race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getCurrentQuestion() != null || (player.getTrackState() == PlayerTrackState.WAITING_FOR_CHOICE)) {
                return;
            }

            boolean update = false;

            if (player.getTrackState() == PlayerTrackState.AUTOSTRADA || player.getTrackState() == PlayerTrackState.DIRT_ROAD) {
                if (player.getSpecialQuestionsRemaining() > 0) {
                    scheduler.schedule(() -> sendNextQuestionToPlayer(race, player),
                            Date.from(Instant.now().plusMillis(700)));
                    return;
                } else {
                    player.setTrackState(PlayerTrackState.REGULAR);

                    ChangeTrackDTO trackDTO = new ChangeTrackDTO(player);
                    update = true;
                    scheduler.schedule(() -> {

                        webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "TRACK_STATE_CHANGED", trackDTO,
                                player.getId(), player.getSessionActive());

                        webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "TRACK_STATE_CHANGED_FOR_PLAYER", trackDTO,
                                race.getHost().getId(), race.getHost().getSessionActive());
                    },Date.from(Instant.now().plusMillis(700)));
                }
            }

            if (player.getTrackState() == PlayerTrackState.REGULAR) {
                boolean shouldOfferJunction = randomEventEngine.shouldTriggerJunction(player, race);

                if (shouldOfferJunction) {
                    scheduler.schedule(() -> sendJunctionOfferToPlayer(race, player),
                            Date.from(Instant.now().plusMillis(update ? 1800 : 700)));
                } else {
                    scheduler.schedule(() -> sendNextQuestionToPlayer(race, player),
                            Date.from(Instant.now().plusMillis(update ? 1800 : 700)));
                }
            }
        }
    }

    public void sendJunctionOfferToPlayer(RaceManager race, RacePlayer player) {
        if (!race.isAccountIn(player.getId()) || !race.getStatus().isRunning()) return;

        synchronized (player) {
            player.setTrackState(PlayerTrackState.WAITING_FOR_CHOICE);
            player.setQuestionStartTimeAtMs(System.currentTimeMillis());
            player.setQuestionRemainingTimeMs(player.getTrackState().getTimeLimitMillis());

            JunctionOfferDTO offerDTO = new JunctionOfferDTO(race,player);

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "JUNCTION_OFFERED", offerDTO,
                    player.getId(), player.getSessionActive());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "JUNCTION_OFFERED_TO_PLAYER", offerDTO,
                    race.getHost().getId(), race.getHost().getSessionActive());

            ScheduledFuture<?> timeoutTask = scheduler.schedule(
                    () -> handleJunctionTimeout(race, player),
                    Date.from(Instant.now().plusMillis(player.getTrackState().getTimeLimitMillis()).plusMillis(700))
            );

            playerQuestionTimers.put(player.getId(), timeoutTask);
        }
    }

    public void handleJunctionChoice(RaceManager race, RacePlayer player, String choice) {
        if (!race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getTrackState() != PlayerTrackState.WAITING_FOR_CHOICE) return;
            player.addJunctionsOfferedCount();
            player.snapshotJunctionState();


            ScheduledFuture<?> timer = playerQuestionTimers.remove(player.getId());
            if (timer != null) timer.cancel(false);

            if (PlayerTrackState.AUTOSTRADA.name().equalsIgnoreCase(choice)) {
                player.setTrackState(PlayerTrackState.AUTOSTRADA);
                player.addAutostradaChoices();
            } else {
                player.setTrackState(PlayerTrackState.DIRT_ROAD);
                player.addDirtRoadChoices();
            }

            player.setSpecialQuestionsRemaining(player.getTrackState().getQuestionsNumber());
            ChangeTrackDTO junctionChoose = new ChangeTrackDTO(player);


            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "JUNCTION_CHOOSE", junctionChoose,
                    player.getId(), player.getSessionActive());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "JUNCTION_CHOOSE_FOR_PLAYER", junctionChoose,
                    race.getHost().getId(), race.getHost().getSessionActive());


            processNextStep(race, player);
        }
    }

    private void handleJunctionTimeout(RaceManager race, RacePlayer player) {
        if (!race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getTrackState() != PlayerTrackState.WAITING_FOR_CHOICE) {
                return;
            }

            player.addJunctionsOfferedCount();
            player.snapshotJunctionState();

            playerQuestionTimers.remove(player.getId());

            player.setTrackState(PlayerTrackState.DIRT_ROAD);
            player.setSpecialQuestionsRemaining(player.getTrackState().getQuestionsNumber());
            player.addDirtRoadChoices();

            ChangeTrackDTO junctionChoose = new ChangeTrackDTO(player);

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "JUNCTION_TIMEOUT", junctionChoose,
                    player.getId(), player.getSessionActive());
            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "JUNCTION_TIMEOUT_FOR_PLAYER", junctionChoose,
                    race.getHost().getId(), race.getHost().getSessionActive());

            processNextStep(race, player);
        }
    }


    public void sendNextQuestionToPlayer(RaceManager race, RacePlayer player) {
        if (!race.isAccountIn(player.getId()) || !race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getCurrentQuestion() != null) {
                return;
            }

            ScheduledFuture<?> existingTimer = playerQuestionTimers.remove(player.getId());
            if (existingTimer != null) {
                existingTimer.cancel(false);
            }

            MathQuestion question = generateForPlayer(race,player);

            player.setCurrentQuestion(question);
            player.setQuestionStartTimeAtMs(System.currentTimeMillis());
            player.setQuestionRemainingTimeMs(player.getTrackState().getTimeLimitMillis());
            player.setGotHint(false);
            player.setCanAskHint(randomEventEngine.shouldGiveHint(player,race));

            MathQuestionDTO questionDTO = new MathQuestionDTO(race, player, question);
            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "NEW_QUESTION", questionDTO,
                    player.getId(), player.getSessionActive());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "QUESTION_SENT", questionDTO,
                    race.getHost().getId(), race.getHost().getSessionActive());

            Instant timeoutTime = Instant.now().plusMillis(player.getTrackState().getTimeLimitMillis());
            ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> handleQuestionTimeout(race, player, question), Date.from(timeoutTime));

            playerQuestionTimers.put(player.getId(), timeoutTask);
        }
    }

    public void processPlayerAnswer(RaceManager race, RacePlayer player, String answer) {
        if (!race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getCurrentQuestion() == null || player.getTrackState().equals(PlayerTrackState.WAITING_FOR_CHOICE)) {
                return;
            }

            ScheduledFuture<?> timer = playerQuestionTimers.remove(player.getId());
            if (timer != null) {
                timer.cancel(false);
            }

            long timeSpent = player.getQuestionTimeSpent();
            if (!player.getTrackState().equals(PlayerTrackState.REGULAR)) {
                player.subSpecialQuestionsRemaining();
            }else {
                player.addRegularTimeMs(timeSpent);
                player.addRegularAttempt();
            }


            boolean isCorrect = player.checkAnswer(answer);
            int addScore;
            if (isCorrect) {
                addScore = player.getTrackState().getScore();
                if (player.getTrackState().equals(PlayerTrackState.REGULAR)) {
                    player.addRegularSuccess();
                    player.addRegularStreak(1);
                    player.addRegularSuccessTimeMs(timeSpent);
                    if (player.getCurrentRegularStreak() > player.getMaxRegularStreak()) {
                        player.setMaxRegularStreak(player.getCurrentRegularStreak());
                    }
                }
            } else {
                addScore = -(int) (player.getTrackState().getScore() * 0.2);
                if (player.getCurrentScore() + addScore < 0)
                    addScore = 0;
                if (player.getTrackState().equals(PlayerTrackState.REGULAR)) {
                    player.setCurrentRegularStreak(0);
                }
            }

            player.addScore(addScore);

            AnswerScoreDTO answerScoreDTO = new AnswerScoreDTO(addScore, player.getId());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, isCorrect ? "CORRECT_ANSWER" : "WRONG_ANSWER", answerScoreDTO,
                    player.getId(), player.getSessionActive());
            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, isCorrect ? "PLAYER_ANSWERED_CORRECTLY" : "PLAYER_ANSWERED_INCORRECTLY", answerScoreDTO,
                    race.getHost().getId(), race.getHost().getSessionActive());

            player.setCurrentQuestion(null);


            if (player.getCurrentScore() >= race.getSettings().getTargetScore()) {
                finishRace(race);
            } else {
                processNextStep(race, player);
            }
        }
    }

    private void handleQuestionTimeout(RaceManager race, RacePlayer player, MathQuestion timedOutQuestion) {
        if (!race.getStatus().isRunning()) return;

        synchronized (player) {
            if (player.getCurrentQuestion() != timedOutQuestion) {
                return;
            }

            if (!player.getTrackState().equals(PlayerTrackState.REGULAR)) {
                player.subSpecialQuestionsRemaining();
            }else {
                player.addRegularAttempt();
                player.addRegularTimeMs(player.getQuestionTimeSpent());
                player.setCurrentRegularStreak(0);
            }

            playerQuestionTimers.remove(player.getId());
            player.setCurrentQuestion(null);

            AnswerScoreDTO answerScoreDTO = new AnswerScoreDTO(0, player.getId());

            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_FEEDBACK, "TIMEOUT", answerScoreDTO,
                    player.getId(), player.getSessionActive());
            webSocketService.sendSuccessToQueueSession(QUEUE_RACE_HOST, "PLAYER_TIMEOUT", answerScoreDTO,
                    race.getHost().getId(), race.getHost().getSessionActive());

            processNextStep(race, player);
        }
    }

    private void resumePlayerState(RaceManager race, RacePlayer player) {
        if (!race.isAccountIn(player.getId()) || !race.getStatus().isRunning()) return;

        synchronized (player) {
            long remainingTime = player.getQuestionRemainingTimeMs();
            player.setQuestionStartTimeAtMs(System.currentTimeMillis());

            if (player.getTrackState() == PlayerTrackState.WAITING_FOR_CHOICE) {
                ScheduledFuture<?> timeoutTask = scheduler.schedule(
                        () -> handleJunctionTimeout(race, player),
                        Date.from(Instant.now().plusMillis(remainingTime))
                );
                playerQuestionTimers.put(player.getId(), timeoutTask);
                return;
            }

            if (player.getCurrentQuestion() == null) {
                processNextStep(race, player);
                return;
            }

            MathQuestion currentQuestion = player.getCurrentQuestion();
            Instant timeoutTime = Instant.now().plusMillis(remainingTime);

            ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> handleQuestionTimeout(race, player, currentQuestion), Date.from(timeoutTime));

            playerQuestionTimers.put(player.getId(), timeoutTask);
        }
    }

    public void pauseRace(RaceManager race) {
        if (race == null || !race.getStatus().isRunning()) return;

        long currentRemainingTime = race.getCalculatedRemainingTime();

        for (RacePlayer player : race.getPlayers().values()) {
            ScheduledFuture<?> timer = playerQuestionTimers.remove(player.getId());
            if (timer != null) {
                timer.cancel(false);

                long remainingTime = player.getCalculatedQuestionRemainingTime(race.getStatus());
                player.setQuestionRemainingTimeMs(remainingTime);
            }
        }

        race.setStatus(RaceStatus.PAUSED);
        race.setLastPausedAtMs(System.currentTimeMillis());
        race.setLastResumedAtMs(0);
        race.setRemainingTimeMs(currentRemainingTime);

        ScheduledFuture<?> endTask = raceEndTimers.remove(race.getId().toString());
        if (endTask != null) {
            endTask.cancel(false);
        }

        StatusChangedDTO statusChangedDTO = new StatusChangedDTO(race);

        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()), "RACE_PAUSED", statusChangedDTO);
   }

    public void resumeRace(RaceManager race) {
        if (race == null || !race.getStatus().equals(RaceStatus.PAUSED)) return;

        race.finalizeCurrentPause();

        race.setStatus(RaceStatus.IN_PROGRESS);
        race.setLastResumedAtMs(System.currentTimeMillis());

        Instant endTime = Instant.now().plusMillis(race.getRemainingTimeMs());
        ScheduledFuture<?> endTask = scheduler.schedule(() -> finishRace(race), Date.from(endTime));
        raceEndTimers.put(race.getId().toString(), endTask);

        StatusChangedDTO  statusChangedDTO = new StatusChangedDTO(race);
        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()), "RACE_RESUMED", statusChangedDTO);

        for (RacePlayer player : race.getPlayers().values()) {
            resumePlayerState(race, player);
        }
    }

    private void finishRace(RaceManager race) {
        if (!race.getStatus().isRunning()) return;

        race.setStatus(RaceStatus.FINISHED);
        race.setEndedAtMs(System.currentTimeMillis());

        ScheduledFuture<?> endTask = raceEndTimers.remove(race.getId().toString());
        if (endTask != null) {
            endTask.cancel(false);
        }

        RaceResultsDTO resultsDTO = new RaceResultsDTO(race);
        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),"RACE_COMPLETED",resultsDTO);

        endRace(race);
    }

    public void cancelledRace(RaceManager race) {
        if (race.getStatus().isClosed()) return;

        if (race.getStatus().equals(RaceStatus.PAUSED)){
           race.finalizeCurrentPause();
        }

        race.setStatus(RaceStatus.CANCELLED);
        race.setEndedAtMs(System.currentTimeMillis());

        ScheduledFuture<?> endTask = raceEndTimers.remove(race.getId().toString());
        if (endTask != null) {
            endTask.cancel(false);
        }

        StatusChangedDTO  statusChangedDTO = new StatusChangedDTO(race);
        webSocketService.sendSuccessToTopic(webSocketService.getRaceUpdatesTopic(race.getRoomCode()),"RACE_CANCELLED",statusChangedDTO);

        endRace(race);
    }

    private void endRace(RaceManager race){
        saveRace(race);
        clearAllPlayerTimersForRoom(race);
        kickFromWs(race);
        raceService.removeRace(race);
    }

    private void kickFromWs(RaceManager race){
        for (RacePlayer player : race.getPlayers().values()) {
            webSocketService.removeSession(player.getId(),player.getSessionActive(), ErrorCode.RACE_ALREADY_FINISHED);
        }
        webSocketService.removeSession(race.getHost().getId(),race.getHost().getSessionActive(),ErrorCode.RACE_ALREADY_FINISHED);
    }

    public void saveRace(RaceManager race) {
        raceRepository.saveRaceToHistory(race);
    }

    private void clearAllPlayerTimersForRoom(RaceManager race) {
        for (RacePlayer player : race.getPlayers().values()) {
            ScheduledFuture<?> timer = playerQuestionTimers.remove(player.getId());
            if (timer != null) {
                timer.cancel(false);
            }
        }
    }

    public void removeTimerForPlayer(RacePlayer player) {
        ScheduledFuture<?> timer = playerQuestionTimers.remove(player.getId());
        if (timer != null) {
            timer.cancel(false);
        }
    }

    public MathQuestion generateForPlayer(RaceManager race, RacePlayer player) {
        String level = player.getTrackState().getLevel();
        if (level.isEmpty()) return null;
        int i = 0;
        TemplateResult<MathQuestion> question;
        do {
            if (i > 80) throw new RuntimeException();

            question = questionEngine.processTemplate(questionTemplateService.getTemplateByDifficulty(level));
            if (!question.isSuccess()){
                questionErrorReportRepository.save(new QuestionErrorReportEntity(question, QuestionErrorReportEntity.ReporterType.SYSTEM,"-SYSTEM-","An error occurred while creating the template!"));
            }else{
                break;
            }
            i++;
        }while (true);

        race.getAllMathQuestions().put(question.data().getQuestionId(),question);

        return question.data();
    }
}
