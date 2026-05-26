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
 * Normalizes data after application startup by merging duplicate teams and fixing their relations.
 */
@Service
@RequiredArgsConstructor
public class DataNormalizationService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final StatisticsService statisticsService;

    /**
     * Runs data normalization after the application has fully started.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void normalizeData() {
        mergeDuplicateTeams();
        statisticsService.recalculateAllStatistics();
    }

    /**
     * Merges duplicate teams by external id or normalized name.
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
     * Merges one duplicate group into a single primary record.
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
     * Also merges local teams without an external id into matching external records.
     */
    private void mergeNameOnlyTeamsIntoExternalTeams() {
        List<Team> teams = teamRepository.findAll();
        Map<Long, List<Team>> teamsByOwner = teams.stream()
                .filter(team -> team.getOwner() != null && team.getOwner().getId() != null)
                .collect(LinkedHashMap::new,
                        (map, team) -> map.computeIfAbsent(team.getOwner().getId(), ignored -> new ArrayList<>()).add(team),
                        Map::putAll);

        for (List<Team> ownerTeams : teamsByOwner.values()) {
            Map<String, Team> externalTeamsByName = ownerTeams.stream()
                    .filter(team -> team.getExternalTeamId() != null)
                    .filter(team -> team.getName() != null && !team.getName().isBlank())
                    .sorted(teamPriority())
                    .collect(LinkedHashMap::new,
                            (map, team) -> map.putIfAbsent(normalizeName(team.getName()), team),
                            Map::putAll);

            for (Team team : ownerTeams) {
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
    }

    /**
     * Moves all relations from the duplicate team to the team that survives the merge.
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

        for (Player player : playerRepository.findAll().stream()
                .filter(candidate -> candidate.getTeam() != null && candidate.getTeam().getId().equals(source.getId()))
                .toList()) {
            player.setTeam(survivor);
            player.setOwner(survivor.getOwner());
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
     * Chooses the team that remains the primary record after the merge.
     */
    private Team chooseSurvivor(List<Team> teams) {
        return teams.stream()
                .sorted(teamPriority())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Calculates team priority when choosing the primary record.
     */
    private Comparator<Team> teamPriority() {
        return Comparator
                .comparing(Team::isTracked).reversed()
                .thenComparing(team -> team.getExternalTeamId() != null, Comparator.reverseOrder())
                .thenComparing(team -> team.getName() == null || team.getName().isBlank())
                .thenComparing(Team::getId);
    }

    /**
     * Builds the grouping key used to find duplicates.
     */
    private String groupKey(Team team) {
        String ownerKey = team.getOwner() == null || team.getOwner().getId() == null
                ? "owner:none"
                : "owner:" + team.getOwner().getId();
        if (team.getExternalTeamId() != null) {
            return ownerKey + ":ext:" + team.getExternalTeamId();
        }
        return ownerKey + ":name:" + normalizeName(team.getName());
    }

    /**
     * Normalizes a team name for case-insensitive comparison.
     */
    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}

