package com.example.sportsapp.repository;

import com.example.sportsapp.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for user accounts used by registration and login.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Finds a user by login name.
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * Checks whether the requested login name is already taken.
     */
    boolean existsByUsername(String username);
}
