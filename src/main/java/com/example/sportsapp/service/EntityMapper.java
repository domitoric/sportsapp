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
 * Maps JPA entities to DTOs used by controllers and views.
 */
@Component
public class EntityMapper {

    /**
     * Maps a team entity to a DTO for REST responses and templates.
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
     * Maps a player entity to a DTO together with the team name.
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
     * Maps a match entity to a DTO together with its scorer list.
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
     * Maps an internal scorer record to a response DTO.
     */
    public MatchScorerDto toMatchScorerDto(MatchScorer scorer, String playerName) {
        return MatchScorerDto.builder()
                .playerId(scorer.getPlayerId())
                .playerName(playerName)
                .goals(scorer.getGoals())
                .build();
    }

    /**
     * Builds a team statistics DTO from stored aggregates and calculated values.
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

