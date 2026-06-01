package com.example.math_race.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // General Errors
    INVALID_INPUT(1000, "Invalid input!"),
    AUTH_FAILED(1001, "Invalid email or password"),
    EMAIL_ALREADY_EXISTS(1002, "Email already exists!"),
    USERNAME_ALREADY_EXISTS(1003, "Username already exists!"),
    REGISTRATION_FAILED(1004, "Registration failed. Please check your details and try again"),
    ACCOUNT_NOT_VERIFIED(1005, "Account not verified. Please check your email for the verification link."),
    EMAIL_NOT_EXISTS(1006, "Email not exists!"),
    ACCOUNT_NOT_FOUND(1007, "Account not found!"),
    PASSWORD_SAME_AS_OLD(1008, "New password cannot be the same as the current password!"),
    USERNAME_SAME_AS_OLD(1009, "New username cannot be the same as the current username!"),
    ACCOUNT_DELETED(1010,"Account has been deleted!"),
    INCORRECT_PASSWORD(1011, "Incorrect password!"),
    RESERVED_USERNAME(1012, "This username is reserved and cannot be used!"),

    // Token & Security Errors (1100 Series)
    INVALID_TOKEN(1100, "The token is invalid or has expired."),

    // Email Specific Errors (1200 Series)
    EMAIL_SEND_FAILED(1200, "Failed to send email. Please try again later."),
    EMAIL_NOT_FOUND(1201, "The recipient email address does not exist."),
    EMAIL_INVALID_FORMAT(1202, "Invalid email format!"),

    // Rate Limiting & Flow Errors (1300 Series)
    TOO_MANY_REQUESTS(1300, "Too many requests. Please wait a moment and try again."),
    EMAIL_COOLDOWN_ACTIVE(1301, "Please wait 2 minutes between email requests."),

    // Race & Game Errors (1400 Series)
    INVALID_RACE_SCORE(1400, "Winning points must be between 400 and 1500!"),
    RACE_NAME_TOO_SHORT(1401, "Race name must be at least 3 characters long!"),
    RACE_MAX_PLAYERS_EXCEEDED(1402, "Number of players exceeds the allowed limit."),
    USER_ALREADY_IN_RACE(1403, "You are already a host or a participant in another active race!"),
    RACE_NOT_FOUND(1404, "The requested race room does not exist!"),
    RACE_ALREADY_STARTED(1405, "This race has already started and cannot be joined!"),
    RACE_ALREADY_FINISHED(1406, "This race has already ended and is no longer available!"),
    HOST_FORBIDDEN_PLAYER_ACTION(1407, "As the host, you cannot participate as a player or subscribe to player-only paths!"),
    NOT_REGISTERED_FOR_RACE(1408, "You are not registered for this race. Please join the race before performing any actions."),
    DUPLICATE_RACE_CONNECTION(1409, "Action denied. You are already connected to this race from another session or tab."),
    SESSION_TRANSFERRED_TO_NEW_DEVICE(1410, "Your connection has been transferred to a new device or tab. This session is no longer active."),
    NOT_RACE_HOST(1411, "Only the host of the race is allowed to perform this action!"),
    INVALID_RACE_PATH(1412, "The requested subscription path is invalid or does not exist!"),
    USER_NOT_IN_ANY_RACE(1413, "You are not currently participating in any race! Please join a room first."),
    USER_NOT_IDENTIFIED(1414, "Identity verification failed. Please log in or join as a guest to continue."),
    MISSING_IDENTIFICATION(1415, "No identification provided! You must provide either an authentication token or a guest ID to proceed."),
    NOT_RACE_PLAYER(1416, "Only player on the race is allowed to perform this action!"),
    RACE_ALREADY_INITIALIZED(1417, "The race has already been initialized!"),
    NOT_ENOUGH_PLAYERS_TO_START(1418, "At least 2 players are required to start the race!"),
    RACE_HISTORY_ACCESS_DENIED(1419, "You are not authorized to view the history of this race!"),
    RACE_HISTORY_NOT_FOUND(1420, "The requested race history does not exist or has been deleted."),
    PLAYER_KICKED(1420,"The race host has removed you from the race."),
    PLAYER_LEFT(1421,"The player left the race."),
    RACE_NAME_TOO_BIG(1422, "The race name is too long!"),

    // Connection & Session Errors (1500 Series)
    INTENTIONAL_DISCONNECT(1500, "The connection was closed intentionally."),

    // System Errors
    INTERNAL_ERROR(9000, "An unexpected error occurred"),
    NOT_FOUND(9001, "This page is not found.");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
