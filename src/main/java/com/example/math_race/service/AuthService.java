package com.example.math_race.service;

import com.example.math_race.dto.http.request.*;
import com.example.math_race.dto.http.response.CreateGuestTokenResponse;
import com.example.math_race.dto.http.response.LoginResponse;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.entities.UserEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.repositories.AuthRepository;
import com.example.math_race.repositories.TokenRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.example.math_race.entities.TokenEntity.TokenType.*;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AuthRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final TokenRepository tokenRepository;

    private final Cache<String, String> guestSessions = Caffeine.newBuilder()
            .expireAfterAccess(24, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    @Autowired
    public AuthService(EmailService emailService, AuthRepository userRepository, TokenService tokenService, TokenRepository tokenRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
    }


    @Transactional
    public LoginResponse loginUser(LoginRequest request, RequestMetadata metadata) {
        UserEntity user = userRepository.findByEmail(request.getEmail());

        if (user == null || !checkPassword(request.getPassword(), user.getPassword())) {
            throw new LogicException(ErrorCode.AUTH_FAILED);
        }

        if (!user.isVerified()){
            tokenService.resendVerificationEmail(user, metadata);
            throw new LogicException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        TokenEntity tokenEntity = tokenService.createTokenEntity(user, SESSION,
                metadata.getIpAddress(),  metadata.getUserAgent());

        return new LoginResponse(
                tokenEntity.getToken(),
                365,
                user.getUsername(),
                user.getEmail()
        );
    }

    @Transactional
    public void registerUser(RegisterRequest request, RequestMetadata metadata) {
        UserEntity user = userRepository.findByEmailOrUsername(request.getEmail(),request.getUsername());

        if (user != null) {
            if (user.isDeleted()) {
                throw new LogicException(ErrorCode.REGISTRATION_FAILED);
            }

            if (user.getEmail().equals(request.getEmail())) {
                throw new LogicException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

            throw new LogicException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        if (request.getUsername().equalsIgnoreCase("bot") || request.getUsername().toLowerCase().startsWith("bot_")) {
            throw new LogicException(ErrorCode.RESERVED_USERNAME);
        }

        user = new UserEntity(request.getUsername(), hashPassword(request.getPassword()), request.getEmail());
        userRepository.save(user);

        TokenEntity token = tokenService.createTokenEntity(user, VERIFICATION,
                metadata.getIpAddress(), metadata.getUserAgent());

        emailService.sendVerificationEmail(user,token);
    }

    @Transactional
    public void verifyAccount(VerifyAccountRequest request) {
        TokenEntity token = tokenRepository.findByToken(request.getToken());

       if (token == null || !token.isValid() || token.isDeleted() || token.getType() != VERIFICATION) {
           throw new LogicException(ErrorCode.INVALID_TOKEN);
       }

       UserEntity user = token.getUser();
       if (user.isDeleted()) {
           throw new LogicException(ErrorCode.ACCOUNT_NOT_FOUND);
       }

       token.setRevoked(true);
       tokenRepository.save(token);

       user.setVerified(true);
       userRepository.save(user);
    }

    @Transactional
    public void userForgotPassword(ForgotPasswordRequest request, RequestMetadata metadata) {
        UserEntity user = userRepository.findByEmail(request.getEmail());

        if (user == null || user.isDeleted()) {
            throw new LogicException(ErrorCode.EMAIL_NOT_EXISTS);
        }

        if (!user.isVerified()){
            throw new LogicException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        TokenEntity token = tokenService.createTokenEntity(user, PASSWORD_RESET,
                metadata.getIpAddress(),  metadata.getUserAgent());
        emailService.sendPasswordResetEmail(user,token);
    }

    @Transactional
    public void userResetPassword(ResetPasswordRequest request) {
        TokenEntity token = tokenRepository.findByToken(request.getToken());

        if (token == null || !token.isValid() || token.isDeleted() || token.getType() != PASSWORD_RESET) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        UserEntity user = token.getUser();
        if (user.isDeleted()) {
            throw new LogicException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        token.setRevoked(true);
        tokenRepository.save(token);

        user.setPassword(hashPassword(request.getNewPassword()));
        userRepository.save(user);

        tokenRepository.invalidateTokensByUserAndType(user, SESSION);
    }

    public UserEntity getActiveUserByToken(RequestMetadata metadata) {
       return getActiveUserByToken(metadata.getAuthorization());
    }

    public UserEntity getActiveUserByToken(String userToken) {
        if (!StringUtils.hasText(userToken)) {
            return null;
        }

        TokenEntity token = tokenRepository.findByToken(userToken);
        if (token != null && token.isValid() && !token.isDeleted() && token.getType() == SESSION &&
                !token.getUser().isDeleted() && token.getUser().isVerified()) {
            return token.getUser();
        }

        return null;
    }

    public UserEntity getValidUser(RequestMetadata metadata) {
        TokenEntity token = null;
        if (StringUtils.hasText(metadata.getAuthorization())) {
            token = tokenRepository.findByToken(metadata.getAuthorization());
        }

        if (token == null || !token.isValid() || token.isDeleted() || token.getType() != SESSION) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        UserEntity user = token.getUser();

        if (user.isDeleted()) {
            throw new LogicException(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        if (!user.isVerified()){
            throw new LogicException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }

        return user;
    }

    public CreateGuestTokenResponse createGuestToken() {
        String guestId = "Guest-" +UUID.randomUUID().toString().substring(6);
        String sessionToken = UUID.randomUUID().toString();

        guestSessions.put(sessionToken, guestId);
        return new CreateGuestTokenResponse(sessionToken,30);
    }

    @Transactional
    public TokenEntity generateTokenForBot(int botIndex) {
        String botUsername = "Bot_" + botIndex;
        UserEntity botUser = userRepository.findByEmailOrUsername("",botUsername);
        if (botUser == null) {
            throw new RuntimeException("Bot user not found in DB");
        }

        return tokenService.createTokenEntity(botUser, SESSION);
    }

    public String getGuestIdByToken(String token) {
        if (token == null) return null;
        return guestSessions.getIfPresent(token);
    }

    public String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
    }

    public boolean checkPassword(String rawPassword, String hashedDbPassword) {
        if (hashedDbPassword == null || !hashedDbPassword.startsWith("$2a$")) {
            return false;
        }
        return BCrypt.checkpw(rawPassword, hashedDbPassword);
    }
}
