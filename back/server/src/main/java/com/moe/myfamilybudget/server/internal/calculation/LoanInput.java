package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Prêt tel que le moteur d'analyse des prêts le consomme (RF-800, voir
 * doc/architecture/10-domaine-prets-suggestions.md).
 *
 * <p>Ne reprend que les champs réellement utilisés par le calcul : {@code initialAmount} et
 * {@code totalInstallments} de {@code LoanModel} n'y figurent volontairement pas. Les dates sont
 * au format ISO ({@code YYYY-MM-DD} ou {@code YYYY-MM}), comme dans le modèle source.
 *
 * @param id        identifiant du prêt
 * @param label     libellé affichable
 * @param crd       capital restant dû à la date de référence du prêt
 * @param rate      taux nominal annuel (fraction)
 * @param monthly   mensualité, assurance comprise
 * @param insurance part d'assurance de la mensualité
 * @param startDate date de référence du capital restant dû
 * @param endDate   date de fin du prêt, {@code null} si inconnue
 * @param stepDate  date de fin du palier de mensualité lissée, {@code null} si non applicable
 */
public record LoanInput(
        String id,
        String label,
        BigDecimal crd,
        BigDecimal rate,
        BigDecimal monthly,
        BigDecimal insurance,
        String startDate,
        String endDate,
        String stepDate) {
}
