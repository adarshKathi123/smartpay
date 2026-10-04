package com.smartpay.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smartpay.dto.RegisterRequest;
import com.smartpay.dto.UserResponse;
import com.smartpay.entity.Role;
import com.smartpay.entity.User;
import com.smartpay.entity.UserStatus;
import com.smartpay.exception.EmailAlreadyExistsException;
import com.smartpay.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // No @Transactional: this method does one save, and save() already runs
    // in its own transaction. Add it later when several writes must succeed together.
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = buildUser(request, email);
        User saved = saveUser(user);
        return toResponse(saved);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private User buildUser(RegisterRequest request, String email) {
        LocalDateTime now = LocalDateTime.now();

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }

    private User saveUser(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // Two people registering the same email at the same moment can both
            // pass existsByEmail; the database unique key then rejects the second.
            throw new EmailAlreadyExistsException();
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt());
    }
}