package com.example.sportsapp.service;

import com.example.sportsapp.entity.Match;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Provides helper methods for the current season and statistics filtering.
 */
@Service
public class SeasonService {

    /**
     * Returns the server's current calendar year.
     */
    public int currentYear() {
        return LocalDate.now().getYear();
    }

    /**
     * Returns the server's current date.
     */
    public LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Checks whether a match belongs to the current year.
     */
    public boolean isCurrentYear(Match match) {
        return match.getDate() != null && match.getDate().getYear() == currentYear();
    }

    /**
     * Checks whether the match should already have been played at request time.
     */
    public boolean hasBeenPlayed(Match match) {
        return match.getDate() != null && !match.getDate().isAfter(today());
    }

    /**
     * Returns the year for which the application should display statistics.
     */
    public int relevantYear() {
        return currentYear();
    }

    /**
     * Checks whether a match belongs to the currently displayed year.
     */
    public boolean isRelevantYear(Match match) {
        return isCurrentYear(match);
    }
}

