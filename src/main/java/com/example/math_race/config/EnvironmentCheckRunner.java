package com.example.math_race.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnvironmentCheckRunner implements CommandLineRunner {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";

    private final Environment env;

    @Autowired
    public EnvironmentCheckRunner(Environment env) {
        this.env = env;
    }

    @Override
    public void run(String... args) throws Exception {
        List<String> requiredVariables = List.of(
                "DB_HOST",
                "DB_PASSWORD",
                "DB_PORT",
                "DB_ROOT",
                "DB_SCHEMA",
                "MAIL_PASSWORD",
                "MAIL_USERNAME",
                "GEMINI_API_KEY"
        );

        System.out.println(ANSI_CYAN + "=== [System] Environment Variables Initialization Check ===" + ANSI_RESET);

        boolean hasWarnings = false;

        for (String var : requiredVariables) {
            String value = env.getProperty(var);

            if (value == null || value.trim().isEmpty()) {
                System.out.println(ANSI_RED + "WARNING: Required environment variable '" + var + "' is not set or empty." + ANSI_RESET);
                hasWarnings = true;
            } else {
                System.out.println(ANSI_GREEN + "INFO: Environment variable '" + var + "' is securely loaded." + ANSI_RESET);
            }
        }

        if (hasWarnings) {
            System.out.println(ANSI_YELLOW + "SYSTEM NOTICE: Application started with missing variables. Some features may not function correctly." + ANSI_RESET);
        } else {
            System.out.println(ANSI_CYAN + "=== [System] All Environment Variables Loaded Successfully ===" + ANSI_RESET);
        }
    }
}
