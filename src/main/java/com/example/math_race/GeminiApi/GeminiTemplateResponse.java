package com.example.math_race.GeminiApi;

public record GeminiTemplateResponse(
        boolean success,
        String message,
        TemplateContent content
) {}
