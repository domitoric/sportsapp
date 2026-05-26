package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for storing players and common top-scorer queries.
 */
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByIdAndOwner_Id(Long id, Long ownerId);

    List<Player> findTop10ByOwner_IdOrderByGoalsDescNameAsc(Long ownerId);

    List<Player> findAllByOwner_IdOrderByNameAsc(Long ownerId);

    List<Player> findByTeam_IdAndOwner_IdOrderByNameAsc(Long teamId, Long ownerId);

    void deleteByTeam_Id(Long teamId);

    Optional<Player> findByExternalPlayerIdAndOwner_Id(Long externalPlayerId, Long ownerId);

    List<Player> findByExternalPlayerIdIsNotNullAndOwner_IdOrderByNameAsc(Long ownerId);
}

