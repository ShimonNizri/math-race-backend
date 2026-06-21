package com.example.math_race.dto.http.request;

import lombok.Data;

@Data
public class QuestionTemplateUpdateRequest {
    private String questionTemplate;
    private String answerTemplate;
    private String hintTemplate;

    private String distractor1;
    private String distractor2;
    private String distractor3;
}
