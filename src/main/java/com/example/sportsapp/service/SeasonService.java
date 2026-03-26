package com.example.sportsapp.service;

import com.example.sportsapp.entity.Match;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Надає допоміжні методи для роботи з поточним сезоном і фільтрації статистики.
 */
@Service
public class SeasonService {

    /**
     * Повертає поточний календарний рік сервера.
     */
    public int currentYear() {
        return LocalDate.now().getYear();
    }

    /**
     * Повертає поточну дату сервера.
     */
    public LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Перевіряє, чи належить матч до поточного року.
     */
    public boolean isCurrentYear(Match match) {
        return match.getDate() != null && match.getDate().getYear() == currentYear();
    }

    /**
     * Перевіряє, чи матч уже мав відбутися на момент запиту.
     */
    public boolean hasBeenPlayed(Match match) {
        return match.getDate() != null && !match.getDate().isAfter(today());
    }

    /**
     * Повертає рік, за який слід показувати статистику у застосунку.
     */
    public int relevantYear() {
        return currentYear();
    }

    /**
     * Перевіряє, чи належить матч до року, обраного для відображення.
     */
    public boolean isRelevantYear(Match match) {
        return isCurrentYear(match);
    }
}
