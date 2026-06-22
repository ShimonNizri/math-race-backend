package com.example.math_race.GeminiApi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCreationRequest {

    private String templateId;
    private String prompt;
}
