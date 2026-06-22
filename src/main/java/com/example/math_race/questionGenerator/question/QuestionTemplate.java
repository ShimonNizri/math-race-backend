package com.example.math_race.questionGenerator.question;

import com.example.math_race.entities.QuestionTemplateEntity;

import java.util.List;

public record QuestionTemplate(
        String id,
        String questionTemplate,
        String answerTemplate,
        String hintTemplate,
        List<String> distractorsTemplates
) {
    public QuestionTemplate(QuestionTemplateEntity entity) {
        this(
                entity.getTemplateId(),
                entity.getQuestionTemplate(),
                entity.getAnswerTemplate(),
                entity.getHintTemplate(),
                entity.getDistractorsTemplates()
        );
    }
}
