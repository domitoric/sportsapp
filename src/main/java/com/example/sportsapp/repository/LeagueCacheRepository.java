package com.example.sportsapp.repository;

import com.example.sportsapp.entity.LeagueCache;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторій для кешованих довідкових даних про ліги.
 */
public interface LeagueCacheRepository extends JpaRepository<LeagueCache, Long> {

    List<LeagueCache> findAllByOrderByNameAsc();
}
