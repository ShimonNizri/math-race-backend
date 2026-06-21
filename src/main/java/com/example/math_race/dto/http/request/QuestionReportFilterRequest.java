package com.example.math_race.dto.http.request;

import com.example.math_race.entities.QuestionErrorReportEntity.ReportStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionReportFilterRequest {

    @Min(value = 0, message = "Page number must be 0 or greater")
    private int page = 0;

    @Min(value = 1, message = "Size must be at least 1")
    @Max(value = 100, message = "Size cannot exceed 100 items per page")
    private int size = 10;

    private ReportStatus status;
}
