package com.example.sportsapp.repository;

import com.example.sportsapp.entity.LeagueCache;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for cached league reference data.
 */
public interface LeagueCacheRepository extends JpaRepository<LeagueCache, Long> {

    List<LeagueCache> findAllByOrderByNameAsc();
}

