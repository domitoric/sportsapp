package com.example.sportsapp.service;

import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles player CRUD operations and external player tracking.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamService teamService;
    private final EntityMapper entityMapper;
    private final CurrentUserService currentUserService;

    /**
     * Creates a local player that the user adds manually.
     */
    public PlayerDto create(PlayerDto dto) {
        Team team = teamService.findEntity(dto.getTeamId());
        AppUser owner = currentUserService.getCurrentUser();
        Player player = Player.builder()
                .owner(owner)
                .externalPlayerId(null)
                .name(dto.getName())
                .position(dto.getPosition())
                .goals(0)
                .team(team)
                .build();
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Returns all players from the local database in alphabetical order.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getAll() {
        return playerRepository.findAllByOwner_IdOrderByNameAsc(currentUserService.getCurrentUserId()).stream()
                .map(entityMapper::toPlayerDto)
                .toList();
    }

    /**
     * Returns a single player by local id.
     */
    @Transactional(readOnly = true)
    public PlayerDto getById(Long id) {
        return entityMapper.toPlayerDto(findEntity(id));
    }

    /**
     * Updates the core data of a local player.
     */
    public PlayerDto update(Long id, PlayerDto dto) {
        Player player = findEntity(id);
        player.setName(dto.getName());
        player.setPosition(dto.getPosition());
        player.setTeam(teamService.findEntity(dto.getTeamId()));
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Deletes a player from the local database.
     */
    public void delete(Long id) {
        playerRepository.delete(findEntity(id));
    }

    /**
     * Returns the top players by goal count.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getTopPlayers() {
        return playerRepository.findTop10ByOwner_IdOrderByGoalsDescNameAsc(currentUserService.getCurrentUserId()).stream()
                .map(entityMapper::toPlayerDto)
                .toList();
    }

    /**
     * Returns the player entity or throws if it is not found.
     */
    @Transactional(readOnly = true)
    public Player findEntity(Long id) {
        return playerRepository.findByIdAndOwner_Id(id, currentUserService.getCurrentUserId())
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + id));
    }

    /**
     * Returns all players for a specific team.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getByTeam(Long teamId) {
        return playerRepository.findByTeam_IdAndOwner_IdOrderByNameAsc(teamId, currentUserService.getCurrentUserId()).stream()
                .map(entityMapper::toPlayerDto)
                .toList();
    }

    /**
     * Returns external players that have already been added to tracking.
     */
    @Transactional(readOnly = true)
    public List<Player> getTrackedExternalPlayers() {
        return playerRepository.findByExternalPlayerIdIsNotNullAndOwner_IdOrderByNameAsc(currentUserService.getCurrentUserId());
    }

    /**
     * Creates or updates the local record for a player from the external API.
     */
    public PlayerDto trackExternalPlayer(Long externalPlayerId, Long teamId, String name, String position) {
        Team team = teamService.findEntity(teamId);
        AppUser owner = currentUserService.getCurrentUser();
        Player player = playerRepository.findByExternalPlayerIdAndOwner_Id(externalPlayerId, owner.getId())
                .orElseGet(() -> Player.builder()
                        .owner(owner)
                        .externalPlayerId(externalPlayerId)
                        .goals(0)
                        .build());
        player.setName(name);
        player.setPosition(position);
        player.setTeam(team);
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Updates the goal count for a specific player.
     */
    public Player updateGoals(Long playerId, int goals) {
        Player player = findEntity(playerId);
        player.setGoals(goals);
        return playerRepository.save(player);
    }
}

