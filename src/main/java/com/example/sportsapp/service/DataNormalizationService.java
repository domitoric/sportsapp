package com.example.sportsapp.service;

import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.MatchRepository;
import com.example.sportsapp.repository.PlayerRepository;
import com.example.sportsapp.repository.TeamRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Нормалізує дані після старту застосунку: об'єднує дублікати команд і виправляє зв'язки.
 */
@Service
@RequiredArgsConstructor
public class DataNormalizationService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final StatisticsService statisticsService;

    /**
     * Запускає нормалізацію даних після повного старту застосунку.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void normalizeData() {
        mergeDuplicateTeams();
        statisticsService.recalculateStatistics();
    }

    /**
     * Об'єднує дублікати команд за зовнішнім id або нормалізованою назвою.
     */
    private void mergeDuplicateTeams() {
        List<Team> allTeams = new ArrayList<>(teamRepository.findAll());
        if (allTeams.size() < 2) {
            return;
        }

        Map<String, List<Team>> groups = new LinkedHashMap<>();
        for (Team team : allTeams) {
            String key = groupKey(team);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(team);
        }

        for (List<Team> group : groups.values()) {
            if (group.size() > 1) {
                mergeTeamGroup(group);
            }
        }

        mergeNameOnlyTeamsIntoExternalTeams();
    }

    /**
     * Об'єднує одну групу дублікатів у єдиний основний запис.
     */
    private void mergeTeamGroup(List<Team> duplicates) {
        Team survivor = chooseSurvivor(duplicates);
        for (Team duplicate : duplicates) {
            if (duplicate.getId().equals(survivor.getId())) {
                continue;
            }
            mergeTeamIntoSurvivor(duplicate, survivor);
        }
        teamRepository.save(survivor);
    }

    /**
     * Додатково зливає локальні команди без external id у відповідні зовнішні записи.
     */
    private void mergeNameOnlyTeamsIntoExternalTeams() {
        List<Team> teams = teamRepository.findAll();
        Map<String, Team> externalTeamsByName = teams.stream()
                .filter(team -> team.getExternalTeamId() != null)
                .filter(team -> team.getName() != null && !team.getName().isBlank())
                .sorted(teamPriority())
                .collect(LinkedHashMap::new, (map, team) -> map.putIfAbsent(normalizeName(team.getName()), team), Map::putAll);

        for (Team team : teams) {
            if (team.getExternalTeamId() != null || team.getName() == null || team.getName().isBlank()) {
                continue;
            }
            Team survivor = externalTeamsByName.get(normalizeName(team.getName()));
            if (survivor != null && !survivor.getId().equals(team.getId())) {
                mergeTeamIntoSurvivor(team, survivor);
                teamRepository.save(survivor);
            }
        }
    }

    /**
     * Переносить усі зв'язки з команди-дубліката на команду, що вижила після злиття.
     */
    private void mergeTeamIntoSurvivor(Team source, Team survivor) {
        if (survivor.getExternalTeamId() == null && source.getExternalTeamId() != null) {
            survivor.setExternalTeamId(source.getExternalTeamId());
        }
        if (survivor.getExternalLeagueId() == null && source.getExternalLeagueId() != null) {
            survivor.setExternalLeagueId(source.getExternalLeagueId());
        }
        if ((survivor.getName() == null || survivor.getName().isBlank()) && source.getName() != null) {
            survivor.setName(source.getName());
        }
        survivor.setTracked(survivor.isTracked() || source.isTracked());

        for (Player player : playerRepository.findByTeam_IdOrderByNameAsc(source.getId())) {
            player.setTeam(survivor);
            playerRepository.save(player);
        }

        for (Match match : matchRepository.findAll()) {
            boolean changed = false;

            if (match.getHomeTeam().getId().equals(source.getId())) {
                match.setHomeTeam(survivor);
                changed = true;
            }
            if (match.getAwayTeam().getId().equals(source.getId())) {
                match.setAwayTeam(survivor);
                changed = true;
            }
            if (match.getTeams().removeIf(team -> team.getId().equals(source.getId()))) {
                match.getTeams().add(survivor);
                changed = true;
            }

            if (!changed) {
                continue;
            }

            if (match.getHomeTeam().getId().equals(match.getAwayTeam().getId())) {
                matchRepository.delete(match);
                continue;
            }

            match.setTeams(new LinkedHashSet<>(List.of(match.getHomeTeam(), match.getAwayTeam())));
            matchRepository.save(match);
        }

        teamRepository.delete(source);
    }

    /**
     * Обирає команду, яка залишиться основним записом після злиття.
     */
    private Team chooseSurvivor(List<Team> teams) {
        return teams.stream()
                .sorted(teamPriority())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Визначає пріоритет команди під час вибору основного запису.
     */
    private Comparator<Team> teamPriority() {
        return Comparator
                .comparing(Team::isTracked).reversed()
                .thenComparing(team -> team.getExternalTeamId() != null, Comparator.reverseOrder())
                .thenComparing(team -> team.getName() == null || team.getName().isBlank())
                .thenComparing(Team::getId);
    }

    /**
     * Формує ключ групування для пошуку дублікатів.
     */
    private String groupKey(Team team) {
        if (team.getExternalTeamId() != null) {
            return "ext:" + team.getExternalTeamId();
        }
        return "name:" + normalizeName(team.getName());
    }

    /**
     * Нормалізує назву команди для порівняння без урахування регістру.
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
