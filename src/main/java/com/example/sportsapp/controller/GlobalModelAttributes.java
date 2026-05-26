package com.example.sportsapp.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Contributes shared view attributes that should be available on every HTML page.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    /**
     * Exposes the current username to Thymeleaf templates, or an empty string for anonymous users.
     */
    @ModelAttribute("currentUsername")
    public String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "";
        }
        return authentication.getName();
    }
}
