package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO that contains calculated statistics for a tracked team.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamStatsDto {

    private Long teamId;
    private String teamName;
    private int wins;
    private int draws;
    private int losses;
    private int matchesPlayed;
    private int goalsScored;
    private int goalsConceded;
}

