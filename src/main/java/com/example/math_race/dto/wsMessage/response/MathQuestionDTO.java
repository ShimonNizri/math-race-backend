package com.example.math_race.dto.wsMessage.response;

import com.example.math_race.race.RaceManager;
import com.example.math_race.race.RacePlayer;
import com.example.math_race.questionGenerator.question.MathQuestion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MathQuestionDTO {

    private String questionId;
    private String playerId;
    private String expression;
    private List<String> options;
    private long timeLimitMillis;
    private long questionRemainingTimeMillis;
    private int score;
    private String hint;
    private boolean canAskHint;
    private long sentAt;

    public MathQuestionDTO(RaceManager race, RacePlayer player, MathQuestion mathQuestion) {
        this.questionId = mathQuestion.getQuestionId();
        this.playerId = player.getId();
        this.expression = mathQuestion.getExpression();
        this.options = mathQuestion.getOptions();
        this.timeLimitMillis = player.getTrackState().getTimeLimitMillis();
        this.score = player.getTrackState().getScore();
        this.questionRemainingTimeMillis = player.getCalculatedQuestionRemainingTime(race.getStatus());
        this.sentAt = System.currentTimeMillis();
        this.hint = player.isGotHint() ? mathQuestion.getHint() : null;
        this.canAskHint = player.isCanAskHint();
    }
}
