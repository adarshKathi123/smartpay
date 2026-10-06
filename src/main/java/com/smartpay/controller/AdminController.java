package com.smartpay.controller;

import com.smartpay.dto.AdminUserStatusResponse;
import com.smartpay.entity.User;
import com.smartpay.service.AdminUserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @PostMapping("/{userId}/freeze")
    public AdminUserStatusResponse freeze(Authentication authentication, @PathVariable Long userId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        User user = adminUserService.setFrozen(actorUserId, userId, true);
        return new AdminUserStatusResponse(userId, String.valueOf(user.getStatus()));
    }

    @PostMapping("/{userId}/unfreeze")
    public AdminUserStatusResponse unfreeze(Authentication authentication, @PathVariable Long userId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        User user = adminUserService.setFrozen(actorUserId, userId, false);
        return new AdminUserStatusResponse(userId, String.valueOf(user.getStatus()));
    }
}