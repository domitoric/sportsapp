package com.example.sportsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsapp.dto.AvailableLeagueDto;
import com.example.sportsapp.dto.AvailablePlayerDto;
import com.example.sportsapp.dto.TeamStatsDto;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.LeagueCacheRepository;
import com.example.sportsapp.repository.MatchRepository;
import com.example.sportsapp.repository.PlayerRepository;
import com.example.sportsapp.repository.TeamRepository;
import com.example.sportsapp.service.ExternalMatchParserService;
import com.example.sportsapp.service.MatchService;
import com.example.sportsapp.service.TeamService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
/**
 * Інтеграційні тести сценаріїв роботи із зовнішнім футбольним API через MockWebServer.
 */
class SportsDbIntegrationTests {

    private static final MockWebServer SPORTS_DB_SERVER = startServer();
    private static final String API_PREFIX = "/v4";
    private static final Map<String, Queue<MockResponse>> RESPONSES = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalMatchParserService externalMatchParserService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private LeagueCacheRepository leagueCacheRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("football.api.base-url", () -> SPORTS_DB_SERVER.url(API_PREFIX + "/").toString());
        registry.add("football.api.token", () -> "test-token");
        registry.add("football.api.season", () -> "2025");
    }

    @BeforeEach
    void setUp() {
        RESPONSES.clear();
        matchRepository.deleteAll();
        playerRepository.deleteAll();
        teamRepository.deleteAll();
        leagueCacheRepository.deleteAll();
    }

    @AfterAll
    static void tearDown() throws IOException {
        SPORTS_DB_SERVER.shutdown();
    }

    /**
     * Перевіряє завантаження й алфавітне сортування ліг із зовнішнього API.
     */
    @Test
    void loadsAllReturnedLeaguesInAlphabeticalOrder() {
        mockJson("/competitions", """
                {
                  "competitions": [
                    {"id": 2019, "code": "SA", "name": "Serie A", "type": "LEAGUE"},
                    {"id": 2002, "code": "BL1", "name": "Bundesliga", "type": "LEAGUE"},
                    {"id": 9999, "code": "CL", "name": "Champions League", "type": "CUP"}
                  ]
                }
                """);

        List<AvailableLeagueDto> leagues = externalMatchParserService.fetchAvailableLeagues();

        assertThat(leagues).extracting(AvailableLeagueDto::getName)
                .containsExactly("Bundesliga", "Serie A");
    }

    /**
     * Перевіряє, що список ліг після першого завантаження перевикористовується з локального кешу.
     */
    @Test
    void reusesCachedLeaguesWithoutCallingApiAgain() {
        mockJson("/competitions", """
                {
                  "competitions": [
                    {"id": 2019, "code": "SA", "name": "Serie A", "type": "LEAGUE"},
                    {"id": 2002, "code": "BL1", "name": "Bundesliga", "type": "LEAGUE"}
                  ]
                }
                """);

        List<AvailableLeagueDto> firstLoad = externalMatchParserService.fetchAvailableLeagues();
        assertThat(firstLoad).extracting(AvailableLeagueDto::getName)
                .containsExactly("Bundesliga", "Serie A");

        List<AvailableLeagueDto> secondLoad = externalMatchParserService.fetchAvailableLeagues();
        assertThat(secondLoad).extracting(AvailableLeagueDto::getName)
                .containsExactly("Bundesliga", "Serie A");
    }

    /**
     * Перевіряє додавання команди зі списку зовнішньої ліги та коректний редірект назад.
     */
    @Test
    void tracksSelectedTeamFromPostedSelectionAndKeepsLeagueSelectionInRedirect() throws Exception {
        mockJson("/teams/133604", """
                {
                  "name": "Birmingham City",
                  "runningCompetitions": [
                    {"id": 2021, "type": "LEAGUE"}
                  ]
                }
                """);
        mockJson("/competitions/2021/matches", """
                {
                  "matches": []
                }
                """);

        mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("externalTeamId", "133604")
                        .param("externalLeagueId", "2021")
                        .param("teamName", "Birmingham City")
                        .param("leagueName", "2021"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teams?league=2021"));

        assertThat(teamService.getAll()).hasSize(1);
        assertThat(teamService.getAll().get(0).getName()).isEqualTo("Birmingham City");
    }

    /**
     * Перевіряє завантаження й сортування доступних гравців для tracked team.
     */
    @Test
    void fetchesPlayersForTrackedTeamWithFallbackAndSortsThemAlphabetically() {
        teamService.saveTrackedTeam(133604L, 2021L, "Arsenal FC");

        mockJson("/teams/133604", """
                {
                  "squad": [
                    {"id": 2, "name": "Zinchenko", "position": "Defence"},
                    {"id": 1, "name": "Aaron", "position": "Midfield"}
                  ]
                }
                """);

        List<AvailablePlayerDto> players = externalMatchParserService.fetchAvailablePlayersForTrackedTeams();

        assertThat(players).extracting(AvailablePlayerDto::getName)
                .containsExactly("Aaron", "Zinchenko");
    }

    /**
     * Перевіряє додавання зовнішнього гравця без повторного ручного запиту списку.
     */
    @Test
    void addsPlayerByExternalIdWithoutRequiringSecondListReload() throws Exception {
        teamService.saveTrackedTeam(133604L, 2021L, "Arsenal FC");

        mockJson("/teams/133604", """
                {
                  "squad": [
                    {
                      "id": 7788,
                      "name": "Bukayo Saka",
                      "position": "Attacker"
                    }
                  ]
                }
                """);

        mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("externalPlayerId", "7788"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/players/top"));

        assertThat(playerRepository.findAll()).hasSize(1);
        assertThat(playerRepository.findAll().get(0).getName()).isEqualTo("Bukayo Saka");
    }

    /**
     * Перевіряє отримання голів зовнішнього гравця за поточний рік.
     */
    @Test
    void trackedPlayerShowsGoalsForCurrentYearFromExternalApi() throws Exception {
        teamService.saveTrackedTeam(133604L, 2021L, "Arsenal FC");
        int currentYear = LocalDate.now().getYear();

        mockJson("/teams/133604", """
                {
                  "squad": [
                    {
                      "id": 7788,
                      "name": "Bukayo Saka",
                      "position": "Attacker"
                    }
                  ]
                }
                """);
        mockJson("/persons/7788/matches", """
                {
                  "aggregations": "paid only",
                  "matches": [
                    {
                      "utcDate": "%d-02-01T15:30:00Z"
                    }
                  ]
                }
                """.formatted(currentYear));
        mockJson("/persons/7788/matches", """
                {
                  "resultSet": {
                    "count": 7
                  },
                  "matches": [
                    {
                      "utcDate": "%d-02-01T15:30:00Z"
                    }
                  ]
                }
                """.formatted(currentYear));

        mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("externalPlayerId", "7788"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/players/top"));

        assertThat(playerRepository.findAll()).hasSize(1);
        assertThat(playerRepository.findAll().get(0).getGoals()).isEqualTo(7);
    }

    /**
     * Регресійний тест: при некоректній відповіді API старе значення голів не повинно затиратися.
     */
    @Test
    void syncKeepsPreviousPlayerGoalsWhenApiReturnsNoUsableGoalData() throws Exception {
        Team trackedTeam = teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC");
        Player player = playerRepository.save(Player.builder()
                .externalPlayerId(7788L)
                .name("Bukayo Saka")
                .position("Attacker")
                .goals(8)
                .team(trackedTeam)
                .build());
        int currentYear = LocalDate.now().getYear();

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 57, "name": "Arsenal FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(currentYear));
        mockJson("/persons/7788/matches", """
                {
                  "error": "rate limit reached"
                }
                """);
        mockJson("/persons/7788/matches", """
                {
                  "error": "rate limit reached"
                }
                """);

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/players/top")
                        .param("scope", "players"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/players/top?sync=ok"));

        assertThat(playerRepository.findById(player.getId())).get()
                .extracting(Player::getGoals)
                .isEqualTo(8);
    }

    /**
     * Перевіряє імпорт матчів і оновлення статистики tracked-команди тільки за поточний рік.
     */
    @Test
    void syncDataImportsMatchesAndUpdatesTrackedTeamStatisticsForCurrentYear() throws Exception {
        Long trackedTeamId = teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC").getId();
        int currentYear = LocalDate.now().getYear();

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 57, "name": "Arsenal FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    },
                    {
                      "utcDate": "%d-05-12T15:30:00Z",
                      "homeTeam": {"id": 61, "name": "Chelsea FC"},
                      "awayTeam": {"id": 57, "name": "Arsenal FC"},
                      "score": {"fullTime": {"home": 3, "away": 0}}
                    }
                  ]
                }
                """.formatted(currentYear, currentYear - 1));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));

        TeamStatsDto stats = teamService.getStats(trackedTeamId);
        assertThat(matchRepository.findAll()).hasSize(2);
        assertThat(stats.getMatchesPlayed()).isEqualTo(1);
        assertThat(stats.getWins()).isEqualTo(1);
        assertThat(stats.getLosses()).isEqualTo(0);
        assertThat(stats.getGoalsScored()).isEqualTo(2);
        assertThat(stats.getGoalsConceded()).isEqualTo(1);
    }

    /**
     * Перевіряє fallback-зв'язування матчу за назвою команди, якщо external ids не збігаються.
     */
    @Test
    void syncDataImportsCurrentYearMatchEvenWhenApiTeamIdsDoNotMatchButNamesDo() throws Exception {
        Long trackedTeamId = teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC").getId();
        int currentYear = LocalDate.now().getYear();

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 999999, "name": "Arsenal FC"},
                      "awayTeam": {"id": 888888, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(currentYear));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));

        TeamStatsDto stats = teamService.getStats(trackedTeamId);
        assertThat(stats.getMatchesPlayed()).isEqualTo(1);
        assertThat(matchService.getAll()).hasSize(1);
        assertThat(matchService.getAll().get(0).getHomeTeamName()).isEqualTo("Arsenal FC");
    }

    /**
     * Перевіряє, що матчі попереднього року не показуються після зовнішньої синхронізації.
     */
    @Test
    void syncDataShowsNoMatchesWhenApiReturnsOnlyPreviousYearData() throws Exception {
        Long trackedTeamId = teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC").getId();
        int previousYear = LocalDate.now().getYear() - 1;

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 57, "name": "Arsenal FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(previousYear));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));

        TeamStatsDto stats = teamService.getStats(trackedTeamId);
        assertThat(stats.getMatchesPlayed()).isEqualTo(0);
        assertThat(stats.getWins()).isEqualTo(0);
        assertThat(matchService.getDisplayedYear()).isEqualTo(LocalDate.now().getYear());
        assertThat(matchService.getAll()).isEmpty();
    }

    /**
     * Перевіряє, що sync не падає при дублях tracked-команд із однаковим external id.
     */
    @Test
    void syncDataDoesNotFailWhenDuplicateTrackedTeamsExistForSameExternalId() throws Exception {
        teamRepository.save(Team.builder()
                .externalTeamId(57L)
                .externalLeagueId(2021L)
                .name("Arsenal FC")
                .tracked(true)
                .wins(0)
                .losses(0)
                .build());
        teamRepository.save(Team.builder()
                .externalTeamId(57L)
                .externalLeagueId(2021L)
                .name("Arsenal Duplicate")
                .tracked(true)
                .wins(0)
                .losses(0)
                .build());

        mockJson("/teams/57", """
                {
                  "name": "Arsenal FC",
                  "runningCompetitions": [
                    {"id": 2021, "type": "LEAGUE"}
                  ]
                }
                """);
        mockJson("/teams/57", """
                {
                  "name": "Arsenal FC",
                  "runningCompetitions": [
                    {"id": 2021, "type": "LEAGUE"}
                  ]
                }
                """);
        int currentYear = LocalDate.now().getYear();
        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 57, "name": "Arsenal FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(currentYear));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));
    }

    /**
     * Перевіряє, що імпортовані матчі зберігаються після видалення і повторного додавання external team.
     */
    @Test
    void deletingAndReAddingExternalTeamKeepsImportedMatchesAvailable() throws Exception {
        Team trackedTeam = teamService.saveTrackedTeam(58L, 2021L, "Aston Villa FC");
        int currentYear = LocalDate.now().getYear();

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 58, "name": "Aston Villa FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(currentYear));
        mockJson("/teams/58", """
                {
                  "name": "Aston Villa FC",
                  "runningCompetitions": [
                    {"id": 2021, "type": "LEAGUE"}
                  ]
                }
                """);
        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-03-15T15:30:00Z",
                      "homeTeam": {"id": 58, "name": "Aston Villa FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 2, "away": 1}}
                    }
                  ]
                }
                """.formatted(currentYear));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));

        assertThat(externalMatchParserService.fetchMatches()).hasSize(1);

        mockMvc.perform(post("/teams/%d/delete".formatted(trackedTeam.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection());

        assertThat(externalMatchParserService.fetchMatches()).isEmpty();

        mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("externalTeamId", "58")
                        .param("externalLeagueId", "2021")
                        .param("teamName", "Aston Villa FC")
                        .param("leagueName", "2021"))
                .andExpect(status().is3xxRedirection());

        assertThat(externalMatchParserService.fetchMatches()).hasSize(1);
        assertThat(teamService.getAll()).extracting(team -> team.getName())
                .containsExactly("Aston Villa FC");
    }

    /**
     * Перевіряє, що додавання зовнішньої команди працює навіть без доступного імпорту матчів.
     */
    @Test
    void trackingTeamDoesNotFailEvenIfMatchImportWouldBeUnavailable() {
        Team trackedTeam = externalMatchParserService.trackTeam(57L, 2021L, "Arsenal FC");

        TeamStatsDto stats = teamService.getStats(trackedTeam.getId());
        assertThat(trackedTeam.getName()).isEqualTo("Arsenal FC");
        assertThat(stats.getMatchesPlayed()).isEqualTo(0);
        assertThat(externalMatchParserService.fetchMatches()).isEmpty();
    }

    /**
     * Перевіряє імпорт матчів одразу для двох tracked-команд з однієї ліги.
     */
    @Test
    void syncDataImportsMatchesForTwoTrackedTeamsFromSameCompetition() throws Exception {
        Long arsenalId = teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC").getId();
        Long villaId = teamService.saveTrackedTeam(58L, 2021L, "Aston Villa FC").getId();
        int currentYear = LocalDate.now().getYear();

        mockJson("/competitions/2021/matches", """
                {
                  "matches": [
                    {
                      "utcDate": "%d-01-03T17:30:00Z",
                      "homeTeam": {"id": 91, "name": "AFC Bournemouth"},
                      "awayTeam": {"id": 57, "name": "Arsenal FC"},
                      "score": {"fullTime": {"home": 2, "away": 3}}
                    },
                    {
                      "utcDate": "%d-02-10T15:00:00Z",
                      "homeTeam": {"id": 58, "name": "Aston Villa FC"},
                      "awayTeam": {"id": 61, "name": "Chelsea FC"},
                      "score": {"fullTime": {"home": 1, "away": 0}}
                    }
                  ]
                }
                """.formatted(currentYear, currentYear));

        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));

        assertThat(teamService.getStats(arsenalId).getMatchesPlayed()).isEqualTo(1);
        assertThat(teamService.getStats(villaId).getMatchesPlayed()).isEqualTo(1);
        assertThat(externalMatchParserService.fetchMatches()).hasSize(2);
    }

    /**
     * Перевіряє, що HTML-сторінка гравців показує зовнішній склад tracked teams.
     */
    @Test
    void rendersPlayersPageWithAvailablePlayersFromTrackedTeams() throws Exception {
        teamService.saveTrackedTeam(57L, 2021L, "Arsenal FC");

        mockJson("/teams/57", """
                {
                  "squad": [
                    {"id": 10, "name": "Martin Odegaard", "position": "Midfield"}
                  ]
                }
                """);

        mockMvc.perform(get("/players/top").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Martin Odegaard")));
    }

    /**
     * Додає моковану JSON-відповідь для конкретного API-шляху.
     */
    private void mockJson(String path, String body) {
        String normalizedPath = path.startsWith(API_PREFIX) ? path : API_PREFIX + path;
        RESPONSES.computeIfAbsent(normalizedPath, ignored -> new ConcurrentLinkedQueue<>())
                .add(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    /**
     * Підіймає локальний mock-сервер, який імітує відповіді зовнішнього API.
     */
    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String rawPath = request.getPath() == null ? "" : request.getPath();
                    String normalizedPath = rawPath.contains("?") ? rawPath.substring(0, rawPath.indexOf('?')) : rawPath;
                    Queue<MockResponse> responses = RESPONSES.get(normalizedPath);
                    if (responses == null || responses.isEmpty()) {
                        return new MockResponse()
                                .setResponseCode(404)
                                .setHeader("Content-Type", "application/json")
                                .setBody("{}");
                    }
                    return responses.poll();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start mock sports DB server", exception);
        }
    }
}
