package com.example.sportsapp.service;

import com.example.sportsapp.dto.TeamDto;
import com.example.sportsapp.dto.TeamStatsDto;
import com.example.sportsapp.entity.AppUser;
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
 * Handles team CRUD operations, tracking state, and aggregated team statistics.
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
    private final CurrentUserService currentUserService;

    /**
     * Creates a local team that the user adds manually through the REST API.
     */
    public TeamDto create(TeamDto dto) {
        AppUser owner = currentUserService.getCurrentUser();
        Team team = Team.builder()
                .owner(owner)
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
     * Returns all tracked teams.
     */
    @Transactional(readOnly = true)
    public List<TeamDto> getAll() {
        return teamRepository.findByTrackedTrueAndOwner_IdOrderByNameAsc(currentUserService.getCurrentUserId()).stream()
                .map(entityMapper::toTeamDto)
                .toList();
    }

    /**
     * Returns a single team by local id.
     */
    @Transactional(readOnly = true)
    public TeamDto getById(Long id) {
        return entityMapper.toTeamDto(findEntity(id));
    }

    /**
     * Updates the core data of a local team.
     */
    public TeamDto update(Long id, TeamDto dto) {
        Team team = findEntity(id);
        team.setName(dto.getName());
        return entityMapper.toTeamDto(teamRepository.save(team));
    }

    /**
     * Deletes a local team or removes an external team from tracking.
     */
    public void delete(Long id) {
        Team team = findEntity(id);
        Long ownerId = currentUserService.getCurrentUserId();
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
        List<Match> affectedMatches = matchRepository.findAllByTeamIdAndOwnerId(id, ownerId);
        if (!affectedMatches.isEmpty()) {
            matchRepository.deleteAll(affectedMatches);
        }
        playerRepository.deleteByTeam_Id(id);
        teamRepository.delete(team);
        statisticsService.recalculateStatistics();
        restoreExternalPlayerGoals(preservedExternalGoals);
    }

    /**
     * Calculates statistics for a specific team for the current relevant year.
     */
    @Transactional(readOnly = true)
    public TeamStatsDto getStats(Long id) {
        Team team = findEntity(id);
        List<Match> matches = matchRepository.findAllByOwner_IdOrderByDateDesc(currentUserService.getCurrentUserId());
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
     * Returns the team entity or throws if it is not found.
     */
    @Transactional(readOnly = true)
    public Team findEntity(Long id) {
        return teamRepository.findByIdAndOwner_Id(id, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + id));
    }

    /**
     * Finds a team by name or creates a local opponent without tracking.
     */
    public Team findOrCreateByName(String name) {
        Long ownerId = currentUserService.getCurrentUserId();
        AppUser owner = currentUserService.getCurrentUser();
        return teamRepository.findByNameIgnoreCaseAndOwner_Id(name, ownerId)
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .owner(owner)
                        .name(name)
                        .tracked(false)
                        .wins(0)
                        .draws(0)
                        .losses(0)
                        .build()));
    }

    /**
     * Returns the canonical local team record for an external team id.
     */
    @Transactional(readOnly = true)
    public Optional<Team> findByExternalTeamId(Long externalTeamId) {
        return findCanonicalByExternalTeamId(externalTeamId);
    }

    /**
     * Creates or updates a tracked team imported from the external API.
     */
    public Team saveTrackedTeam(Long externalTeamId, Long externalLeagueId, String name) {
        if (externalTeamId == null) {
            throw new IllegalArgumentException("External team id is required");
        }
        AppUser owner = currentUserService.getCurrentUser();
        Team team = findCanonicalByExternalTeamId(externalTeamId)
                .orElseGet(() -> Team.builder()
                        .owner(owner)
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
     * Creates or updates an opponent record that exists only for stored matches.
     */
    public Team saveUntrackedOpponent(Long externalTeamId, String name) {
        AppUser owner = currentUserService.getCurrentUser();
        Team team = findCanonicalByExternalTeamId(externalTeamId)
                .orElseGet(() -> Team.builder()
                        .owner(owner)
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
     * Chooses the primary record among possible duplicates of the same external team.
     */
    private Optional<Team> findCanonicalByExternalTeamId(Long externalTeamId) {
        if (externalTeamId == null) {
            return Optional.empty();
        }
        return teamRepository.findAllByExternalTeamIdAndOwner_Id(externalTeamId, currentUserService.getCurrentUserId()).stream()
                .sorted(Comparator.comparing(Team::isTracked).reversed()
                .thenComparing(Team::getId))
                .findFirst();
    }

    /**
     * Preserves external player goals for other teams before deleting the current team.
     */
    private Map<Long, Integer> snapshotExternalPlayerGoalsExcludingTeam(Long teamId) {
        Map<Long, Integer> goalsByPlayerId = new HashMap<>();
        playerRepository.findByExternalPlayerIdIsNotNullAndOwner_IdOrderByNameAsc(currentUserService.getCurrentUserId()).stream()
                .filter(player -> player.getTeam() != null)
                .filter(player -> !player.getTeam().getId().equals(teamId))
                .forEach(player -> goalsByPlayerId.put(player.getId(), player.getGoals()));
        return goalsByPlayerId;
    }

    /**
     * Restores external player goals after statistics recalculation.
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

