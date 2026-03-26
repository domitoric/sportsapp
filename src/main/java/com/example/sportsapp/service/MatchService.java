package com.example.sportsapp.service;

import com.example.sportsapp.dto.MatchDto;
import com.example.sportsapp.dto.MatchScorerDto;
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
 * Обробляє CRUD-операції над матчами та правила валідації.
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

    /**
     * Створює матч у локальній базі та запускає перерахунок статистики.
     */
    public MatchDto create(MatchDto dto) {
        Match saved = matchRepository.save(buildMatch(new Match(), dto));
        statisticsService.recalculateStatistics();
        return toDto(saved);
    }

    /**
     * Повертає всі матчі за рік, який зараз показується в інтерфейсі.
     */
    @Transactional(readOnly = true)
    public List<MatchDto> getAll() {
        List<Match> matches = matchRepository.findAllByOrderByDateDesc();
        int relevantYear = seasonService.relevantYear();
        return matches.stream()
                .filter(match -> match.getDate() != null && match.getDate().getYear() == relevantYear)
                .map(this::toDto)
                .toList();
    }

    /**
     * Повертає рік, за який користувач бачить матчі та статистику.
     */
    @Transactional(readOnly = true)
    public int getDisplayedYear() {
        return seasonService.relevantYear();
    }

    /**
     * Повертає один матч за локальним ідентифікатором.
     */
    @Transactional(readOnly = true)
    public MatchDto getById(Long id) {
        return toDto(findEntity(id));
    }

    /**
     * Оновлює матч і повторно обчислює статистику.
     */
    public MatchDto update(Long id, MatchDto dto) {
        Match match = findEntity(id);
        Match saved = matchRepository.save(buildMatch(match, dto));
        statisticsService.recalculateStatistics();
        return toDto(saved);
    }

    /**
     * Видаляє матч і запускає перерахунок статистики.
     */
    public void delete(Long id) {
        matchRepository.delete(findEntity(id));
        statisticsService.recalculateStatistics();
    }

    /**
     * Повертає сутність матчу або кидає виняток, якщо матч не знайдено.
     */
    @Transactional(readOnly = true)
    public Match findEntity(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match not found: " + id));
    }

    /**
     * Заповнює сутність матчу даними з DTO і перевіряє базові обмеження.
     */
    private Match buildMatch(Match match, MatchDto dto) {
        List<MatchScorerDto> scorers = dto.getScorers() == null ? List.of() : dto.getScorers();
        Team homeTeam = teamService.findEntity(dto.getHomeTeamId());
        Team awayTeam = teamService.findEntity(dto.getAwayTeamId());
        if (homeTeam.getId().equals(awayTeam.getId())) {
            throw new IllegalArgumentException("Home and away teams must be different");
        }

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
     * Перетворює DTO авторів голів у вбудовані записи матчу.
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
     * Перевіряє узгодженість між рахунком матчу та сумою голів авторів.
     */
    private void validateScoreConsistency(Match match) {
        int scorerGoals = match.getScorers().stream().mapToInt(MatchScorer::getGoals).sum();
        int scoreGoals = match.getScoreHome() + match.getScoreAway();
        if (scorerGoals > 0 && scorerGoals != scoreGoals) {
            throw new IllegalArgumentException("Sum of scorer goals must match total score");
        }
    }

    /**
     * Перетворює сутність матчу у DTO для REST-відповіді.
     */
    private MatchDto toDto(Match match) {
        List<MatchScorerDto> scorers = match.getScorers().stream()
                .map(scorer -> entityMapper.toMatchScorerDto(scorer, playerService.findEntity(scorer.getPlayerId()).getName()))
                .toList();
        return entityMapper.toMatchDto(match, scorers);
    }
}
