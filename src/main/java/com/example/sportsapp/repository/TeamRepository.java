package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for tracked teams and imported opponents.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByIdAndOwner_Id(Long id, Long ownerId);

    Optional<Team> findByNameIgnoreCaseAndOwner_Id(String name, Long ownerId);

    List<Team> findAllByExternalTeamIdAndOwner_Id(Long externalTeamId, Long ownerId);

    List<Team> findAllByOrderByNameAsc();

    List<Team> findAllByOwner_IdOrderByNameAsc(Long ownerId);

    List<Team> findByTrackedTrueAndOwner_IdOrderByNameAsc(Long ownerId);
}

