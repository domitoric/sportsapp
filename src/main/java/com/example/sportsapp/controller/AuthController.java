package com.example.sportsapp.controller;

import com.example.sportsapp.dto.RegistrationRequest;
import com.example.sportsapp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
/**
 * MVC controller for the login and registration pages.
 */
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", defaultValue = "") String error,
                        @RequestParam(name = "logout", defaultValue = "") String logout,
                        @RequestParam(name = "registered", defaultValue = "") String registered,
                        Model model) {
        // Converts redirect query parameters into simple template flags.
        model.addAttribute("loginError", !error.isBlank());
        model.addAttribute("loggedOut", !logout.isBlank());
        model.addAttribute("registered", !registered.isBlank());
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationRequest", new RegistrationRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationRequest") RegistrationRequest request,
                           BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            authService.register(request);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException exception) {
            // Shows duplicate username errors directly next to the username field.
            bindingResult.rejectValue("username", "duplicate", exception.getMessage());
            return "register";
        }
    }
}

