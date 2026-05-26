package com.example.sportsapp.service;

import com.example.sportsapp.dto.MatchDto;
import com.example.sportsapp.dto.MatchScorerDto;
import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.MatchScorer;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.MatchRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles match CRUD operations and validation rules.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MatchService {

    private final MatchRepository matchRepository;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final StatisticsService statisticsService;
    private final EntityMapper entityMapper;
    private final SeasonService seasonService;
    private final CurrentUserService currentUserService;

    /**
     * Creates a match in the local database and triggers statistics recalculation.
     */
    public MatchDto create(MatchDto dto) {
        Match saved = matchRepository.save(buildMatch(new Match(), dto));
        statisticsService.recalculateStatistics();
        return toDto(saved);
    }

    /**
     * Returns all matches for the year currently shown in the UI.
     */
    @Transactional(readOnly = true)
    public List<MatchDto> getAll() {
        List<Match> matches = matchRepository.findAllByOwner_IdOrderByDateDesc(currentUserService.getCurrentUserId());
        int relevantYear = seasonService.relevantYear();
        return matches.stream()
                .filter(match -> match.getDate() != null && match.getDate().getYear() == relevantYear)
                .map(this::toDto)
                .toList();
    }

    /**
     * Returns the year for which the user sees matches and statistics.
     */
    @Transactional(readOnly = true)
    public int getDisplayedYear() {
        return seasonService.relevantYear();
    }

    /**
     * Returns a single match by its local id.
     */
    @Transactional(readOnly = true)
    public MatchDto getById(Long id) {
        return toDto(findEntity(id));
    }

    /**
     * Updates a match and recalculates statistics.
     */
    public MatchDto update(Long id, MatchDto dto) {
        Match match = findEntity(id);
        Match saved = matchRepository.save(buildMatch(match, dto));
        statisticsService.recalculateStatistics();
        return toDto(saved);
    }

    /**
     * Deletes a match and triggers statistics recalculation.
     */
    public void delete(Long id) {
        matchRepository.delete(findEntity(id));
        statisticsService.recalculateStatistics();
    }

    /**
     * Returns the match entity or throws if the match is not found.
     */
    @Transactional(readOnly = true)
    public Match findEntity(Long id) {
        return matchRepository.findByIdAndOwner_Id(id, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new EntityNotFoundException("Match not found: " + id));
    }

    /**
     * Populates a match entity from a DTO and validates basic constraints.
     */
    private Match buildMatch(Match match, MatchDto dto) {
        List<MatchScorerDto> scorers = dto.getScorers() == null ? List.of() : dto.getScorers();
        Team homeTeam = teamService.findEntity(dto.getHomeTeamId());
        Team awayTeam = teamService.findEntity(dto.getAwayTeamId());
        AppUser owner = currentUserService.getCurrentUser();
        if (homeTeam.getId().equals(awayTeam.getId())) {
            throw new IllegalArgumentException("Home and away teams must be different");
        }

        match.setOwner(owner);
        match.setDate(dto.getDate());
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScoreHome(dto.getScoreHome());
        match.setScoreAway(dto.getScoreAway());
        match.setTeams(new LinkedHashSet<>(List.of(homeTeam, awayTeam)));
        match.setScorers(mapScorers(scorers, homeTeam, awayTeam));
        validateScoreConsistency(match);
        return match;
    }

    /**
     * Converts scorer DTOs into embedded match scorer records.
     */
    private List<MatchScorer> mapScorers(List<MatchScorerDto> scorerDtos, Team homeTeam, Team awayTeam) {
        return scorerDtos.stream().map(dto -> {
            Player player = playerService.findEntity(dto.getPlayerId());
            Long teamId = player.getTeam().getId();
            if (!teamId.equals(homeTeam.getId()) && !teamId.equals(awayTeam.getId())) {
                throw new IllegalArgumentException("Scorer does not belong to one of the match teams: " + dto.getPlayerId());
            }
            return MatchScorer.builder()
                    .playerId(player.getId())
                    .goals(dto.getGoals())
                    .build();
        }).toList();
    }

    /**
     * Validates that the match score matches the total scorer goals.
     */
    private void validateScoreConsistency(Match match) {
        int scorerGoals = match.getScorers().stream().mapToInt(MatchScorer::getGoals).sum();
        int scoreGoals = match.getScoreHome() + match.getScoreAway();
        if (scorerGoals > 0 && scorerGoals != scoreGoals) {
            throw new IllegalArgumentException("Sum of scorer goals must match total score");
        }
    }

    /**
     * Maps a match entity to a DTO for REST responses.
     */
    private MatchDto toDto(Match match) {
        List<MatchScorerDto> scorers = match.getScorers().stream()
                .map(scorer -> entityMapper.toMatchScorerDto(scorer, playerService.findEntity(scorer.getPlayerId()).getName()))
                .toList();
        return entityMapper.toMatchDto(match, scorers);
    }
}

