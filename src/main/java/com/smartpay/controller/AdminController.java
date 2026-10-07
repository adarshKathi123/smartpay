package com.smartpay.controller;

import com.smartpay.dto.AdminUserStatusResponse;
import com.smartpay.entity.User;
import com.smartpay.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Admin-only user management. The admin role is checked in the database.")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "Freeze a user (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User is frozen (also returned if already frozen)"),
            @ApiResponse(responseCode = "400", description = "Admins cannot freeze their own account"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{userId}/freeze")
    public AdminUserStatusResponse freeze(Authentication authentication, @PathVariable Long userId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        User user = adminUserService.setFrozen(actorUserId, userId, true);
        return new AdminUserStatusResponse(userId, String.valueOf(user.getStatus()));
    }

    @Operation(summary = "Unfreeze a user (admin only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User is active (also returned if already active)"),
            @ApiResponse(responseCode = "400", description = "Admins cannot change their own account"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/{userId}/unfreeze")
    public AdminUserStatusResponse unfreeze(Authentication authentication, @PathVariable Long userId) {
        Long actorUserId = Long.valueOf(authentication.getName());
        User user = adminUserService.setFrozen(actorUserId, userId, false);
        return new AdminUserStatusResponse(userId, String.valueOf(user.getStatus()));
    }
}