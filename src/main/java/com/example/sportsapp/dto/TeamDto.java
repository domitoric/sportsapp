package com.example.sportsapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для передачі даних відстежуваної команди між шарами застосунку.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamDto {

    private Long id;

    @NotBlank
    private String name;

    private int wins;

    private int draws;

    private int losses;
}
