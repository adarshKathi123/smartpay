package com.smartpay.dto;

import com.smartpay.entity.Role;
import com.smartpay.entity.UserStatus;

import java.time.LocalDateTime;

public class UserResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final Role role;
    private final UserStatus status;
    private final LocalDateTime createdAt;

    public UserResponse(Long id, String name, String email, Role role,
                        UserStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}