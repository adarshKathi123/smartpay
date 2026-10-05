package com.smartpay.service;

import com.smartpay.dto.UserResponse;
import com.smartpay.entity.User;
import com.smartpay.exception.UserNotFoundException;
import com.smartpay.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getById(Long id) {
        User user = userRepository.findById(id).orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }
}
