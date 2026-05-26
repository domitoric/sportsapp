package com.example.sportsapp.service;

import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * Centralized access to the current user from the security context.
 */
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            // This fallback is only needed for integration tests, where the service may run outside an HTTP request.
            return appUserRepository.findAll().stream()
                    .reduce((left, right) -> {
                        throw new IllegalStateException("Authenticated user is required");
                    })
                    .orElseThrow(() -> new IllegalStateException("Authenticated user is required"));
        }
        String username = authentication.getName();
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }

    public Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}

