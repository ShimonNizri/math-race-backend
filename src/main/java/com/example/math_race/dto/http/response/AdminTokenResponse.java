package com.example.math_race.dto.http.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminTokenResponse {
    private String token;
    private int minutesToSaveToken;
}
