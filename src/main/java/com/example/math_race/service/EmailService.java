package com.example.math_race.service;

import com.example.math_race.email.EmailSender;
import com.example.math_race.email.MailRequest;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.entities.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


import static com.example.math_race.config.AsyncConfig.EMAIL_TASK_EXECUTOR_BEAN_NAME;


@Service
public class EmailService {

    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;
    private final String BASE_PATH = "http://10.36.92.56:5174";


    @Autowired
    public EmailService(EmailSender emailSender, TemplateEngine templateEngine) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }


    @Async(EMAIL_TASK_EXECUTOR_BEAN_NAME)
    public void sendVerificationEmail(UserEntity to, TokenEntity token) {
        Context context = new Context();

        context.setVariable("name", to.getUsername());
        context.setVariable("verificationUrl", BASE_PATH+"/auth/verify/" + token.getToken());

        String htmlContent = templateEngine.process("verify-email", context);

        MailRequest mailRequest = new MailRequest(
                to.getEmail(),
                "אימות רישום - Math Race",
                htmlContent,
                true
        );

        emailSender.sendEmail(mailRequest);
    }

    @Async(EMAIL_TASK_EXECUTOR_BEAN_NAME)
    public void sendPasswordResetEmail(UserEntity to, TokenEntity token) {
        Context context = new Context();

        context.setVariable("name", to.getUsername());
        context.setVariable("resetUrl", BASE_PATH+"/auth/reset-password/" + token.getToken());

        String htmlContent = templateEngine.process("reset-password", context);

        MailRequest mailRequest = new MailRequest(
                to.getEmail(),
                "איפוס סיסמה - Math Race",
                htmlContent,
                true
        );

        emailSender.sendEmail(mailRequest);
    }

    @Async(EMAIL_TASK_EXECUTOR_BEAN_NAME)
    public void sendPasswordChangedEmail(UserEntity to) {
        Context context = new Context();

        context.setVariable("name", to.getUsername());
        context.setVariable("loginUrl", BASE_PATH+"/auth/login");

        String htmlContent = templateEngine.process("password-changed", context);

        MailRequest mailRequest = new MailRequest(
                to.getEmail(),
                "הסיסמה שלך שונתה - Math Race",
                htmlContent,
                true
        );

        emailSender.sendEmail(mailRequest);
    }

    @Async(EMAIL_TASK_EXECUTOR_BEAN_NAME)
    public void sendDeleteAccountEmail(UserEntity to, TokenEntity token) {
        Context context = new Context();

        context.setVariable("name", to.getUsername());
        context.setVariable("deleteUrl", BASE_PATH + "/manage-profile/delete-account/" + token.getToken());

        String htmlContent = templateEngine.process("delete-account", context);

        MailRequest mailRequest = new MailRequest(
                to.getEmail(),
                "אישור מחיקת חשבון - Math Race",
                htmlContent,
                true
        );

        emailSender.sendEmail(mailRequest);
    }

    @Async(EMAIL_TASK_EXECUTOR_BEAN_NAME)
    public void sendAccountDeletedSuccessfullyEmail(String targetEmail, String originalUsername) {
        Context context = new Context();

        context.setVariable("name", originalUsername);

        String htmlContent = templateEngine.process("account-deleted", context);

        MailRequest mailRequest = new MailRequest(
                targetEmail,
                "החשבון שלך נמחק בהצלחה - Math Race",
                htmlContent,
                true
        );

        emailSender.sendEmail(mailRequest);
    }
}
