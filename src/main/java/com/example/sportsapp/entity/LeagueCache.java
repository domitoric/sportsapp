package com.example.sportsapp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Local cache of league reference data used to reduce external API calls.
 */
@Entity
@Table(name = "league_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeagueCache {

    @Id
    private Long id;

    private String code;

    private String name;

    private String sport;
}

