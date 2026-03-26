package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторій для збереження матчів і завантаження пов'язаних сутностей.
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    List<Match> findAll();

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    List<Match> findAllByOrderByDateDesc();

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    Optional<Match> findByDateAndHomeTeam_IdAndAwayTeam_Id(
            java.time.LocalDate date,
            Long homeTeamId,
            Long awayTeamId
    );
}
