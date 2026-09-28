package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

/**
 * Flux de versements vers les placements pour une année, déjà agrégé tous placements confondus
 * (RF-400, voir doc/architecture/06-domaine-tresorerie.md).
 *
 * <p><b>Décision du point ouvert</b> (voir 05-domaine-patrimoine.md#point-ouvert et le principe
 * anti-cycle Patrimoine ↔ Trésorerie de 00-principes.md) : ce montant est une somme simple des
 * versements mensuels configurés sur la fenêtre {@code [contributionFrom, contributionUntil]} de
 * chaque placement, <b>sans tenir compte du mécanisme de pause</b> ({@code ContributionPauseRules})
 * du domaine Patrimoine. Trésorerie ne consomme donc pas la sortie simulée de
 * {@code PatrimoineProjectionService} : les deux domaines gardent chacun leur propre
 * approximation de l'autre (Patrimoine approxime déjà la trésorerie pour sa pause, voir
 * {@code PatrimoineProjectionParameters}), ce qui évite la dépendance retour interdite tout en
 * reproduisant exactement le comportement actuel de {@code TresorerieCalculationService}
 * (fonction {@code placementsMonthlyAnnualForYear}).
 *
 * @param year   année civile
 * @param amount versements annuels vers les placements, tous placements confondus ({@code null} vaut 0)
 */
public record PlacementCashflowInput(int year, BigDecimal amount) {
    public PlacementCashflowInput {
        amount = amount != null ? amount : BigDecimal.ZERO;
    }
}
