package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторій для збереження гравців і типових запитів до таблиці бомбардирів.
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findTop10ByOrderByGoalsDescNameAsc();

    List<Player> findAllByOrderByNameAsc();

    List<Player> findByTeam_IdOrderByNameAsc(Long teamId);

    void deleteByTeam_Id(Long teamId);

    Optional<Player> findByExternalPlayerId(Long externalPlayerId);

    List<Player> findByExternalPlayerIdIsNotNullOrderByNameAsc();
}
