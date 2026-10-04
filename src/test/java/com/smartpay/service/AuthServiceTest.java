package com.smartpay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.smartpay.dto.RegisterRequest;
import com.smartpay.dto.UserResponse;
import com.smartpay.entity.Role;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.exception.EmailAlreadyExistsException;
import com.smartpay.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    // A real encoder, so we can prove the stored value is a genuine hash
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void register_savesUserWithHashedPasswordAndDefaultRoleAndStatus() {
        RegisterRequest request = new RegisterRequest("Test User", "  Test@Example.com ", "password123");
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("test@example.com", saved.getEmail());
        assertEquals(Role.USER, saved.getRole());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("password123", saved.getPasswordHash()));

        assertEquals(1L, response.getId());
        assertEquals("Test User", response.getName());
        assertEquals("test@example.com", response.getEmail());
        assertEquals(Role.USER, response.getRole());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
    }

    @Test
    void register_throwsWhenEmailAlreadyExists_andDoesNotSave() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123");
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }
}