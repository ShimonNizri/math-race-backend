package com.example.math_race.service;

import com.example.math_race.dto.http.request.ChangePasswordRequest;
import com.example.math_race.dto.http.request.RequestMetadata;
import com.example.math_race.dto.http.request.UpdateUsernameRequest;
import com.example.math_race.dto.http.response.ProfileResponse;
import com.example.math_race.dto.http.response.RaceHistoryDetailsResponse;
import com.example.math_race.dto.http.response.RaceHistorySummaryResponse;
import com.example.math_race.dto.http.response.UserOverallStatisticsResponse;
import com.example.math_race.entities.TokenEntity;
import com.example.math_race.entities.UserEntity;
import com.example.math_race.exception.ErrorCode;
import com.example.math_race.exception.LogicException;
import com.example.math_race.repositories.TokenRepository;
import com.example.math_race.repositories.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.example.math_race.entities.TokenEntity.TokenType.SESSION;


@Service
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final TokenRepository tokenRepository;
    private final AuthService authService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final WebSocketService webSocketService;

    @Autowired
    public UserProfileService(UserProfileRepository userProfileRepository, AuthService authService,
                              TokenService tokenService, EmailService emailService, TokenRepository tokenRepository,
                              WebSocketService webSocketService) {
        this.userProfileRepository = userProfileRepository;
        this.authService = authService;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.tokenRepository = tokenRepository;
        this.webSocketService = webSocketService;
    }

    public ProfileResponse getProfile(RequestMetadata metadata) {
       UserEntity user = authService.getValidUser(metadata);
        return new ProfileResponse(user.getUsername(), user.getEmail());
    }


    public UserOverallStatisticsResponse getStatistics (RequestMetadata metadata) {
        UserEntity user = authService.getValidUser(metadata);
        return userProfileRepository.getUserOverallStatistics(user);
    }

    public List<RaceHistorySummaryResponse> getHistorySummaries(RequestMetadata metadata) {
        UserEntity user = authService.getValidUser(metadata);
        return userProfileRepository.getRaceHistorySummary(user);
    }

    public RaceHistoryDetailsResponse getSpecificRaceDetails(String raceId, RequestMetadata metadata) {
        UserEntity user = authService.getValidUser(metadata);
        return userProfileRepository.getRaceHistoryDetails(user, raceId);
    }

    @Transactional
    public void updateUsername(UpdateUsernameRequest request, RequestMetadata metadata){
        UserEntity user = authService.getValidUser(metadata);

        if (user.getUsername().equals(request.getUsername())) {
            throw new LogicException(ErrorCode.USERNAME_SAME_AS_OLD);
        }

        if (userProfileRepository.findByUsername(request.getUsername()) != null) {
            throw new LogicException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        if (request.getUsername().equalsIgnoreCase("bot") || request.getUsername().toLowerCase().startsWith("bot_")) {
            throw new LogicException(ErrorCode.RESERVED_USERNAME);
        }

        user.setUsername(request.getUsername());
        userProfileRepository.save(user);
    }

    @Transactional
    public void userChangePassword(ChangePasswordRequest request, RequestMetadata metadata) {
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

        if (authService.checkPassword(request.getNewPassword(), user.getPassword())) {
            throw new LogicException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        if (!authService.checkPassword(request.getOldPassword(), user.getPassword())) {
            throw new LogicException(ErrorCode.INCORRECT_PASSWORD);
        }

        user.setPassword(authService.hashPassword(request.getNewPassword()));
        tokenRepository.save(user);

        tokenService.updateTokenSessionExpiresDate(token);
        emailService.sendPasswordChangedEmail(user);
    }

    @Transactional
    public void requestAccountDeletion(RequestMetadata metadata){
        UserEntity user = authService.getValidUser(metadata);
        TokenEntity token = tokenService.createTokenEntity(user, TokenEntity.TokenType.DELETE_ACCOUNT,metadata.getIpAddress(),metadata.getUserAgent());

        emailService.sendDeleteAccountEmail(user,token);
    }

    @Transactional
    public void confirmAccountDeletion(String token, RequestMetadata metadata){
        UserEntity user = authService.getValidUser(metadata);
        TokenEntity tokenEntity = tokenRepository.findByToken(token);

        if (tokenEntity == null || !tokenEntity.getType().equals(TokenEntity.TokenType.DELETE_ACCOUNT) ||
                tokenEntity.isDeleted() || !tokenEntity.isValid()) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        if (!tokenEntity.getUser().getId().equals(user.getId())) {
            throw new LogicException(ErrorCode.INVALID_TOKEN);
        }

        tokenRepository.invalidateTokensByUser(user);

        String username = user.getUsername();
        String email = user.getEmail();

        user.setDeleted(true);
        user.setDeletionDate(new Date());

        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        user.setUsername("DeletedPlayer_" + randomSuffix);
        user.setEmail("deleted_" + randomSuffix + "@deleted.local");
        user.setPassword("DELETED");

        userProfileRepository.save(user);

        emailService.sendAccountDeletedSuccessfullyEmail(email,username);
        webSocketService.removeAllUserSessions(user.getId().toString(),ErrorCode.ACCOUNT_DELETED);
    }
}
