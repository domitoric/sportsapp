package com.example.sportsapp.service;

import com.example.sportsapp.dto.AvailableLeagueDto;
import com.example.sportsapp.dto.AvailablePlayerDto;
import com.example.sportsapp.dto.AvailableTeamDto;
import com.example.sportsapp.dto.MatchDto;
import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.entity.LeagueCache;
import com.example.sportsapp.entity.Match;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.LeagueCacheRepository;
import com.example.sportsapp.repository.MatchRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Integrates the application with the external football API and imports its data.
 */
@Service
@RequiredArgsConstructor
public class ExternalMatchParserService {

    private final WebClient footballWebClient;
    private final TeamService teamService;
    private final PlayerService playerService;
    private final MatchRepository matchRepository;
    private final LeagueCacheRepository leagueCacheRepository;
    private final StatisticsService statisticsService;
    private final ObjectMapper objectMapper;
    private final SeasonService seasonService;
    private final CurrentUserService currentUserService;

    @Value("${football.api.season}")
    private int season;

    /**
     * Returns the list of available leagues.
     * Reads the local database cache first and falls back to the external API when the cache is empty.
     */
    @Transactional
    public List<AvailableLeagueDto> fetchAvailableLeagues() {
        // League catalog is effectively static, so we cache it locally after the first successful fetch.
        List<AvailableLeagueDto> cachedLeagues = leagueCacheRepository.findAllByOrderByNameAsc().stream()
                .map(league -> AvailableLeagueDto.builder()
                        .id(league.getId())
                        .code(league.getCode())
                        .name(league.getName())
                        .sport(league.getSport())
                        .build())
                .toList();
        if (!cachedLeagues.isEmpty()) {
            return cachedLeagues;
        }
        try {
            String response = footballWebClient.get()
                    .uri("competitions")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode competitionsNode = readArrayNode(response, "competitions");
            if (competitionsNode == null) {
                return List.of();
            }

            List<AvailableLeagueDto> leagues = streamOf(competitionsNode)
                    .filter(node -> "LEAGUE".equalsIgnoreCase(node.path("type").asText("")))
                    .map(node -> AvailableLeagueDto.builder()
                            .id(readLong(node, "id"))
                            .code(node.path("code").asText(""))
                            .name(node.path("name").asText(""))
                            .sport("Football")
                            .build())
                    .filter(league -> league.getId() != null && !league.getName().isBlank())
                    .sorted(Comparator.comparing(AvailableLeagueDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (!leagues.isEmpty()) {
                leagueCacheRepository.saveAll(leagues.stream()
                        .map(league -> LeagueCache.builder()
                                .id(league.getId())
                                .code(league.getCode())
                                .name(league.getName())
                                .sport(league.getSport())
                                .build())
                        .toList());
            }
            return leagues;
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * Loads the team list for the selected league from the external API.
     */
    @Transactional(readOnly = true)
    public List<AvailableTeamDto> fetchAvailableTeams(String leagueIdValue) {
        Long leagueId = parseLong(leagueIdValue);
        if (leagueId == null) {
            return List.of();
        }
        try {
            String response = footballWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("competitions/{id}/teams")
                            .queryParam("season", season)
                            .build(leagueId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode teamsNode = readArrayNode(response, "teams");
            if (teamsNode == null) {
                return List.of();
            }

            return streamOf(teamsNode)
                    .map(node -> AvailableTeamDto.builder()
                            .externalTeamId(readLong(node, "id"))
                            .externalLeagueId(leagueId)
                            .name(node.path("name").asText(""))
                            .build())
                    .filter(team -> team.getExternalTeamId() != null && !team.getName().isBlank())
                    .sorted(Comparator.comparing(AvailableTeamDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * Adds an external team to the local tracked list.
     */
    @Transactional
    public Team trackTeam(Long externalTeamId, Long externalLeagueId, String teamName) {
        if (externalTeamId == null) {
            throw new IllegalArgumentException("External team id is required");
        }
        String resolvedName = teamName == null ? "" : teamName.trim();
        Long resolvedCompetitionId = externalLeagueId;
        if (resolvedName.isBlank() || resolvedCompetitionId == null) {
            TeamMetadata metadata = fetchTeamMetadata(externalTeamId);
            if (resolvedName.isBlank()) {
                resolvedName = metadata.name();
            }
            if (resolvedCompetitionId == null) {
                resolvedCompetitionId = metadata.competitionId();
            }
        }
        if (resolvedName.isBlank()) {
            throw new IllegalArgumentException("Team name is required");
        }
        return teamService.saveTrackedTeam(externalTeamId, resolvedCompetitionId, resolvedName);
    }

    /**
     * Refreshes metadata for already tracked teams without importing matches.
     */
    public void syncTrackedTeams() {
        trackedTeams().forEach(trackedTeam -> {
            TeamMetadata metadata = fetchTeamMetadata(trackedTeam.getExternalTeamId());
            Long competitionId = trackedTeam.getExternalLeagueId() != null
                    ? trackedTeam.getExternalLeagueId()
                    : metadata.competitionId();
            String teamName = trackedTeam.getName() != null && !trackedTeam.getName().isBlank()
                    ? trackedTeam.getName()
                    : metadata.name();
            teamService.saveTrackedTeam(trackedTeam.getExternalTeamId(), competitionId, teamName);
        });
    }

    /**
     * Builds the list of available external players for all tracked teams.
     */
    @Transactional(readOnly = true)
    public List<AvailablePlayerDto> fetchAvailablePlayersForTrackedTeams() {
        Map<Long, AvailablePlayerDto> playersByExternalId = new LinkedHashMap<>();
        trackedTeams().stream()
                .flatMap(team -> fetchPlayersForTeam(team).stream())
                .forEach(player -> {
                    Long key = player.getExternalPlayerId() == null ? Long.MIN_VALUE + player.getTeamId() + player.getName().hashCode()
                            : player.getExternalPlayerId();
                    playersByExternalId.putIfAbsent(key, player);
                });
        return playersByExternalId.values().stream()
                .sorted(Comparator.comparing(AvailablePlayerDto::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Returns locally stored matches for tracked teams in the current year.
     */
    @Transactional(readOnly = true)
    public List<MatchDto> fetchMatches() {
        int currentYear = seasonService.currentYear();
        return matchRepository.findAllByOwner_IdOrderByDateDesc(currentUserService.getCurrentUserId()).stream()
                .filter(match -> match.getHomeTeam().isTracked() || match.getAwayTeam().isTracked())
                .filter(match -> match.getDate() != null && match.getDate().getYear() == currentYear)
                .map(match -> MatchDto.builder()
                        .id(match.getId())
                        .date(match.getDate())
                        .homeTeamId(match.getHomeTeam().getId())
                        .homeTeamName(match.getHomeTeam().getName())
                        .awayTeamId(match.getAwayTeam().getId())
                        .awayTeamName(match.getAwayTeam().getName())
                        .scoreHome(match.getScoreHome())
                        .scoreAway(match.getScoreAway())
                        .scorers(List.of())
                        .build())
                .toList();
    }

    /**
     * Imports matches for tracked teams and updates team stats and external player goals.
     */
    public List<MatchDto> importMatches() {
        List<Team> trackedTeams = trackedTeams();
        if (trackedTeams.isEmpty()) {
            return List.of();
        }
        // Keep the last known external totals if the API starts throttling during sync.
        Map<Long, Integer> existingExternalPlayerGoals = snapshotExternalPlayerGoals();

        Map<Long, Team> refreshedTrackedTeams = new LinkedHashMap<>();
        for (Team trackedTeam : trackedTeams) {
            TeamMetadata metadata = fetchTeamMetadata(trackedTeam.getExternalTeamId());
            Long competitionId = trackedTeam.getExternalLeagueId() != null
                    ? trackedTeam.getExternalLeagueId()
                    : metadata.competitionId();
            String teamName = !trackedTeam.getName().isBlank() ? trackedTeam.getName() : metadata.name();
            Team saved = teamService.saveTrackedTeam(trackedTeam.getExternalTeamId(), competitionId, teamName);
            refreshedTrackedTeams.put(saved.getExternalTeamId(), saved);
        }

        Map<Long, Team> trackedByExternalId = refreshedTrackedTeams.values().stream()
                .collect(Collectors.toMap(
                        Team::getExternalTeamId,
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        Map<String, Team> trackedByName = refreshedTrackedTeams.values().stream()
                .filter(team -> team.getName() != null && !team.getName().isBlank())
                .collect(Collectors.toMap(
                        team -> normalizeTeamName(team.getName()),
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        LinkedHashSet<Long> competitionIds = refreshedTrackedTeams.values().stream()
                .map(Team::getExternalLeagueId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Long competitionId : competitionIds) {
            for (JsonNode externalMatch : fetchCompetitionMatches(competitionId)) {
                importExternalMatch(externalMatch, trackedByExternalId, trackedByName);
            }
        }

        statisticsService.recalculateStatistics();
        syncTrackedPlayerGoals(existingExternalPlayerGoals, false);
        return fetchMatches();
    }

    /**
     * Updates goals for already tracked external players without re-importing matches.
     */
    public void syncTrackedPlayers() {
        // For the players page, we must propagate API limit failures so the UI can show sync=error.
        boolean allRequestsSucceeded = syncTrackedPlayerGoals(snapshotExternalPlayerGoals(), true);
        if (!allRequestsSucceeded) {
            throw new IllegalStateException("Failed to refresh player goals from external API");
        }
    }

    /**
     * Adds an external player to the local list and immediately syncs that player's goals.
     */
    @Transactional
    public Optional<PlayerDto> trackPlayer(Long externalPlayerId) {
        return fetchAvailablePlayersForTrackedTeams().stream()
                .filter(player -> player.getExternalPlayerId() != null)
                .filter(player -> player.getExternalPlayerId().equals(externalPlayerId))
                .findFirst()
                .map(player -> {
                    PlayerDto trackedPlayer = playerService.trackExternalPlayer(
                            player.getExternalPlayerId(),
                            player.getTeamId(),
                            player.getName(),
                            player.getPosition()
                    );
                    syncPlayerGoals(trackedPlayer.getId(), trackedPlayer.getTeamId(), player.getExternalPlayerId(), trackedPlayer.getGoals());
                    return trackedPlayer;
                });
    }

    private List<AvailablePlayerDto> fetchPlayersForTeam(Team team) {
        if (team.getExternalTeamId() == null) {
            return List.of();
        }
        try {
            String response = footballWebClient.get()
                    .uri("teams/{id}", team.getExternalTeamId())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = readRoot(response);
            JsonNode squadNode = root == null ? null : root.path("squad");
            if (squadNode == null || !squadNode.isArray()) {
                return List.of();
            }

            return streamOf(squadNode)
                    .map(node -> AvailablePlayerDto.builder()
                            .externalPlayerId(readLong(node, "id"))
                            .name(node.path("name").asText(""))
                            .position(node.path("position").asText("").isBlank() ? "Unknown" : node.path("position").asText(""))
                            .teamId(team.getId())
                            .teamName(team.getName())
                            .build())
                    .filter(player -> !player.getName().isBlank())
                    .sorted(Comparator.comparing(AvailablePlayerDto::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Team> trackedTeams() {
        Map<Long, Team> uniqueByExternalId = new LinkedHashMap<>();
        return teamService.getAll().stream()
                .map(teamDto -> teamService.findEntity(teamDto.getId()))
                .filter(team -> team.getExternalTeamId() != null)
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Team::getId))
                // The same external team id can be stored more than once, so we deduplicate it.
                .filter(team -> uniqueByExternalId.putIfAbsent(team.getExternalTeamId(), team) == null)
                .toList();
    }

    private List<JsonNode> fetchCompetitionMatches(Long competitionId) {
        try {
            String response = footballWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("competitions/{id}/matches")
                            .queryParam("season", season)
                            .build(competitionId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode matchesNode = readArrayNode(response, "matches");
            if (matchesNode == null) {
                return List.of();
            }
            return streamOf(matchesNode).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch matches for competition " + competitionId, exception);
        }
    }

    private void importExternalMatch(JsonNode externalMatch,
                                     Map<Long, Team> trackedByExternalId,
                                     Map<String, Team> trackedByName) {
        LocalDate matchDate = parseMatchDate(externalMatch.path("utcDate").asText(""));
        if (matchDate == null) {
            return;
        }

        Long homeExternalId = readLong(externalMatch.path("homeTeam"), "id");
        Long awayExternalId = readLong(externalMatch.path("awayTeam"), "id");
        String homeName = externalMatch.path("homeTeam").path("name").asText("");
        String awayName = externalMatch.path("awayTeam").path("name").asText("");

        Team homeTeam = homeExternalId == null ? null : trackedByExternalId.get(homeExternalId);
        Team awayTeam = awayExternalId == null ? null : trackedByExternalId.get(awayExternalId);
        // Name matching is a fallback for cases where API ids differ from saved ids.
        if (homeTeam == null && !homeName.isBlank()) {
            homeTeam = trackedByName.get(normalizeTeamName(homeName));
        }
        if (awayTeam == null && !awayName.isBlank()) {
            awayTeam = trackedByName.get(normalizeTeamName(awayName));
        }
        if (homeTeam == null && awayTeam == null) {
            return;
        }

        if (homeTeam == null && !homeName.isBlank()) {
            homeTeam = teamService.saveUntrackedOpponent(homeExternalId, homeName);
        }
        if (awayTeam == null && !awayName.isBlank()) {
            awayTeam = teamService.saveUntrackedOpponent(awayExternalId, awayName);
        }
        if (homeTeam == null || awayTeam == null) {
            return;
        }

        AppUser owner = currentUserService.getCurrentUser();
        Match match = matchRepository.findByDateAndHomeTeam_IdAndAwayTeam_IdAndOwner_Id(
                        matchDate,
                        homeTeam.getId(),
                        awayTeam.getId(),
                        owner.getId()
                )
                .orElseGet(Match::new);

        match.setOwner(owner);
        match.setDate(matchDate);
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setScoreHome(readNullableScore(externalMatch.path("score").path("fullTime").path("home")));
        match.setScoreAway(readNullableScore(externalMatch.path("score").path("fullTime").path("away")));
        match.setTeams(new LinkedHashSet<>(List.of(homeTeam, awayTeam)));
        match.setScorers(new ArrayList<>());
        matchRepository.save(match);
    }

    private TeamMetadata fetchTeamMetadata(Long externalTeamId) {
        if (externalTeamId == null) {
            return TeamMetadata.empty();
        }
        try {
            String response = footballWebClient.get()
                    .uri("teams/{id}", externalTeamId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = readRoot(response);
            if (root == null) {
                return TeamMetadata.empty();
            }

            Long competitionId = null;
            JsonNode competitionsNode = root.path("runningCompetitions");
            if (competitionsNode.isArray()) {
                competitionId = streamOf(competitionsNode)
                        .filter(node -> "LEAGUE".equalsIgnoreCase(node.path("type").asText("")))
                        .map(node -> readLong(node, "id"))
                        .filter(id -> id != null)
                        .findFirst()
                        .orElse(null);
            }

            return new TeamMetadata(root.path("name").asText("").trim(), competitionId);
        } catch (Exception exception) {
            return TeamMetadata.empty();
        }
    }

    private JsonNode readRoot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private JsonNode readArrayNode(String json, String fieldName) {
        JsonNode root = readRoot(json);
        if (root == null) {
            return null;
        }
        JsonNode node = root.path(fieldName);
        return node.isArray() ? node : null;
    }

    private Stream<JsonNode> streamOf(JsonNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false);
    }

    private Long readLong(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            return parseLong(value.asText(""));
        }
        return null;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate parseMatchDate(String utcDate) {
        if (utcDate == null || utcDate.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(utcDate).toLocalDate();
        } catch (Exception exception) {
            return null;
        }
    }

    private int readNullableScore(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        return node.asInt(0);
    }

    private boolean syncTrackedPlayerGoals(Map<Long, Integer> existingExternalPlayerGoals, boolean detectFailures) {
        boolean allRequestsSucceeded = true;
        for (Player player : playerService.getTrackedExternalPlayers()) {
            boolean playerSyncSucceeded = syncPlayerGoals(
                    player.getId(),
                    player.getTeam().getId(),
                    player.getExternalPlayerId(),
                    existingExternalPlayerGoals.get(player.getId())
            );
            if (detectFailures && !playerSyncSucceeded) {
                allRequestsSucceeded = false;
            }
        }
        return allRequestsSucceeded;
    }

    private boolean syncPlayerGoals(Long playerId, Long teamId, Long externalPlayerId, Integer fallbackGoals) {
        if (playerId == null || teamId == null || externalPlayerId == null) {
            return true;
        }
        try {
            Integer goals = fetchPlayerGoalsForCurrentYear(externalPlayerId);
            playerService.updateGoals(playerId, goals != null ? goals : fallbackGoals == null ? 0 : fallbackGoals);
            return true;
        } catch (Exception exception) {
            // When the API limit is hit or a request fails, keep the previous stored value instead of overwriting stats with zero.
            if (fallbackGoals != null) {
                playerService.updateGoals(playerId, fallbackGoals);
            }
            return false;
        }
    }

    private Integer fetchPlayerGoalsForCurrentYear(Long externalPlayerId) {
        int currentYear = seasonService.currentYear();
        LocalDate dateFrom = LocalDate.of(currentYear, 1, 1);
        LocalDate dateTo = LocalDate.of(currentYear, 12, 31);

        String response = footballWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("persons/{id}/matches")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .queryParam("limit", 100)
                        .build(externalPlayerId))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = readRoot(response);
        if (root == null) {
            return null;
        }

        JsonNode aggregationsNode = root.path("aggregations");
        if (aggregationsNode.isObject()) {
            return aggregationsNode.path("goals").asInt(0);
        }

        String goalEventsResponse = footballWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("persons/{id}/matches")
                        .queryParam("dateFrom", dateFrom)
                        .queryParam("dateTo", dateTo)
                        .queryParam("e", "GOAL")
                        .queryParam("limit", 100)
                        .build(externalPlayerId))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode goalEventsRoot = readRoot(goalEventsResponse);
        if (goalEventsRoot == null) {
            return null;
        }

        JsonNode countNode = goalEventsRoot.path("resultSet").path("count");
        if (countNode.isNumber()) {
            return countNode.asInt();
        }

        JsonNode matchesNode = goalEventsRoot.path("matches");
        if (matchesNode.isArray()) {
            return (int) matchesNode.size();
        }

        return null;
    }

    private String normalizeTeamName(String teamName) {
        return teamName == null ? "" : teamName.trim().toLowerCase(Locale.ROOT);
    }

    private Map<Long, Integer> snapshotExternalPlayerGoals() {
        return playerService.getTrackedExternalPlayers().stream()
                .collect(Collectors.toMap(Player::getId, Player::getGoals, (left, right) -> left, LinkedHashMap::new));
    }

    private record TeamMetadata(String name, Long competitionId) {
        private static TeamMetadata empty() {
            return new TeamMetadata("", null);
        }
    }
}

