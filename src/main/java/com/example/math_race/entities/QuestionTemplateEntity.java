package com.example.math_race.entities;

import com.example.math_race.json.models.questions.QuestionTemplateJsonModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTemplateEntity extends BaseEntity {

    private String templateId;

    private String questionTemplate;
    private String answerTemplate;
    private String hintTemplate;

    private String distractor1;
    private String distractor2;
    private String distractor3;

    public QuestionTemplateEntity(QuestionTemplateJsonModel model) {
        this.templateId = model.id();
        this.questionTemplate = model.questionTemplate();
        this.answerTemplate = model.answerTemplate();
        this.hintTemplate = model.hintTemplate();
        setDistractorsFromList(model.distractorsTemplates());
    }

    public List<String> getDistractorsTemplates() {
        return Arrays.asList(distractor1, distractor2, distractor3);
    }

    public void setDistractorsFromList(List<String> distractors) {
        if (distractors != null && distractors.size() >= 3) {
            this.distractor1 = distractors.get(0);
            this.distractor2 = distractors.get(1);
            this.distractor3 = distractors.get(2);
        }
    }
}
