package com.example.math_race.json.models.seeders;

import com.example.math_race.entities.QuestionTemplateEntity;

import com.example.math_race.json.models.questions.QuestionTemplateJsonModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TemplateJsonSeeder {

    private static final String EASY_TEMPLATES_PATH = "/templates/math-questions/easy.json";
    private static final String MEDIUM_TEMPLATES_PATH = "/templates/math-questions/medium.json";
    private static final String HARD_TEMPLATES_PATH = "/templates/math-questions/hard.json";

    private static final String[] ALL_JSON_PATHS = {
            EASY_TEMPLATES_PATH,
            MEDIUM_TEMPLATES_PATH,
            HARD_TEMPLATES_PATH
    };

    private final ObjectMapper mapper = new ObjectMapper();

    public List<QuestionTemplateEntity> getAllTemplateEntitiesFromJson() {
        List<QuestionTemplateEntity> allTemplates = new ArrayList<>();

        for (String path : ALL_JSON_PATHS) {
            allTemplates.addAll(loadEntitiesFromJson(path));
        }

        return allTemplates;
    }

    private List<QuestionTemplateEntity> loadEntitiesFromJson(String jsonPath) {
        try {
            InputStream inputStream = getClass().getResourceAsStream(jsonPath);
            if (inputStream == null) {
                System.out.println("\u001B[31m" + "Warning: Cannot find " + jsonPath + " in resources. Skipping." + "\u001B[0m");
                return new ArrayList<>();
            }

            List<QuestionTemplateJsonModel> jsonModels = mapper.readValue(
                    inputStream, new TypeReference<List<QuestionTemplateJsonModel>>() {});

            return jsonModels.stream()
                    .map(QuestionTemplateEntity::new)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to load templates from JSON at path: " + jsonPath, e);
        }
    }
}
