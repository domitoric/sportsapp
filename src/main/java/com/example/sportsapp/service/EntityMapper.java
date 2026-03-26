package com.example.sportsapp.service;

import com.example.sportsapp.dto.MatchDto;
import com.example.sportsapp.dto.MatchScorerDto;
import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.dto.TeamDto;
import com.example.sportsapp.dto.TeamStatsDto;
import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.MatchScorer;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Перетворює JPA-сутності на DTO, які використовуються контролерами та представленнями.
 */
@Component
public class EntityMapper {

    /**
     * Перетворює сутність команди у DTO для REST-відповідей і шаблонів.
     */
    public TeamDto toTeamDto(Team team) {
        return TeamDto.builder()
                .id(team.getId())
                .name(team.getName())
                .wins(team.getWins())
                .draws(team.getDraws())
                .losses(team.getLosses())
                .build();
    }

    /**
     * Перетворює сутність гравця у DTO разом із назвою його команди.
     */
    public PlayerDto toPlayerDto(Player player) {
        return PlayerDto.builder()
                .id(player.getId())
                .name(player.getName())
                .position(player.getPosition())
                .goals(player.getGoals())
                .teamId(player.getTeam().getId())
                .teamName(player.getTeam().getName())
                .build();
    }

    /**
     * Перетворює сутність матчу у DTO разом зі списком авторів голів.
     */
    public MatchDto toMatchDto(Match match, List<MatchScorerDto> scorers) {
        return MatchDto.builder()
                .id(match.getId())
                .date(match.getDate())
                .homeTeamId(match.getHomeTeam().getId())
                .homeTeamName(match.getHomeTeam().getName())
                .awayTeamId(match.getAwayTeam().getId())
                .awayTeamName(match.getAwayTeam().getName())
                .scoreHome(match.getScoreHome())
                .scoreAway(match.getScoreAway())
                .scorers(scorers)
                .build();
    }

    /**
     * Перетворює внутрішній запис про автора голів у DTO для відповіді.
     */
    public MatchScorerDto toMatchScorerDto(MatchScorer scorer, String playerName) {
        return MatchScorerDto.builder()
                .playerId(scorer.getPlayerId())
                .playerName(playerName)
                .goals(scorer.getGoals())
                .build();
    }

    /**
     * Формує DTO статистики команди на основі збережених агрегатів і розрахункових значень.
     */
    public TeamStatsDto toTeamStatsDto(Team team, int matchesPlayed, int goalsScored, int goalsConceded) {
        return TeamStatsDto.builder()
                .teamId(team.getId())
                .teamName(team.getName())
                .wins(team.getWins())
                .draws(team.getDraws())
                .losses(team.getLosses())
                .matchesPlayed(matchesPlayed)
                .goalsScored(goalsScored)
                .goalsConceded(goalsConceded)
                .build();
    }
}
