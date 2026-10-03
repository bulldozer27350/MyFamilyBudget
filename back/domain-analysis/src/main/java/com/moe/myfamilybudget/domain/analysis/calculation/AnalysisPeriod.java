package com.moe.myfamilybudget.domain.analysis.calculation;

import java.time.LocalDate;

/**
 * Période d'analyse (RF-600, voir doc/architecture/08-domaine-analyse.md).
 *
 * <p>Porte la date du jour explicitement : le moteur d'Analyse ne doit jamais appeler
 * {@code LocalDate.now()} lui-même, pour rester déterministe et testable. La Factory passe
 * {@code LocalDate.now()} en production.
 *
 * @param today     date du jour, référence pour la coupure et le mois courant
 * @param monthsBack profondeur de l'historique pris en compte, en mois ({@code 0} = tout
 *                   l'historique disponible)
 */
public record AnalysisPeriod(LocalDate today, int monthsBack) {

    public AnalysisPeriod {
        if (today == null) today = LocalDate.now();
        if (monthsBack < 0) monthsBack = 0;
    }
}
