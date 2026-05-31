package com.example.math_race.dto.http.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class CreateRaceRequest {

    @Size(max = 15, message = "name cannot exceed 15 characters")
    @Pattern(regexp = "^(?:.*\\S){3}.*$", message = "name must contain at least 3 actual characters")
    @Pattern(regexp = "^\\S.*\\S$", message = "name must not start or end with a space")
    private String name;

    @NotNull(message = "Target score is required")
    @Min(value = 400, message = "Target score must be at least 400")
    @Max(value = 2500, message = "Target score cannot exceed 2500")
    private Integer targetScore;

    @Size(max = 15, message = "nickname cannot exceed 15 characters")
    @Pattern(regexp = "^(?:.*\\S){3}.*$", message = "nickname must contain at least 3 actual characters")
    @Pattern(regexp = "^\\S.*\\S$", message = "nickname must not start or end with a space")
    private String nickname;

    @JsonProperty("isPrivate")
    private boolean isPrivate = true;
}
