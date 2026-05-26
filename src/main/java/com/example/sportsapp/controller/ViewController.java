package com.example.sportsapp.controller;

import com.example.sportsapp.dto.TrackTeamRequest;
import com.example.sportsapp.dto.TrackPlayerRequest;
import com.example.sportsapp.service.ExternalMatchParserService;
import com.example.sportsapp.service.MatchService;
import com.example.sportsapp.service.PlayerService;
import com.example.sportsapp.service.SeasonService;
import com.example.sportsapp.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * MVC controller that renders Thymeleaf pages and handles HTML forms.
 */
@Controller
@RequiredArgsConstructor
public class ViewController {

    private final TeamService teamService;
    private final PlayerService playerService;
    private final MatchService matchService;
    private final ExternalMatchParserService externalMatchParserService;
    private final SeasonService seasonService;

    /**
     * Renders the dashboard page with a short summary of local data.
     */
    @GetMapping("/")
    public String home(@RequestParam(name = "sync", defaultValue = "") String sync, Model model) {
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("currentYear", matchService.getDisplayedYear());
        model.addAttribute("syncStatus", sync);
        model.addAttribute("teams", teamService.getAll());
        model.addAttribute("players", playerService.getAll());
        model.addAttribute("matches", matchService.getAll());
        model.addAttribute("topPlayers", playerService.getTopPlayers());
        model.addAttribute("teamStats", teamService.getAll().stream()
                .map(team -> teamService.getStats(team.getId()))
                .toList());
        return "dashboard";
    }

    /**
     * Renders the teams page and, when requested, the list of teams for the selected league.
     */
    @GetMapping(value = "/teams", produces = "text/html")
    public String teams(@RequestParam(name = "league", defaultValue = "") String league,
                        @RequestParam(name = "sync", defaultValue = "") String sync,
                        Model model) {
        model.addAttribute("activePage", "teams");
        model.addAttribute("currentYear", matchService.getDisplayedYear());
        model.addAttribute("syncStatus", sync);
        model.addAttribute("teams", teamService.getAll());
        model.addAttribute("availableLeagues", externalMatchParserService.fetchAvailableLeagues());
        model.addAttribute("availableTeams",
                !league.isBlank() ? externalMatchParserService.fetchAvailableTeams(league) : java.util.List.of());
        model.addAttribute("selectedLeague", league);
        model.addAttribute("teamStats", teamService.getAll().stream()
                .map(team -> teamService.getStats(team.getId()))
                .toList());
        model.addAttribute("trackTeamForm", TrackTeamRequest.builder().build());
        return "teams";
    }

    /**
     * Renders the players page and the list of available external players for tracked teams.
     */
    @GetMapping(value = "/players/top", produces = "text/html")
    public String topPlayers(@RequestParam(name = "sync", defaultValue = "") String sync, Model model) {
        model.addAttribute("activePage", "players");
        model.addAttribute("currentYear", matchService.getDisplayedYear());
        model.addAttribute("syncStatus", sync);
        model.addAttribute("topPlayers", playerService.getTopPlayers());
        model.addAttribute("players", playerService.getAll());
        model.addAttribute("teams", teamService.getAll());
        model.addAttribute("availablePlayers", externalMatchParserService.fetchAvailablePlayersForTrackedTeams());
        model.addAttribute("trackPlayerForm", TrackPlayerRequest.builder().build());
        return "players-top";
    }

    /**
     * Renders the matches page for already tracked teams.
     */
    @GetMapping(value = "/matches", produces = "text/html")
    public String matches(@RequestParam(name = "sync", defaultValue = "") String sync, Model model) {
        model.addAttribute("activePage", "matches");
        model.addAttribute("currentYear", matchService.getDisplayedYear());
        model.addAttribute("syncStatus", sync);
        model.addAttribute("matches", externalMatchParserService.fetchMatches());
        return "matches";
    }

    /**
     * Adds an external team to the local tracked list through the HTML form.
     */
    @PostMapping(value = "/teams", consumes = "application/x-www-form-urlencoded")
    public String trackTeam(@ModelAttribute("trackTeamForm") TrackTeamRequest request) {
        externalMatchParserService.trackTeam(
                request.getExternalTeamId(),
                request.getExternalLeagueId(),
                request.getTeamName()
        );
        if (request.getLeagueName() != null && !request.getLeagueName().isBlank()) {
            return "redirect:/teams?league=" + request.getLeagueName().trim().replace(" ", "%20");
        }
        return "redirect:/teams";
    }

    /**
     * Deletes a team or removes it from tracking through the HTML form.
     */
    @PostMapping(value = "/teams/{id}/delete", consumes = "application/x-www-form-urlencoded")
    public String deleteTeam(@PathVariable Long id,
                             @RequestParam(name = "leagueName", defaultValue = "") String leagueName) {
        teamService.delete(id);
        if (!leagueName.isBlank()) {
            return "redirect:/teams?league=" + leagueName.trim().replace(" ", "%20");
        }
        return "redirect:/teams";
    }

    /**
     * Adds an external player to the local tracked list.
     */
    @PostMapping(value = "/players", consumes = "application/x-www-form-urlencoded")
    public String trackPlayer(@ModelAttribute("trackPlayerForm") TrackPlayerRequest request) {
        externalMatchParserService.trackPlayer(request.getExternalPlayerId())
                .or(() -> externalMatchParserService.fetchAvailablePlayersForTrackedTeams().stream()
                        .filter(player -> player.getExternalPlayerId().equals(request.getExternalPlayerId()))
                        .findFirst()
                        .map(player -> playerService.trackExternalPlayer(
                                player.getExternalPlayerId(),
                                player.getTeamId(),
                                player.getName(),
                                player.getPosition()
                        )));
        return "redirect:/players/top";
    }

    /**
     * Deletes a player through the HTML interface.
     */
    @PostMapping(value = "/players/{id}/delete", consumes = "application/x-www-form-urlencoded")
    public String deletePlayer(@PathVariable Long id) {
        playerService.delete(id);
        return "redirect:/players/top";
    }

    /**
     * Provides a dedicated route for syncing matches from the matches page.
     */
    @PostMapping(value = "/matches/sync", consumes = "application/x-www-form-urlencoded")
    public String syncMatches() {
        return syncDatabase("/matches");
    }

    /**
     * Runs a full or partial sync depending on the page and the requested scope.
     */
    @PostMapping(value = "/sync-data", consumes = "application/x-www-form-urlencoded")
    public String syncData(@RequestParam(name = "redirectTo", defaultValue = "/") String redirectTo,
                           @RequestParam(name = "scope", defaultValue = "") String scope,
                           @RequestParam(name = "league", defaultValue = "") String league) {
        return syncDatabase(redirectTo, scope, league);
    }

    /**
     * Shortcut sync entry point with automatic scope resolution.
     */
    private String syncDatabase(String redirectTo) {
        return syncDatabase(redirectTo, "", "");
    }

    /**
     * Syncs the requested data type and redirects the user back to the current page.
     */
    private String syncDatabase(String redirectTo, String scope) {
        return syncDatabase(redirectTo, scope, "");
    }

    private String syncDatabase(String redirectTo, String scope, String league) {
        String safeRedirect = switch (redirectTo) {
            case "/matches" -> "/matches";
            case "/players/top" -> "/players/top";
            case "/teams" -> "/teams";
            default -> "/";
        };
        String redirectSuffix = safeRedirect.equals("/teams") && league != null && !league.isBlank()
                ? "?league=" + league.trim().replace(" ", "%20")
                : "";
        // Each page syncs only the data it actually shows to reduce API usage.
        String effectiveScope = scope == null || scope.isBlank()
                ? switch (safeRedirect) {
                    case "/teams" -> "teams";
                    case "/players/top" -> "players";
                    case "/matches" -> "matches";
                    default -> "overview";
                }
                : scope;
        try {
            switch (effectiveScope) {
                case "teams" -> {
                    externalMatchParserService.syncTrackedTeams();
                    externalMatchParserService.importMatches();
                }
                case "players" -> externalMatchParserService.syncTrackedPlayers();
                case "matches" -> externalMatchParserService.importMatches();
                default -> {
                    externalMatchParserService.syncTrackedTeams();
                    externalMatchParserService.importMatches();
                }
            }
            return "redirect:" + safeRedirect + redirectSuffix + (redirectSuffix.isBlank() ? "?sync=ok" : "&sync=ok");
        } catch (Exception exception) {
            return "redirect:" + safeRedirect + redirectSuffix + (redirectSuffix.isBlank() ? "?sync=error" : "&sync=error");
        }
    }
}

