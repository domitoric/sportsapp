package com.example.sportsapp.service;

import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.MatchScorer;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.MatchRepository;
import com.example.sportsapp.repository.PlayerRepository;
import com.example.sportsapp.repository.TeamRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fully rebuilds team and player statistics from stored matches.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final SeasonService seasonService;
    private final CurrentUserService currentUserService;

    /**
     * Fully rebuilds team and local player statistics from stored matches.
     */
    @Transactional
    public void recalculateStatistics() {
        Long ownerId = currentUserService.getCurrentUserId();
        List<Team> teams = teamRepository.findAllByOwner_IdOrderByNameAsc(ownerId);
        List<Player> players = playerRepository.findAllByOwner_IdOrderByNameAsc(ownerId);
        List<Match> matches = matchRepository.findAllByOwner_IdOrderByDateDesc(ownerId);
        recalculateStatistics(teams, players, matches);
    }

    @Transactional
    public void recalculateAllStatistics() {
        recalculateStatistics(teamRepository.findAll(), playerRepository.findAll(), matchRepository.findAll());
    }

    private void recalculateStatistics(List<Team> teams, List<Player> players, List<Match> matches) {
        for (Team team : teams) {
            team.setWins(0);
            team.setDraws(0);
            team.setLosses(0);
        }

        Map<Long, Player> playersById = new HashMap<>();
        for (Player player : players) {
            player.setGoals(0);
            playersById.put(player.getId(), player);
        }

        int relevantYear = seasonService.relevantYear();
        for (Match match : matches) {
            if (match.getDate() == null
                    || match.getDate().getYear() != relevantYear
                    || !seasonService.hasBeenPlayed(match)) {
                continue;
            }
            if (match.getScoreHome() > match.getScoreAway()) {
                match.getHomeTeam().setWins(match.getHomeTeam().getWins() + 1);
                match.getAwayTeam().setLosses(match.getAwayTeam().getLosses() + 1);
            } else if (match.getScoreAway() > match.getScoreHome()) {
                match.getAwayTeam().setWins(match.getAwayTeam().getWins() + 1);
                match.getHomeTeam().setLosses(match.getHomeTeam().getLosses() + 1);
            } else {
                match.getHomeTeam().setDraws(match.getHomeTeam().getDraws() + 1);
                match.getAwayTeam().setDraws(match.getAwayTeam().getDraws() + 1);
            }

            for (MatchScorer scorer : match.getScorers()) {
                Player player = playersById.get(scorer.getPlayerId());
                if (player != null) {
                    player.setGoals(player.getGoals() + scorer.getGoals());
                }
            }
        }

        teamRepository.saveAll(teams);
        playerRepository.saveAll(players);
    }
}

