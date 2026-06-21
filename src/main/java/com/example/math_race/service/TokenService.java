package com.example.math_race.service;

import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.entities.UserEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.repositories.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static com.example.math_race.entities.TokenEntity.TokenType.*;

@Service
@Transactional(readOnly = true)
public class TokenService {

    private final TokenRepository tokenRepository;
    private final EmailService emailService;

    @Autowired
    public TokenService(TokenRepository tokenRepository, EmailService emailService) {
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public TokenEntity createTokenEntity(UserEntity user, TokenEntity.TokenType tokenType, String ipAddress, String userAgent) {
        String randomToken = createToken();
        Date expiresAt = null;
        Instant now = Instant.now();


        if (tokenType == SESSION) {
            expiresAt = Date.from(Instant.now().plus(30, ChronoUnit.DAYS));

        }else if (tokenType == VERIFICATION || tokenType == PASSWORD_RESET || tokenType == DELETE_ACCOUNT || tokenType == ADMIN) {
            TokenEntity oldToken = tokenRepository.findLatestActiveToken(user,tokenType);

            if (tokenType != ADMIN && oldToken != null && Duration.between(oldToken.getCreationDate().toInstant(), now).toMinutes() <= 2) {
                throw new LogicException(ErrorCode.EMAIL_COOLDOWN_ACTIVE);
            }

            tokenRepository.invalidateTokensByUserAndType(user, tokenType);
            if (tokenType == VERIFICATION) {
                expiresAt = Date.from(Instant.now().plus(24, ChronoUnit.HOURS));
            }else if (tokenType == PASSWORD_RESET) {
                expiresAt = Date.from(Instant.now().plus(12, ChronoUnit.HOURS));
            }else if (tokenType == DELETE_ACCOUNT) {
                expiresAt = Date.from(Instant.now().plus(6, ChronoUnit.HOURS));
            }else {
                expiresAt = Date.from(Instant.now().plus(30, ChronoUnit.MINUTES));
            }
        }

        TokenEntity token = new TokenEntity(
                randomToken,
                tokenType,
                user,
                expiresAt,
                ipAddress,
                userAgent
        );

        tokenRepository.save(token);

        return token;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resendVerificationEmail(UserEntity user, RequestMetadata metadata) {
        TokenEntity newToken = createTokenEntity(user, VERIFICATION,
                metadata.getIpAddress(),  metadata.getUserAgent());
        emailService.sendVerificationEmail(user,newToken);
    }

    @Transactional
    public TokenEntity createTokenEntity(UserEntity user, TokenEntity.TokenType tokenType) {
        return createTokenEntity(user, tokenType, null, null);
    }

    @Transactional
    public void updateTokenSessionExpiresDate(TokenEntity tokenEntity) {
        if (tokenEntity.getType() == SESSION && tokenEntity.getExpiresAt() != null) {

            Instant now = Instant.now();
            Instant expiresAt = tokenEntity.getExpiresAt().toInstant();
            long daysRemaining = Duration.between(now, expiresAt).toDays();

            if (daysRemaining <= 29) {

                tokenEntity.setExpiresAt(Date.from(now.plus(30, ChronoUnit.DAYS)));
                tokenRepository.save(tokenEntity);
            }
        }
    }

    private String createToken() {
        return UUID.randomUUID().toString();
    }
}
