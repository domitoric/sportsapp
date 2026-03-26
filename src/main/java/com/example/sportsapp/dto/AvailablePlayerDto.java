package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для відображення зовнішнього гравця, якого можна додати до відстеження.
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
