package com.example.math_race.entities;

import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.questionGenerator.question.TemplateResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionErrorReportEntity extends BaseEntity {

    public enum ReportStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        REJECTED
    }

    public enum ReporterType {
        REGISTERED_USER,
        GUEST,
        SYSTEM
    }

    private String templateId;
    private ReportStatus status;

    private ReporterType reporterType;
    private String reporterId;
    private String userComment;

    private String expression;
    private String options;
    private String hint;
    private String errors;

    private String tagMemoryJson;

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public QuestionErrorReportEntity(TemplateResult<MathQuestion> questionTemplateResult, ReporterType type, String reporterId, String userComment) {
        this.status = ReportStatus.OPEN;
        this.reporterType = type;
        this.reporterId = reporterId;
        this.userComment = userComment;

        if (questionTemplateResult != null) {

            if (questionTemplateResult.data() != null) {
                this.templateId = questionTemplateResult.data().getTemplateId();
                this.expression = questionTemplateResult.data().getExpression();
                this.hint = questionTemplateResult.data().getHint();
                this.options = questionTemplateResult.data().getOptions() != null ?
                        String.join(", ", questionTemplateResult.data().getOptions()) : null;
            } else {
                this.expression = "FAILED_TO_GENERATE";
            }

            this.errors = (questionTemplateResult.errors() != null && !questionTemplateResult.errors().isEmpty()) ?
                    String.join("\n", questionTemplateResult.errors()) : null;

            try {
                if (questionTemplateResult.memory() != null) {
                    this.tagMemoryJson = jsonMapper.writeValueAsString(questionTemplateResult.memory());
                }
            } catch (JsonProcessingException e) {
                this.tagMemoryJson = "{\"error\": \"Failed to parse memory to JSON\"}";
            }
        }
    }
}
