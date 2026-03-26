package com.example.sportsapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для відображення ліги, завантаженої із зовнішнього API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableLeagueDto {

    private Long id;
    private String code;
    private String name;
    private String sport;
}
