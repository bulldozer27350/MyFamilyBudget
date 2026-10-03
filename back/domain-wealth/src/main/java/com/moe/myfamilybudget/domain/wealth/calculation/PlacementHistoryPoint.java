package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Valeur réelle constatée d'un placement à une date (voir {@link PlacementEvolutionInput}).
 *
 * @param date  date de la valeur constatée
 * @param value valeur constatée ({@code null} vaut 0)
 */
public record PlacementHistoryPoint(LocalDate date, BigDecimal value) {
    public PlacementHistoryPoint {
        value = value != null ? value : BigDecimal.ZERO;
    }
}
