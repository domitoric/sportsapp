package com.example.sportsapp.repository;

import com.example.sportsapp.entity.Match;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for storing matches and loading related entities.
 */
public interface MatchRepository extends JpaRepository<Match, Long> {

    @Override
    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    List<Match> findAll();

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    List<Match> findAllByOrderByDateDesc();

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    List<Match> findAllByOwner_IdOrderByDateDesc(Long ownerId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    Optional<Match> findByIdAndOwner_Id(Long id, Long ownerId);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    Optional<Match> findByDateAndHomeTeam_IdAndAwayTeam_Id(
            java.time.LocalDate date,
            Long homeTeamId,
            Long awayTeamId
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam", "teams", "scorers"})
    Optional<Match> findByDateAndHomeTeam_IdAndAwayTeam_IdAndOwner_Id(
            java.time.LocalDate date,
            Long homeTeamId,
            Long awayTeamId,
            Long ownerId
    );

    @Query("""
            select distinct m from Match m
            join m.teams t
            where t.id = :teamId and m.owner.id = :ownerId
            """)
    List<Match> findAllByTeamIdAndOwnerId(@Param("teamId") Long teamId, @Param("ownerId") Long ownerId);
}

