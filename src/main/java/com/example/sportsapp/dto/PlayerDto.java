package com.example.sportsapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для передачі даних гравця між шарами застосунку.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDto {

    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String position;

    private int goals;

    @NotNull
    private Long teamId;

    private String teamName;
}
