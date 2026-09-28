package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Moteur de règles de pause automatique des versements (RF-301, voir
 * doc/architecture/05-domaine-patrimoine.md, « Deux concepts métier à séparer explicitement »).
 *
 * <p>Il ne calcule aucune valeur de placement : il décide uniquement, à partir de la trésorerie
 * et du solde des placements surveillés, quel niveau de pause s'applique à la période suivante.
 * Les moteurs de projection ({@link PatrimoineProjectionService},
 * {@link PlacementEvolutionService}) l'appellent en fin de période, ce qui garantit une seule
 * règle pour le pas annuel et le pas mensuel.
 *
 * <p>Il ne dépend que des valeurs de configuration statiques des placements
 * ({@code pausePriority}, {@code pauseTriggerBalance}) et des seuils de trésorerie ; aucun
 * modèle persistant. Les priorités de sweep ({@code sweepPriority}, {@code sweepCap}) ne sont pas
 * encore consommées ici (voir RF-400).
 */
public final class ContributionPauseRules {

    private final List<PlacementProjectionInput> placements;
    private final BigDecimal cashCeiling;
    private final BigDecimal cashAlertThreshold;
    private final int maxPauseLevel;
    private final List<Integer> watchedIndexes = new ArrayList<>();

    /**
     * @param placements         placements du budget, dans un ordre stable : l'index d'un placement
     *                           dans cette liste sert de clé à {@link #next}
     * @param cashCeiling        plafond de trésorerie relâchant la pause, {@code null} si non configuré
     * @param cashAlertThreshold seuil d'alerte de trésorerie déclenchant la pause, {@code null} si non configuré
     */
    public ContributionPauseRules(
            List<PlacementProjectionInput> placements, BigDecimal cashCeiling, BigDecimal cashAlertThreshold) {
        this.placements = List.copyOf(placements);
        this.cashCeiling = cashCeiling;
        this.cashAlertThreshold = cashAlertThreshold;
        int max = 0;
        for (int i = 0; i < this.placements.size(); i++) {
            PlacementProjectionInput p = this.placements.get(i);
            if (p.pausePriority() != null) {
                max = Math.max(max, p.pausePriority());
            }
            if (p.pauseTriggerBalance() != null) {
                watchedIndexes.add(i);
            }
        }
        this.maxPauseLevel = max;
    }

    public PauseState initialState() {
        return new PauseState(0, 0);
    }

    /** Vrai si les versements du placement sont suspendus au niveau de pause courant. */
    public boolean isPaused(PlacementProjectionInput placement, PauseState state) {
        return placement.pausePriority() != null && placement.pausePriority() <= state.level();
    }

    /**
     * Réévalue le niveau de pause en fin de période ; la décision s'applique à la période suivante.
     *
     * @param previous        état à l'issue de la période précédente
     * @param treasuryBalance trésorerie (approximée) à l'issue de la période
     * @param balanceOf       solde du scénario corrigé du placement d'index donné dans la liste
     *                        fournie à la construction ; {@code null} si le solde est inconnu
     *                        (le placement n'est alors pas compté comme en alerte)
     */
    public PauseState next(PauseState previous, BigDecimal treasuryBalance, IntFunction<BigDecimal> balanceOf) {
        int fromCashAlert = previous.levelFromCashAlert();
        boolean cashAlertTriggered = cashAlertThreshold != null && treasuryBalance.compareTo(cashAlertThreshold) < 0;
        if (cashAlertTriggered) {
            fromCashAlert = Math.min(maxPauseLevel, fromCashAlert + 1);
        } else if (cashCeiling != null && treasuryBalance.compareTo(cashCeiling) >= 0) {
            fromCashAlert = Math.max(0, fromCashAlert - 1);
        }

        int alertCount = 0;
        for (int index : watchedIndexes) {
            BigDecimal balance = balanceOf.apply(index);
            if (balance != null && balance.compareTo(placements.get(index).pauseTriggerBalance()) < 0) {
                alertCount++;
            }
        }
        int fromAlerts = Math.min(maxPauseLevel, alertCount);
        return new PauseState(Math.max(fromCashAlert, fromAlerts), fromCashAlert);
    }
}
