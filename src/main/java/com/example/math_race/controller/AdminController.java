package com.example.math_race.controller;

import com.example.math_race.dto.http.ApiResponse;
import com.example.math_race.dto.http.request.QuestionReportFilterRequest;
import com.example.math_race.dto.http.request.QuestionTemplateUpdateRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.dto.http.response.AdminTokenResponse;
import com.example.math_race.dto.http.response.QuestionErrorReportResponse;
import com.example.math_race.dto.http.response.QuestionTemplateResponse;
import com.example.math_race.dto.http.response.QuestionTemplateTestResponse;
import com.example.math_race.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    @Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/token")
    public ApiResponse<AdminTokenResponse> generateAdminToken(RequestMetadata metadata) {
        AdminTokenResponse adminTokenResponse = adminService.createTokenForAdmin(metadata);
        return ApiResponse.success(adminTokenResponse);
    }

    @GetMapping("/question-reports")
    public ApiResponse<List<QuestionErrorReportResponse>> getAllQuestionReports(@Valid QuestionReportFilterRequest filterRequest, RequestMetadata metadata) {
        List<QuestionErrorReportResponse> reports = adminService.getAllQuestionReports(filterRequest, metadata);
        return ApiResponse.success(reports);
    }

    @GetMapping("/templates/{templateId}")
    public ApiResponse<QuestionTemplateResponse> getTemplateById(@PathVariable String templateId, RequestMetadata metadata) {
        QuestionTemplateResponse templateContent = adminService.getTemplateById(templateId, metadata);
        return ApiResponse.success(templateContent);
    }

    // 2. דיבוג בעזרת AI לפי מזהה הדיווח
    @PostMapping("/question-reports/{reportId}/debug-ai")
    public ApiResponse<String> debugTemplateWithAI(
            @PathVariable String reportId,
            RequestMetadata metadata) {
        // מחזיר את התשובה של ה-AI כמחרוזת
        String aiResponse = "לא עובד כעת"; //adminService.analyzeReportWithAI(reportId, metadata);
        return ApiResponse.success(aiResponse);
    }

    @PostMapping("/templates/test")
    public ApiResponse<QuestionTemplateTestResponse> testTemplate(@RequestBody QuestionTemplateUpdateRequest request, RequestMetadata metadata) {

        QuestionTemplateTestResponse testOutput = adminService.testTemplateRun(request, metadata);
        return ApiResponse.success(testOutput);
    }
    @PatchMapping("/question-reports/{reportId}/status")
    public ApiResponse<Void> updateReportStatus(@PathVariable String reportId, @RequestParam String status, RequestMetadata metadata) {

        adminService.updateReportStatus(reportId, status, metadata);
        return ApiResponse.success(null);
    }

    @PutMapping("/templates/{templateId}")
    public ApiResponse<Void> updateTemplate(@PathVariable String templateId, @RequestBody QuestionTemplateUpdateRequest request, RequestMetadata metadata) {

        adminService.updateTemplate(templateId, request, metadata);
        return ApiResponse.success(null);
    }
}
