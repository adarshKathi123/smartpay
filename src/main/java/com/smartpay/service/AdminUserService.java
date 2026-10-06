package com.smartpay.service;

import com.smartpay.entity.AuditLog;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.AuditLogRepository;
import com.smartpay.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AdminUserService(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // freeze = true freezes the user, freeze = false unfreezes.
    // The admin check reads the role from the database (not from the token),
    // so a role change takes effect immediately.
    @Transactional
    public User setFrozen(Long actorUserId, Long targetUserId, boolean freeze) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.FORBIDDEN, "Admin access required"));
        if (!"ADMIN".equals(String.valueOf(actor.getRole()))) {
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        if (actorUserId.equals(targetUserId)) {
            throw new BusinessRuleException(HttpStatus.BAD_REQUEST,
                    "Admins cannot freeze or unfreeze their own account");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND, "User not found"));

        boolean alreadyFrozen = target.getStatus() != null && "FROZEN".equals(target.getStatus().name());
        if (freeze == alreadyFrozen) {
            return target; // nothing to change, nothing to log
        }

        target.setStatus(freeze ? UserStatus.FROZEN : UserStatus.ACTIVE);
        userRepository.save(target);

        String action = freeze ? "USER_FROZEN" : "USER_UNFROZEN";
        auditLogRepository.save(new AuditLog(actorUserId, targetUserId, action,
                "Status changed by admin " + actorUserId));
        return target;
    }
}