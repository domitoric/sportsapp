package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для відображення зовнішньої команди, яку можна додати до відстеження.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableTeamDto {

    private Long externalTeamId;
    private Long externalLeagueId;
    private String name;
}
