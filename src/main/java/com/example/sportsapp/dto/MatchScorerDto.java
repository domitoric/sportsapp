package com.example.sportsapp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO, що описує одного автора голів у матчі.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScorerDto {

    @NotNull
    private Long playerId;

    private String playerName;

    @Min(1)
    private int goals;
}
