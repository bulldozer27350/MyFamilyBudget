package com.moe.myfamilybudget.domain.bankpointage.calculation;

/**
 * Période de pointage : un mois calendaire au format {@code YYYY-MM} (voir {@link PointageInput}).
 */
public record PointagePeriod(String monthISO) {

    public PointagePeriod {
        if (monthISO == null) monthISO = "";
    }
}
