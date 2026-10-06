package com.smartpay.service;

import com.smartpay.entity.AuditLog;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.entity.Role;
import com.smartpay.exception.BusinessRuleException;
import com.smartpay.repository.AuditLogRepository;
import com.smartpay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User adminUser() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        return admin;
    }

    @Test
    void freeze_asAdmin_setsFrozenAndWritesAuditLog() {
        User admin = adminUser();
        User target = mock(User.class);
        when(target.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminUserService.setFrozen(1L, 2L, true);

        verify(target).setStatus(UserStatus.FROZEN);
        verify(userRepository).save(target);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void unfreeze_asAdmin_setsActive() {
        User admin = adminUser();
        User target = mock(User.class);
        when(target.getStatus()).thenReturn(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminUserService.setFrozen(1L, 2L, false);

        verify(target).setStatus(UserStatus.ACTIVE);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void freeze_alreadyFrozen_changesNothing() {
        User admin = adminUser();
        User target = mock(User.class);
        when(target.getStatus()).thenReturn(UserStatus.FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminUserService.setFrozen(1L, 2L, true);

        verify(target, never()).setStatus(any(UserStatus.class));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void freeze_asNormalUser_returns403() {
        User normal = mock(User.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(normal));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> adminUserService.setFrozen(1L, 2L, true));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void freeze_ownAccount_returns400() {
        User admin = adminUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> adminUserService.setFrozen(1L, 1L, true));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void freeze_unknownTarget_returns404() {
        User admin = adminUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> adminUserService.setFrozen(1L, 9L, true));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}