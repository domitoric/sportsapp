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
 * Повністю перебудовує статистику команд і гравців на основі збережених матчів.
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final SeasonService seasonService;

    /**
     * Повністю перебудовує статистику команд і локальних гравців за збереженими матчами.
     */
    @Transactional
    public void recalculateStatistics() {
        // Team aggregates are rebuilt from persisted matches instead of updated incrementally.
        List<Team> teams = teamRepository.findAll();
        for (Team team : teams) {
            team.setWins(0);
            team.setDraws(0);
            team.setLosses(0);
        }

        List<Player> players = playerRepository.findAll();
        Map<Long, Player> playersById = new HashMap<>();
        for (Player player : players) {
            player.setGoals(0);
            playersById.put(player.getId(), player);
        }

        List<Match> matches = matchRepository.findAll();
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
