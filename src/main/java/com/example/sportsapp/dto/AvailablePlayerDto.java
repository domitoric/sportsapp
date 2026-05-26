package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO used to display an external player that can be added to tracking.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePlayerDto {

    private Long externalPlayerId;
    private String name;
    private String position;
    private Long teamId;
    private String teamName;
}

