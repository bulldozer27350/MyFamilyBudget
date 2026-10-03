package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Configuration d'un placement nécessaire à la projection patrimoniale (RF-300, voir
 * doc/architecture/05-domaine-patrimoine.md). L'historique de valorisation n'en fait
 * volontairement pas partie : il n'intéresse que le graphe d'évolution d'un placement.
 *
 * <p>Les champs {@code sweepPriority}, {@code sweepCap}, {@code pauseTriggerBalance} et
 * {@code pausePriority} sont des valeurs de configuration statiques stockées par placement, pas
 * une décision calculée (voir le point ouvert du fichier de domaine, traité en RF-400).
 *
 * @param id                     identifiant du placement
 * @param label                  libellé du placement (clé de rapprochement des retraits)
 * @param initialBalance         solde initial ({@code null} vaut 0)
 * @param balanceDate            date de référence du solde, {@code null} si non renseignée
 * @param monthlyContribution    versement mensuel ({@code null} vaut 0)
 * @param contributionFrom       premier mois de versement, {@code null} si non renseigné
 * @param contributionUntil      dernier mois de versement, {@code null} si sans fin
 * @param pessimisticRate        taux annuel du scénario pessimiste ({@code null} vaut 0)
 * @param expectedRate           taux annuel du scénario corrigé ({@code null} vaut 0)
 * @param optimisticRate         taux annuel du scénario optimiste ({@code null} vaut 0)
 * @param excludedFromRetirement vrai si le placement est exclu du patrimoine de départ à la retraite
 * @param sweepPriority          priorité de sweep, {@code null} si le placement n'en fait pas partie
 * @param sweepCap               plafond de sweep, {@code null} si sans plafond
 * @param pauseTriggerBalance    solde sous lequel le placement déclenche une pause, {@code null} sinon
 * @param pausePriority          niveau de pause à partir duquel les versements sont suspendus,
 *                               {@code null} si le placement n'est jamais mis en pause
 */
public record PlacementProjectionInput(
        String id,
        String label,
        BigDecimal initialBalance,
        LocalDate balanceDate,
        BigDecimal monthlyContribution,
        YearMonth contributionFrom,
        YearMonth contributionUntil,
        BigDecimal pessimisticRate,
        BigDecimal expectedRate,
        BigDecimal optimisticRate,
        boolean excludedFromRetirement,
        Integer sweepPriority,
        BigDecimal sweepCap,
        BigDecimal pauseTriggerBalance,
        Integer pausePriority) {

    public PlacementProjectionInput {
        initialBalance = initialBalance != null ? initialBalance : BigDecimal.ZERO;
        monthlyContribution = monthlyContribution != null ? monthlyContribution : BigDecimal.ZERO;
        pessimisticRate = pessimisticRate != null ? pessimisticRate : BigDecimal.ZERO;
        expectedRate = expectedRate != null ? expectedRate : BigDecimal.ZERO;
        optimisticRate = optimisticRate != null ? optimisticRate : BigDecimal.ZERO;
    }
}
