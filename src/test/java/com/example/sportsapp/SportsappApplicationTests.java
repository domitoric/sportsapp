package com.example.sportsapp;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.dto.TeamDto;
import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.AppUserRepository;
import com.example.sportsapp.repository.MatchRepository;
import com.example.sportsapp.repository.PlayerRepository;
import com.example.sportsapp.repository.TeamRepository;
import com.example.sportsapp.service.DataNormalizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "tester")
/**
 * Basic integration tests for local application logic without mocking the external API.
 */
class SportsappApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DataNormalizationService dataNormalizationService;

    private AppUser testUser;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
        playerRepository.deleteAll();
        teamRepository.deleteAll();
        appUserRepository.deleteAll();
        testUser = appUserRepository.save(AppUser.builder()
                .username("tester")
                .passwordHash("hashed")
                .build());
    }

    /**
     * Verifies that the Spring context starts successfully.
     */
    @Test
    void contextLoads() {
    }

    /**
     * Verifies team creation through the REST API in the updated model without a city field.
     */
    @Test
    void createsTeamWithoutCityField() throws Exception {
        mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dynamo Kyiv"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dynamo Kyiv"))
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.losses").value(0));
    }

    /**
     * Verifies that saving a match correctly updates team statistics and player goals.
     */
    @Test
    void updatesStatsAndTopPlayersAfterSavingMatch() throws Exception {
        TeamDto teamOne = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dynamo Kyiv"
                                }
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        TeamDto teamTwo = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Shakhtar Donetsk"
                                }
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        PlayerDto playerOne = objectMapper.readValue(mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Andriy Shevchenko",
                                  "position": "Forward",
                                  "teamId": %d
                                }
                                """.formatted(teamOne.getId())))
                .andReturn().getResponse().getContentAsString(), PlayerDto.class);

        PlayerDto playerTwo = objectMapper.readValue(mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Serhii Rebrov",
                                  "position": "Forward",
                                  "teamId": %d
                                }
                                """.formatted(teamOne.getId())))
                .andReturn().getResponse().getContentAsString(), PlayerDto.class);

        PlayerDto awayPlayer = objectMapper.readValue(mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Away Striker",
                                  "position": "Forward",
                                  "teamId": %d
                                }
                                """.formatted(teamTwo.getId())))
                .andReturn().getResponse().getContentAsString(), PlayerDto.class);

        mockMvc.perform(post("/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-03-20",
                                  "homeTeamId": %d,
                                  "awayTeamId": %d,
                                  "scoreHome": 2,
                                  "scoreAway": 1,
                                  "scorers": [
                                    {"playerId": %d, "goals": 1},
                                    {"playerId": %d, "goals": 1},
                                    {"playerId": %d, "goals": 1}
                                  ]
                                }
                                """.formatted(
                                teamOne.getId(),
                                teamTwo.getId(),
                                playerOne.getId(),
                                playerTwo.getId(),
                                awayPlayer.getId()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scoreHome").value(2))
                .andExpect(jsonPath("$.scoreAway").value(1));

        mockMvc.perform(get("/teams/%d/stats".formatted(teamOne.getId())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.matchesPlayed").value(1))
                .andExpect(jsonPath("$.goalsScored").value(2))
                .andExpect(jsonPath("$.goalsConceded").value(1));

        mockMvc.perform(get("/players/top").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].goals").value(1));
    }

    /**
     * Verifies that the main HTML pages are available to the browser.
     */
    @Test
    void servesHtmlPagesOnBrowserRoutes() throws Exception {
        mockMvc.perform(get("/teams").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(get("/players/top").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(get("/matches").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    /**
     * Verifies that matches from the previous year do not affect current-year statistics.
     */
    @Test
    void ignoresPreviousYearMatchesInCurrentYearStats() throws Exception {
        TeamDto teamOne = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Current FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        TeamDto teamTwo = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Opponent FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        mockMvc.perform(post("/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2025-03-20",
                                  "homeTeamId": %d,
                                  "awayTeamId": %d,
                                  "scoreHome": 4,
                                  "scoreAway": 0
                                }
                                """.formatted(teamOne.getId(), teamTwo.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-03-20",
                                  "homeTeamId": %d,
                                  "awayTeamId": %d,
                                  "scoreHome": 2,
                                  "scoreAway": 1
                                }
                                """.formatted(teamOne.getId(), teamTwo.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/teams/%d/stats".formatted(teamOne.getId())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.matchesPlayed").value(1))
                .andExpect(jsonPath("$.goalsScored").value(2));
    }

    /**
     * Verifies that future matches are not counted as played.
     */
    @Test
    void ignoresFutureMatchesInCurrentYearStats() throws Exception {
        TeamDto teamOne = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Future FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        TeamDto teamTwo = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Future Opponent FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        LocalDate futureDate = LocalDate.now().plusDays(10);
        mockMvc.perform(post("/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "%s",
                                  "homeTeamId": %d,
                                  "awayTeamId": %d,
                                  "scoreHome": 0,
                                  "scoreAway": 0
                                }
                                """.formatted(futureDate, teamOne.getId(), teamTwo.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/teams/%d/stats".formatted(teamOne.getId())).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.draws").value(0))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.matchesPlayed").value(0));
    }

    /**
     * Verifies that only tracked teams appear in the REST response.
     */
    @Test
    void returnsOnlyTrackedTeamsInTeamApi() throws Exception {
        teamRepository.save(Team.builder()
                .owner(testUser)
                .name("Tracked FC")
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());

        teamRepository.save(Team.builder()
                .owner(testUser)
                .name("Untracked Opponent")
                .tracked(false)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());

        mockMvc.perform(get("/teams").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Tracked FC"));
    }

    /**
     * Verifies that the HTML sync route returns a redirect instead of a 500 error.
     */
    @Test
    void syncDatabaseRouteRedirectsInsteadOfThrowingServerError() throws Exception {
        mockMvc.perform(post("/sync-data")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirectTo", "/matches")
                        .param("scope", "matches"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches?sync=ok"));
    }

    /**
     * Verifies player deletion through the HTML flow.
     */
    @Test
    void deletesTrackedPlayerFromHtmlFlow() throws Exception {
        Team team = teamRepository.save(Team.builder()
                .owner(testUser)
                .name("Delete Player FC")
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());

        PlayerDto player = objectMapper.readValue(mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Delete Me",
                                  "position": "Forward",
                                  "teamId": %d
                                }
                                """.formatted(team.getId())))
                .andReturn().getResponse().getContentAsString(), PlayerDto.class);

        mockMvc.perform(post("/players/%d/delete".formatted(player.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/players/top"));

        mockMvc.perform(get("/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Verifies that deleting a team removes related players and matches.
     */
    @Test
    void deletesTrackedTeamAlongWithItsMatchesAndPlayersFromHtmlFlow() throws Exception {
        TeamDto homeTeam = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Delete Team FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);
        TeamDto awayTeam = objectMapper.readValue(mockMvc.perform(post("/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Opponent FC"}
                                """))
                .andReturn().getResponse().getContentAsString(), TeamDto.class);

        mockMvc.perform(post("/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Team Player",
                                  "position": "Midfielder",
                                  "teamId": %d
                                }
                                """.formatted(homeTeam.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-03-20",
                                  "homeTeamId": %d,
                                  "awayTeamId": %d,
                                  "scoreHome": 1,
                                  "scoreAway": 0
                                }
                                """.formatted(homeTeam.getId(), awayTeam.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/teams/%d/delete".formatted(homeTeam.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teams"));

        mockMvc.perform(get("/teams").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Opponent FC"));

        mockMvc.perform(get("/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/matches").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Regression test: deleting one tracked team must not reset goals for players from other teams.
     */
    @Test
    void deletingOneTrackedTeamDoesNotResetGoalsForPlayersOnOtherTeams() throws Exception {
        Team teamToDelete = teamRepository.save(Team.builder()
                .owner(testUser)
                .externalTeamId(1001L)
                .name("Delete Me FC")
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());
        Team otherTeam = teamRepository.save(Team.builder()
                .owner(testUser)
                .externalTeamId(1002L)
                .name("Keep Me FC")
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());

        playerRepository.save(Player.builder()
                .owner(testUser)
                .externalPlayerId(2001L)
                .name("Scorer")
                .position("Forward")
                .goals(8)
                .team(otherTeam)
                .build());

        mockMvc.perform(post("/teams/%d/delete".formatted(teamToDelete.getId()))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teams"));

        mockMvc.perform(get("/players").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Scorer"))
                .andExpect(jsonPath("$[0].goals").value(8));
    }

    /**
     * Verifies automatic merging of duplicate teams by external id and name.
     */
    @Test
    void mergesDuplicateTeamsByExternalIdAndName() throws Exception {
        teamRepository.save(Team.builder()
                .owner(testUser)
                .externalTeamId(133604L)
                .name("Arsenal")
                .tracked(true)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());
        teamRepository.save(Team.builder()
                .owner(testUser)
                .externalTeamId(133604L)
                .name("Arsenal")
                .tracked(false)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());
        teamRepository.save(Team.builder()
                .owner(testUser)
                .name("Arsenal")
                .tracked(false)
                .wins(0)
                .draws(0)
                .losses(0)
                .build());

        dataNormalizationService.normalizeData();

        mockMvc.perform(get("/teams").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Arsenal"));
    }
}

