package com.example.sportsapp.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Вбудований запис про автора голів, що зберігається всередині матчу.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchScorer {

    private Long playerId;

    private int goals;
}
