package com.example.sportsapp.controller;

import com.example.sportsapp.dto.MatchDto;
import com.example.sportsapp.service.ExternalMatchParserService;
import com.example.sportsapp.service.MatchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for CRUD operations on matches.
 */
@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ExternalMatchParserService externalMatchParserService;

    /**
     * Creates a match and triggers statistics recalculation.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@Valid @RequestBody MatchDto dto) {
        return matchService.create(dto);
    }

    /**
     * Returns matches for the year currently shown in the UI.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> getAll() {
        return matchService.getAll();
    }

    /**
     * Returns a single match by local id.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MatchDto getById(@PathVariable Long id) {
        return matchService.getById(id);
    }

    /**
     * Updates a match and its related data.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MatchDto update(@PathVariable Long id, @Valid @RequestBody MatchDto dto) {
        return matchService.update(id, dto);
    }

    /**
     * Deletes a match and refreshes team and player statistics.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        matchService.delete(id);
    }

    /**
     * Returns already imported external matches for tracked teams.
     */
    @GetMapping(value = "/external", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> fetchExternal() {
        return externalMatchParserService.fetchMatches();
    }

    /**
     * Imports matches from the external API into the local database.
     */
    @PostMapping(value = "/external/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> importExternal() {
        return externalMatchParserService.importMatches();
    }
}
