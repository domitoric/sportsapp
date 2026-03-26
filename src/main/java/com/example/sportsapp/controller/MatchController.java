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
 * REST-контролер для CRUD-операцій над матчами.
 */
@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final ExternalMatchParserService externalMatchParserService;

    /**
     * Створює матч і запускає перерахунок статистики.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@Valid @RequestBody MatchDto dto) {
        return matchService.create(dto);
    }

    /**
     * Повертає список матчів за релевантний для інтерфейсу рік.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> getAll() {
        return matchService.getAll();
    }

    /**
     * Повертає один матч за локальним ідентифікатором.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MatchDto getById(@PathVariable Long id) {
        return matchService.getById(id);
    }

    /**
     * Оновлює матч та пов'язані з ним дані.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public MatchDto update(@PathVariable Long id, @Valid @RequestBody MatchDto dto) {
        return matchService.update(id, dto);
    }

    /**
     * Видаляє матч і оновлює статистику команд та гравців.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        matchService.delete(id);
    }

    /**
     * Повертає вже імпортовані зовнішні матчі для tracked teams.
     */
    @GetMapping(value = "/external", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> fetchExternal() {
        return externalMatchParserService.fetchMatches();
    }

    /**
     * Імпортує матчі із зовнішнього API до локальної бази.
     */
    @PostMapping(value = "/external/import", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MatchDto> importExternal() {
        return externalMatchParserService.importMatches();
    }
}
