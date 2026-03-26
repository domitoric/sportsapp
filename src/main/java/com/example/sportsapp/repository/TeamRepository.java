package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Team;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторій для відстежуваних команд та імпортованих суперників.
 */
public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByNameIgnoreCase(String name);

    Optional<Team> findByExternalTeamId(Long externalTeamId);

    List<Team> findAllByExternalTeamId(Long externalTeamId);

    List<Team> findAllByOrderByNameAsc();

    List<Team> findByTrackedTrueOrderByNameAsc();
}
