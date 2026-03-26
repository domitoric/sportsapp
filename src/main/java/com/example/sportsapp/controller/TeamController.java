package com.example.sportsapp.controller;

import com.example.sportsapp.dto.TeamDto;
import com.example.sportsapp.dto.TeamStatsDto;
import com.example.sportsapp.service.TeamService;
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
 * REST-контролер для CRUD-операцій над командами та отримання статистики.
 */
@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /**
     * Створює нову локальну команду через REST API.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TeamDto create(@Valid @RequestBody TeamDto dto) {
        return teamService.create(dto);
    }

    /**
     * Повертає список усіх відстежуваних команд.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TeamDto> getAll() {
        return teamService.getAll();
    }

    /**
     * Повертає одну команду за її локальним ідентифікатором.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public TeamDto getById(@PathVariable Long id) {
        return teamService.getById(id);
    }

    /**
     * Оновлює назву та базові дані команди.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public TeamDto update(@PathVariable Long id, @Valid @RequestBody TeamDto dto) {
        return teamService.update(id, dto);
    }

    /**
     * Видаляє локальну команду або знімає зовнішню команду з відстеження.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        teamService.delete(id);
    }

    /**
     * Повертає розраховану статистику конкретної команди.
     */
    @GetMapping(value = "/{id}/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public TeamStatsDto getStats(@PathVariable Long id) {
        return teamService.getStats(id);
    }
}
