package com.example.sportsapp.service;

import com.example.sportsapp.dto.RegistrationRequest;
import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/**
 * Handles user registration and login-related persistence rules.
 */
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new local user after validating login uniqueness and hashing the password.
     */
    @Transactional
    public AppUser register(RegistrationRequest request) {
        String normalizedUsername = request.getUsername().trim();
        if (appUserRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException("Login is already in use");
        }
        return appUserRepository.save(AppUser.builder()
                .username(normalizedUsername)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build());
    }
}
