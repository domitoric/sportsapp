package com.example.sportsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * Form DTO used by the registration page to collect a login and password.
 */
public class RegistrationRequest {

    @NotBlank(message = "Login is required")
    @Size(min = 3, max = 64, message = "Login must contain 3 to 64 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128, message = "Password must contain 6 to 128 characters")
    private String password;
}
