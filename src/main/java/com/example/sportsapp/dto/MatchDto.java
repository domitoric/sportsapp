package com.example.sportsapp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для передачі даних матчу між контролерами та сервісами.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDto {

    private Long id;

    @NotNull
    private LocalDate date;

    @NotNull
    private Long homeTeamId;

    private String homeTeamName;

    @NotNull
    private Long awayTeamId;

    private String awayTeamName;

    @Min(0)
    private int scoreHome;

    @Min(0)
    private int scoreAway;

    @Valid
    @Builder.Default
    private List<MatchScorerDto> scorers = new ArrayList<>();
}
