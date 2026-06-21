package com.example.math_race.dto.wsMessage.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class ReportTemplateRequest {

    @NotBlank(message = "Question ID is required for reporting")
    @Size(max = 255, message = "Question ID length is invalid")
    private String questionId;

    @Size(max = 1000, message = "Comment is too long (maximum 1000 characters)")
    private String userComment;
}
