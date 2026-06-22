package com.example.math_race.service;

import com.example.math_race.dto.http.request.QuestionReportFilterRequest;
import com.example.math_race.dto.http.request.QuestionTemplateUpdateRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.dto.http.response.*;
import com.example.math_race.entities.QuestionErrorReportEntity;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.entities.UserEntity;
import com.example.math_race.entities.QuestionTemplateEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.questionGenerator.QuestionEngine;
import com.example.math_race.questionGenerator.question.MathQuestion;
import com.example.math_race.questionGenerator.question.QuestionTemplate;
import com.example.math_race.questionGenerator.question.TemplateResult;
import com.example.math_race.repositories.QuestionErrorReportRepository;
import com.example.math_race.repositories.QuestionTemplatesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.math_race.entities.TokenEntity.TokenType.ADMIN;

@Transactional(readOnly = true)
@Service
public class AdminService {

    private final AuthService authService;
    private final TokenService tokenService;
    private final QuestionEngine questionEngine;
    private final QuestionErrorReportRepository questionErrorReportRepository;
    private final QuestionTemplatesRepository questionRepository;
    private final QuestionTemplateService questionTemplateService;
    private final GeminiService geminiService;

    @Autowired
    public AdminService(AuthService authService, TokenService tokenService, QuestionErrorReportRepository questionErrorReportRepository,
                        QuestionTemplatesRepository questionRepository, QuestionTemplateService questionTemplateService, QuestionEngine questionEngine, GeminiService geminiService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.questionErrorReportRepository = questionErrorReportRepository;
        this.questionRepository = questionRepository;
        this.questionTemplateService = questionTemplateService;
        this.questionEngine = questionEngine;
        this.geminiService = geminiService;
    }

    @Transactional
    public AdminTokenResponse createTokenForAdmin(RequestMetadata metadata) {
        UserEntity user = authService.getValidUser(metadata);
        if (!user.isAdmin()) throw new LogicException(ErrorCode.ADMIN_ACCESS_REQUIRED);
        TokenEntity token = tokenService.createTokenEntity(user, TokenEntity.TokenType.ADMIN,
                metadata.getIpAddress(), metadata.getUserAgent());


        return new AdminTokenResponse(token.getToken(), 30);
    }

    public List<QuestionErrorReportResponse> getAllQuestionReports(QuestionReportFilterRequest filterRequest, RequestMetadata metadata) {
        authService.getValidUser(metadata, ADMIN);

        List<QuestionErrorReportEntity> entities = questionErrorReportRepository.findReportsByFilters(
                null,
                filterRequest.getStatus(),
                null,
                null,
                filterRequest.getPage(),
                filterRequest.getSize()
        );

        return entities.stream()
                .map(QuestionErrorReportResponse::new)
                .collect(Collectors.toList());
    }

    public QuestionTemplateResponse getTemplateById(String templateId, RequestMetadata metadata){
        authService.getValidUser(metadata, ADMIN);
        QuestionTemplateEntity questionTemplate = questionTemplateService.getTemplateById(templateId);
        if (questionTemplate == null) throw new LogicException(ErrorCode.TEMPLATE_NOT_FOUND);

        return new QuestionTemplateResponse(questionTemplate);
    }

    @Transactional
    public void updateReportStatus(String reportId, String status, RequestMetadata metadata) {
        authService.getValidUser(metadata, ADMIN);

        UUID reportUuid;
        try {
            reportUuid = UUID.fromString(reportId);
        } catch (IllegalArgumentException e) {
            throw new LogicException(ErrorCode.INVALID_INPUT);
        }

        QuestionErrorReportEntity report = questionErrorReportRepository.loadObject(QuestionErrorReportEntity.class, reportUuid);

        if (report == null || report.isDeleted()) {
            throw new LogicException(ErrorCode.REPORT_NOT_FOUND);
        }

        QuestionErrorReportEntity.ReportStatus newStatus;
        try {
            newStatus = QuestionErrorReportEntity.ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new LogicException(ErrorCode.INVALID_INPUT);
        }

        report.setStatus(newStatus);
        questionErrorReportRepository.save(report);
    }

    @Transactional
    public void updateTemplate(String templateId, QuestionTemplateUpdateRequest updateRequest, RequestMetadata metadata) {
        authService.getValidUser(metadata, ADMIN);

        QuestionTemplateEntity template = questionRepository.loadByTemplateId(templateId);

        if (template == null || template.isDeleted()) {
            throw new LogicException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        if (updateRequest.getQuestionTemplate() != null) {
            template.setQuestionTemplate(updateRequest.getQuestionTemplate());
        }

        if (updateRequest.getAnswerTemplate() != null) {
            template.setAnswerTemplate(updateRequest.getAnswerTemplate());
        }

        if (updateRequest.getHintTemplate() != null) {
            template.setHintTemplate(updateRequest.getHintTemplate());
        }

        if (updateRequest.getDistractor1() != null) {
            template.setDistractor1(updateRequest.getDistractor1());
        }

        if (updateRequest.getDistractor2() != null) {
            template.setDistractor2(updateRequest.getDistractor2());
        }

        if (updateRequest.getDistractor3() != null) {
            template.setDistractor3(updateRequest.getDistractor3());
        }

        questionRepository.save(template);
        questionTemplateService.reloadSingleTemplate(templateId);
    }

    public QuestionTemplateTestResponse testTemplateRun(QuestionTemplateUpdateRequest request, RequestMetadata metadata) {
        authService.getValidUser(metadata, ADMIN);

        List<String> distractors = java.util.Arrays.asList(
                request.getDistractor1() != null ? request.getDistractor1() : "",
                request.getDistractor2() != null ? request.getDistractor2() : "",
                request.getDistractor3() != null ? request.getDistractor3() : ""
        );

        QuestionTemplate tempTemplate = new QuestionTemplate(
                "test_id",
                request.getQuestionTemplate() != null ? request.getQuestionTemplate() : "",
                request.getAnswerTemplate() != null ? request.getAnswerTemplate() : "",
                request.getHintTemplate() != null ? request.getHintTemplate() : "",
                distractors
        );

        TemplateResult<MathQuestion> result = questionEngine.processTemplate(tempTemplate);
        return new QuestionTemplateTestResponse(result);
    }
    
    public TemplateDebugResponse analyzeReportWithAI(String reportId, RequestMetadata metadata){
        authService.getValidUser(metadata, ADMIN);

        UUID reportUuid;
        try {
            reportUuid = UUID.fromString(reportId);
        } catch (IllegalArgumentException e) {
            throw new LogicException(ErrorCode.INVALID_INPUT);
        }

        QuestionErrorReportEntity report = questionErrorReportRepository.loadObject(QuestionErrorReportEntity.class, reportUuid);
        if (report == null || report.isDeleted()) {
            throw new LogicException(ErrorCode.REPORT_NOT_FOUND);
        }

        if (report.getTemplateId() == null || report.getTemplateId().isEmpty()) {
            throw new LogicException(ErrorCode.INVALID_INPUT);
        }

        QuestionTemplateEntity template = questionRepository.loadByTemplateId(report.getTemplateId());
        if (template == null || template.isDeleted()) {
            throw new LogicException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        
        return new TemplateDebugResponse(geminiService.debugTemplate(report, template));
    }
}
