package com.example.sportsapp.service;

import com.example.sportsapp.dto.TeamDto;
import com.example.sportsapp.dto.TeamStatsDto;
import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.MatchRepository;
import com.example.sportsapp.repository.PlayerRepository;
import com.example.sportsapp.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обробляє CRUD-операції над командами, стан відстеження та агреговану статистику команд.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final StatisticsService statisticsService;
    private final EntityMapper entityMapper;
    private final SeasonService seasonService;

    /**
     * Створює локальну команду, яку користувач додає вручну через REST API.
     */
    public TeamDto create(TeamDto dto) {
        Team team = Team.builder()
                .externalTeamId(dto.getId())
                .externalLeagueId(null)
                .name(dto.getName())
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build();
        return entityMapper.toTeamDto(teamRepository.save(team));
    }

    /**
     * Повертає всі відстежувані команди.
     */
    @Transactional(readOnly = true)
    public List<TeamDto> getAll() {
        return teamRepository.findByTrackedTrueOrderByNameAsc().stream().map(entityMapper::toTeamDto).toList();
    }

    /**
     * Повертає одну команду за локальним ідентифікатором.
     */
    @Transactional(readOnly = true)
    public TeamDto getById(Long id) {
        return entityMapper.toTeamDto(findEntity(id));
    }

    /**
     * Оновлює базові дані локальної команди.
     */
    public TeamDto update(Long id, TeamDto dto) {
        Team team = findEntity(id);
        team.setName(dto.getName());
        return entityMapper.toTeamDto(teamRepository.save(team));
    }

    /**
     * Видаляє локальну команду або знімає зовнішню команду з відстеження.
     */
    public void delete(Long id) {
        Team team = findEntity(id);
        Map<Long, Integer> preservedExternalGoals = snapshotExternalPlayerGoalsExcludingTeam(id);
        if (team.getExternalTeamId() != null) {
            playerRepository.deleteByTeam_Id(id);
            team.setTracked(false);
            team.setWins(0);
            team.setDraws(0);
            team.setLosses(0);
            teamRepository.save(team);
            statisticsService.recalculateStatistics();
            restoreExternalPlayerGoals(preservedExternalGoals);
            return;
        }
        List<Match> affectedMatches = matchRepository.findAll().stream()
                .filter(match -> match.getHomeTeam().getId().equals(id) || match.getAwayTeam().getId().equals(id))
                .toList();
        if (!affectedMatches.isEmpty()) {
            matchRepository.deleteAll(affectedMatches);
        }
        playerRepository.deleteByTeam_Id(id);
        teamRepository.delete(team);
        statisticsService.recalculateStatistics();
        restoreExternalPlayerGoals(preservedExternalGoals);
    }

    /**
     * Розраховує статистику конкретної команди за поточний релевантний рік.
     */
    @Transactional(readOnly = true)
    public TeamStatsDto getStats(Long id) {
        Team team = findEntity(id);
        List<Match> matches = matchRepository.findAll();
        int relevantYear = seasonService.relevantYear();
        int matchesPlayed = 0;
        int goalsScored = 0;
        int goalsConceded = 0;
        for (Match match : matches) {
            if (match.getDate() == null
                    || match.getDate().getYear() != relevantYear
                    || !seasonService.hasBeenPlayed(match)) {
                continue;
            }
            if (match.getHomeTeam().getId().equals(id)) {
                matchesPlayed++;
                goalsScored += match.getScoreHome();
                goalsConceded += match.getScoreAway();
            } else if (match.getAwayTeam().getId().equals(id)) {
                matchesPlayed++;
                goalsScored += match.getScoreAway();
                goalsConceded += match.getScoreHome();
            }
        }
        return entityMapper.toTeamStatsDto(team, matchesPlayed, goalsScored, goalsConceded);
    }

    /**
     * Повертає сутність команди або кидає виняток, якщо її не знайдено.
     */
    @Transactional(readOnly = true)
    public Team findEntity(Long id) {
        return teamRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + id));
    }

    /**
     * Шукає команду за назвою або створює локального суперника без tracking.
     */
    public Team findOrCreateByName(String name) {
        return teamRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .name(name)
                        .tracked(false)
                        .wins(0)
                        .losses(0)
                        .build()));
    }

    /**
     * Повертає канонічний локальний запис команди за її зовнішнім ідентифікатором.
     */
    @Transactional(readOnly = true)
    public Optional<Team> findByExternalTeamId(Long externalTeamId) {
        return findCanonicalByExternalTeamId(externalTeamId);
    }

    /**
     * Створює або оновлює tracked-команду, імпортовану із зовнішнього API.
     */
    public Team saveTrackedTeam(Long externalTeamId, Long externalLeagueId, String name) {
        if (externalTeamId == null) {
            throw new IllegalArgumentException("External team id is required");
        }
        Team team = findCanonicalByExternalTeamId(externalTeamId)
                .orElseGet(() -> Team.builder()
                        .externalTeamId(externalTeamId)
                        .tracked(true)
                        .wins(0)
                        .draws(0)
                        .losses(0)
                        .build());
        if (externalLeagueId != null) {
            team.setExternalLeagueId(externalLeagueId);
        }
        team.setName(name);
        team.setTracked(true);
        return teamRepository.save(team);
    }

    /**
     * Створює або оновлює суперника, який потрібен лише для збережених матчів.
     */
    public Team saveUntrackedOpponent(Long externalTeamId, String name) {
        Team team = findCanonicalByExternalTeamId(externalTeamId)
                .orElseGet(() -> Team.builder()
                        .externalTeamId(externalTeamId)
                        .tracked(false)
                        .wins(0)
                        .draws(0)
                        .losses(0)
                        .build());
        team.setName(name);
        return teamRepository.save(team);
    }

    /**
     * Обирає основний запис серед можливих дублікатів однієї зовнішньої команди.
     */
    private Optional<Team> findCanonicalByExternalTeamId(Long externalTeamId) {
        if (externalTeamId == null) {
            return Optional.empty();
        }
        return teamRepository.findAllByExternalTeamId(externalTeamId).stream()
                .sorted(Comparator.comparing(Team::isTracked).reversed()
                .thenComparing(Team::getId))
                .findFirst();
    }

    /**
     * Зберігає голи зовнішніх гравців інших команд перед видаленням поточної команди.
     */
    private Map<Long, Integer> snapshotExternalPlayerGoalsExcludingTeam(Long teamId) {
        Map<Long, Integer> goalsByPlayerId = new HashMap<>();
        playerRepository.findByExternalPlayerIdIsNotNullOrderByNameAsc().stream()
                .filter(player -> player.getTeam() != null)
                .filter(player -> !player.getTeam().getId().equals(teamId))
                .forEach(player -> goalsByPlayerId.put(player.getId(), player.getGoals()));
        return goalsByPlayerId;
    }

    /**
     * Відновлює голи зовнішніх гравців після перерахунку статистики.
     */
    private void restoreExternalPlayerGoals(Map<Long, Integer> goalsByPlayerId) {
        if (goalsByPlayerId.isEmpty()) {
            return;
        }
        List<Player> playersToRestore = playerRepository.findAllById(goalsByPlayerId.keySet()).stream()
                .filter(player -> player.getExternalPlayerId() != null)
                .toList();
        playersToRestore.forEach(player -> player.setGoals(goalsByPlayerId.getOrDefault(player.getId(), 0)));
        playerRepository.saveAll(playersToRestore);
    }
}
