package com.example.math_race.GeminiApi;

public record TemplateContent(
        String templateId,
        String questionTemplate,
        String answerTemplate,
        String hintTemplate,
        String distractor1,
        String distractor2,
        String distractor3
) {}
