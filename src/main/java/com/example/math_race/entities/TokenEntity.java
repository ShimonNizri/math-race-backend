package com.example.math_race.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TokenEntity extends BaseEntity {

    public enum TokenType {
        SESSION,
        VERIFICATION,
        PASSWORD_RESET,
        DELETE_ACCOUNT,
        ADMIN
    }

    private String token;
    private TokenType type;
    private UserEntity user;
    private Date expiresAt;
    private boolean revoked;
    private String ipAddress;
    private String userAgent;

    public TokenEntity(String token, TokenType type, UserEntity user, Date expiresAt, String ipAddress, String userAgent) {
        super();
        this.token = token;
        this.user = user;
        this.type = type;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.revoked = false;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.before(new Date());
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
