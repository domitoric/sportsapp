package com.example.sportsapp.service;

import com.example.sportsapp.dto.PlayerDto;
import com.example.sportsapp.entity.Player;
import com.example.sportsapp.entity.Team;
import com.example.sportsapp.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Обробляє CRUD-операції над гравцями та відстеження зовнішніх гравців.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamService teamService;
    private final EntityMapper entityMapper;

    /**
     * Створює локального гравця, якого користувач додає вручну.
     */
    public PlayerDto create(PlayerDto dto) {
        Team team = teamService.findEntity(dto.getTeamId());
        Player player = Player.builder()
                .externalPlayerId(null)
                .name(dto.getName())
                .position(dto.getPosition())
                .goals(0)
                .team(team)
                .build();
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Повертає всіх гравців із локальної бази в алфавітному порядку.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getAll() {
        return playerRepository.findAllByOrderByNameAsc().stream().map(entityMapper::toPlayerDto).toList();
    }

    /**
     * Повертає одного гравця за локальним ідентифікатором.
     */
    @Transactional(readOnly = true)
    public PlayerDto getById(Long id) {
        return entityMapper.toPlayerDto(findEntity(id));
    }

    /**
     * Оновлює основні дані локального гравця.
     */
    public PlayerDto update(Long id, PlayerDto dto) {
        Player player = findEntity(id);
        player.setName(dto.getName());
        player.setPosition(dto.getPosition());
        player.setTeam(teamService.findEntity(dto.getTeamId()));
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Видаляє гравця з локальної бази.
     */
    public void delete(Long id) {
        playerRepository.delete(findEntity(id));
    }

    /**
     * Повертає топ гравців за кількістю голів.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getTopPlayers() {
        return playerRepository.findTop10ByOrderByGoalsDescNameAsc().stream()
                .map(entityMapper::toPlayerDto)
                .toList();
    }

    /**
     * Повертає сутність гравця або кидає виняток, якщо її не знайдено.
     */
    @Transactional(readOnly = true)
    public Player findEntity(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found: " + id));
    }

    /**
     * Повертає всіх гравців конкретної команди.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> getByTeam(Long teamId) {
        return playerRepository.findByTeam_IdOrderByNameAsc(teamId).stream()
                .map(entityMapper::toPlayerDto)
                .toList();
    }

    /**
     * Повертає зовнішніх гравців, яких уже додано до відстеження.
     */
    @Transactional(readOnly = true)
    public List<Player> getTrackedExternalPlayers() {
        return playerRepository.findByExternalPlayerIdIsNotNullOrderByNameAsc();
    }

    /**
     * Створює або оновлює локальний запис для гравця із зовнішнього API.
     */
    public PlayerDto trackExternalPlayer(Long externalPlayerId, Long teamId, String name, String position) {
        Team team = teamService.findEntity(teamId);
        Player player = playerRepository.findByExternalPlayerId(externalPlayerId)
                .orElseGet(() -> Player.builder()
                        .externalPlayerId(externalPlayerId)
                        .goals(0)
                        .build());
        player.setName(name);
        player.setPosition(position);
        player.setTeam(team);
        return entityMapper.toPlayerDto(playerRepository.save(player));
    }

    /**
     * Оновлює кількість голів для конкретного гравця.
     */
    public Player updateGoals(Long playerId, int goals) {
        Player player = findEntity(playerId);
        player.setGoals(goals);
        return playerRepository.save(player);
    }
}
