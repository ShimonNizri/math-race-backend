package com.example.math_race.service;

import com.example.math_race.GeminiApi.GeminiTemplateResponse;
import com.example.math_race.GeminiApi.TemplateContent;
import com.example.math_race.GeminiApi.TemplateCreationRequest;
import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.entities.QuestionTemplateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String MODEL = "gemini-2.5-flash";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent?key=";

    private String baseSystemInstruction;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("templates/math-questions/template_prompt.md");
            this.baseSystemInstruction = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("Gemini system instructions loaded successfully from markdown file.");
        } catch (IOException e) {
            System.err.println("Critical: Failed to load Gemini system instruction file. Using fallback.");
            this.baseSystemInstruction = "You are an AI assistant helping with math question templates.";
        }
    }

    public GeminiTemplateResponse debugTemplate(QuestionErrorReportEntity errorReport, QuestionTemplateEntity template) {
        String userPrompt = """
                An error report has been received regarding a math question template. Analyze the failure and provide the fix.
                
                ### Original Template Details:
                - Template ID: %s
                - Question: %s
                - Answer: %s
                - Hint: %s
                - Distractors: 1) %s | 2) %s | 3) %s
                
                ### Error Report Details:
                - User Comment: %s
                - Actual Expression: %s
                - Errors: %s
                
                CRITICAL INSTRUCTIONS FOR JSON OUTPUT:
                1. MANDATORY STATIC ANALYSIS: Before looking at the error report, strictly validate the Original Template. Look for unclosed tags, undefined variables, math errors, or logic flaws. You MUST do this even if the Error Report Details are empty ("None").
                2. Set 'success' to true if you successfully analyzed the report and the template. Set to false ONLY if you completely failed to process the request.
                3. In 'message', briefly explain the bugs you found and fixed. If (and ONLY if) you performed a deep static analysis and found zero syntax/logic errors, explain why it's a false alarm.
                4. In the 'content' object, ONLY populate the template fields that you actually modified.
                5. For any field that you DID NOT modify, leave its value as an empty string "".
                6. Set 'templateId' to the exact Template ID provided in the original details above ("%s").
                """.formatted(
                template.getTemplateId(), template.getQuestionTemplate(), template.getAnswerTemplate(),
                template.getHintTemplate(), template.getDistractor1(), template.getDistractor2(), template.getDistractor3(),
                errorReport.getUserComment() != null ? errorReport.getUserComment() : "None",
                errorReport.getExpression() != null ? errorReport.getExpression() : "None",
                errorReport.getErrors() != null ? errorReport.getErrors() : "None",
                template.getTemplateId()
        );

        String fullInstruction = this.baseSystemInstruction +
                "\n\nTask: DEBUG MODE. You are a strict compiler and logic validator. ALWAYS perform a deep static analysis of the Original Template for syntax, variable scoping, and math errors, REGARDLESS of whether explicit system errors are provided in the report. Strictly follow the JSON formatting rules.";

        String jsonResult = sendToGemini(fullInstruction, userPrompt);
        return parseResponse(jsonResult);
    }

    public GeminiTemplateResponse createTemplate(TemplateCreationRequest creationRequest) {
        String userPrompt = """
                Please generate a brand new math question template based on the following structural and thematic requirements:
                
                ### Requirements:
                - Target Template ID: %s
                - Topic and Logic Instructions: %s
                
                CRITICAL INSTRUCTIONS FOR JSON OUTPUT:
                1. Set 'success' to true if you successfully generated a valid template matching the requirements.
                2. In 'message', briefly describe the mathematical structure and theme of the template you created.
                3. In the 'content' object, you MUST POPULATE EVERY SINGLE FIELD completely. No field should be left empty.
                4. Set the 'templateId' field to exactly the Target Template ID provided above ("%s").
                5. Ensure the questionTemplate, answerTemplate, hintTemplate, and all 3 distractors conform strictly to the syntax and formatting rules.
                """.formatted(
                creationRequest.getTemplateId(),
                creationRequest.getPrompt(),
                creationRequest.getTemplateId()
        );

        String fullInstruction = this.baseSystemInstruction +
                "\n\nTask: CREATION MODE. Generate a completely new valid template matching the prompt theme, and populate all fields in the JSON response schema.";

        String jsonResult = sendToGemini(fullInstruction, userPrompt);
        return parseResponse(jsonResult);
    }

    private GeminiTemplateResponse parseResponse(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, GeminiTemplateResponse.class);
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response to DTO: " + e.getMessage());
            return new GeminiTemplateResponse(
                    false,
                    "Failed to parse AI response: " + e.getMessage(),
                    new TemplateContent("", "", "", "", "", "", "")
            );
        }
    }

    private String sendToGemini(String systemInstruction, String userPrompt) {
        System.out.println("AIP request for Gemini sent");
        try {
            JSONObject jsonBody = new JSONObject();

            JSONObject textPart = new JSONObject().put("text", userPrompt);
            jsonBody.put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(textPart))));

            JSONObject systemTextPart = new JSONObject().put("text", systemInstruction);
            jsonBody.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(systemTextPart)));

            JSONObject contentProperties = new JSONObject()
                    .put("templateId", new JSONObject().put("type", "STRING"))
                    .put("questionTemplate", new JSONObject().put("type", "STRING"))
                    .put("answerTemplate", new JSONObject().put("type", "STRING"))
                    .put("hintTemplate", new JSONObject().put("type", "STRING"))
                    .put("distractor1", new JSONObject().put("type", "STRING"))
                    .put("distractor2", new JSONObject().put("type", "STRING"))
                    .put("distractor3", new JSONObject().put("type", "STRING"));

            JSONObject contentSchema = new JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", contentProperties)
                    .put("required", new JSONArray()
                            .put("templateId")
                            .put("questionTemplate")
                            .put("answerTemplate")
                            .put("hintTemplate")
                            .put("distractor1")
                            .put("distractor2")
                            .put("distractor3"));

            JSONObject schemaProperties = new JSONObject()
                    .put("success", new JSONObject().put("type", "BOOLEAN"))
                    .put("message", new JSONObject().put("type", "STRING"))
                    .put("content", contentSchema);

            JSONObject responseSchema = new JSONObject()
                    .put("type", "OBJECT")
                    .put("properties", schemaProperties)
                    .put("required", new JSONArray().put("success").put("message").put("content"));

            JSONObject generationConfig = new JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema);

            jsonBody.put("generationConfig", generationConfig);

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_URL + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Gemini API Error: " + response.body());
                return fallbackJsonString("API HTTP Error " + response.statusCode());
            }

            JSONObject jsonResponse = new JSONObject(response.body());
            return jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

        } catch (Exception e) {
            System.err.println("Exception in GeminiService communication: " + e.getMessage());
            return fallbackJsonString("Internal Exception: " + e.getMessage());
        }
    }

    private String fallbackJsonString(String errorMessage) {
        return """
               {
                 "success": false,
                 "message": "%s",
                 "content": {
                   "templateId": "", "questionTemplate": "", "answerTemplate": "", "hintTemplate": "",
                   "distractor1": "", "distractor2": "", "distractor3": ""
                 }
               }
               """.formatted(errorMessage.replace("\"", "'"));
    }
}
