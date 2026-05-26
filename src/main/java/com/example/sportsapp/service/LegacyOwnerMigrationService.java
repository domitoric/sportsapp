package com.example.sportsapp.service;

import com.example.sportsapp.entity.AppUser;
import com.example.sportsapp.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
/**
 * One-time migration that attaches legacy records without owner_id to a bootstrap user,
 * so the existing PostgreSQL database can survive the transition to per-user data ownership.
 */
public class LegacyOwnerMigrationService implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.username:legacy}")
    private String bootstrapUsername;

    @Value("${app.bootstrap.password:legacy12345}")
    private String bootstrapPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!hasLegacyRowsWithoutOwner("teams")
                && !hasLegacyRowsWithoutOwner("players")
                && !hasLegacyRowsWithoutOwner("matches")) {
            return;
        }

        // Assigns legacy project data to a technical user so it is not lost during migration.
        AppUser bootstrapUser = appUserRepository.findByUsername(bootstrapUsername)
                .orElseGet(() -> appUserRepository.save(AppUser.builder()
                        .username(bootstrapUsername)
                        .passwordHash(passwordEncoder.encode(bootstrapPassword))
                        .build()));

        Long ownerId = bootstrapUser.getId();
        jdbcTemplate.update("update teams set owner_id = ? where owner_id is null", ownerId);
        jdbcTemplate.update("update players set owner_id = ? where owner_id is null", ownerId);
        jdbcTemplate.update("update matches set owner_id = ? where owner_id is null", ownerId);

        jdbcTemplate.execute("alter table teams alter column owner_id set not null");
        jdbcTemplate.execute("alter table players alter column owner_id set not null");
        jdbcTemplate.execute("alter table matches alter column owner_id set not null");

        addForeignKeyIfMissing("teams", "fk_teams_owner");
        addForeignKeyIfMissing("players", "fk_players_owner");
        addForeignKeyIfMissing("matches", "fk_matches_owner");
    }

    private boolean hasLegacyRowsWithoutOwner(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where owner_id is null",
                Integer.class
        );
        return count != null && count > 0;
    }

    private void addForeignKeyIfMissing(String tableName, String constraintName) {
        // Spring SQL initialization runs on every startup, so the foreign key is added idempotently.
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from information_schema.table_constraints
                where table_name = ?
                  and constraint_name = ?
                """,
                Integer.class,
                tableName,
                constraintName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                "alter table " + tableName
                        + " add constraint " + constraintName
                        + " foreign key (owner_id) references app_users(id)"
        );
    }
}

