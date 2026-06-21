package com.example.math_race.dto.http.response;

import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.questionGenerator.question.TemplateResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionTemplateTestResponse {

    private boolean hasErrors;
    private List<String> errors;

    private String questionId;
    private String expression;
    private List<String> options;
    private String hint;
    private String correctAnswer;

    public QuestionTemplateTestResponse(TemplateResult<MathQuestion> result) {
        if (result != null) {
            this.hasErrors = result.hasErrors();
            this.errors = result.errors();

            MathQuestion q = result.data();
            if (q != null) {
                this.questionId = q.getQuestionId();
                this.expression = q.getExpression();
                this.options = q.getOptions();
                this.hint = q.getHint();
                this.correctAnswer = q.getCorrectAnswer();
            }
        }
    }
}
