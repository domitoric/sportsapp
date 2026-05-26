package com.example.sportsapp.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO for adding an external team to local tracking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackTeamRequest {

    @NotNull
    private Long externalTeamId;

    private Long externalLeagueId;

    private String teamName;

    private String leagueName;
}

