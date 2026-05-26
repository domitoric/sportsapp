package com.example.sportsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the sports tracker application.
 */
@SpringBootApplication
public class SportsappApplication {

    /**
     * Boots the Spring application context and starts the embedded web server.
     */
    public static void main(String[] args) {
        SpringApplication.run(SportsappApplication.class, args);
    }

}
