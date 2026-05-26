package com.example.sportsapp.controller;

import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.service.PlayerService;
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
 * REST controller for CRUD operations on players.
 */
@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    /**
     * Creates a new local player.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerDto create(@Valid @RequestBody PlayerDto dto) {
        return playerService.create(dto);
    }

    /**
     * Returns all players from the local database.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PlayerDto> getAll() {
        return playerService.getAll();
    }

    /**
     * Returns a single player by local id.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PlayerDto getById(@PathVariable Long id) {
        return playerService.getById(id);
    }

    /**
     * Updates local player data.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PlayerDto update(@PathVariable Long id, @Valid @RequestBody PlayerDto dto) {
        return playerService.update(id, dto);
    }

    /**
     * Deletes a player from the local database.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        playerService.delete(id);
    }

    /**
     * Returns the list of top scorers.
     */
    @GetMapping(value = "/top", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PlayerDto> getTopPlayers() {
        return playerService.getTopPlayers();
    }
}
