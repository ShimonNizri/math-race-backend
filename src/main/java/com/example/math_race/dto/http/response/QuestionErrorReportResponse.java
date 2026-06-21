package com.example.math_race.dto.http.response;

import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.entities.QuestionErrorReportEntity.ReportStatus;
import com.example.math_race.entities.QuestionErrorReportEntity.ReporterType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionErrorReportResponse {

    private String id;
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


    public QuestionErrorReportResponse(QuestionErrorReportEntity entity){
        this.id = entity.getId().toString();
        this.templateId = entity.getTemplateId();
        this.status = entity.getStatus();
        this.reporterType = entity.getReporterType();
        this.reporterId = entity.getReporterId();
        this.userComment = entity.getUserComment();
        this.expression = entity.getExpression();
        this.options = entity.getOptions();
        this.hint = entity.getHint();
        this.errors = entity.getErrors();
        this.tagMemoryJson = entity.getTagMemoryJson();
    }
}
