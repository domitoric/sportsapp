package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO, що містить обчислену статистику для відстежуваної команди.
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
