package com.example.math_race.dto.http.response;

import com.example.math_race.GeminiApi.GeminiTemplateResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TemplateDebugResponse {
    private boolean success;
    private String message;
    private String templateId;
    private String questionTemplate;
    private String answerTemplate;
    private String hintTemplate;
    private String distractor1;
    private String distractor2;
    private String distractor3;

    public TemplateDebugResponse(GeminiTemplateResponse geminiTemplateResponse){
        this.success = geminiTemplateResponse.success();
        this.message = geminiTemplateResponse.message();
        this.templateId = geminiTemplateResponse.content().templateId();
        this.questionTemplate = geminiTemplateResponse.content().questionTemplate();
        this.answerTemplate = geminiTemplateResponse.content().answerTemplate();
        this.hintTemplate = geminiTemplateResponse.content().hintTemplate();
        this.distractor1 = geminiTemplateResponse.content().distractor1();
        this.distractor2 = geminiTemplateResponse.content().distractor2();
        this.distractor3 = geminiTemplateResponse.content().distractor3();
    }
}
