package com.example.math_race.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class UserEntity extends BaseEntity {

    public enum UserRole {
        ADMIN,
        USER,
        BOT
    }

    private String username;
    private String password;
    private String email;
    private UserRole role;
    private boolean verified;

    public UserEntity(String username, String password, String email) {
        super();
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = UserRole.USER;
        this.verified = false;
    }

    public boolean isAdmin() {
        return this.role == UserRole.ADMIN;
    }

    public boolean isUser() {
        return this.role == UserRole.USER;
    }
}
