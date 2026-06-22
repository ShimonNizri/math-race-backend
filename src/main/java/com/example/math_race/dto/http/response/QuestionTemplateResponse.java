package com.example.math_race.dto.http.response;

import com.example.math_race.entities.QuestionTemplateEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionTemplateResponse {
    private String templateId;

    private String questionTemplate;
    private String answerTemplate;
    private String hintTemplate;

    private String distractor1;
    private String distractor2;
    private String distractor3;

    public QuestionTemplateResponse(QuestionTemplateEntity entity){
        this.templateId = entity.getTemplateId();
        this.questionTemplate = entity.getQuestionTemplate();
        this.answerTemplate = entity.getAnswerTemplate();
        this.hintTemplate = entity.getHintTemplate();
        this.distractor1 = entity.getDistractor1();
        this.distractor2 = entity.getDistractor2();
        this.distractor3 = entity.getDistractor3();
    }
}
