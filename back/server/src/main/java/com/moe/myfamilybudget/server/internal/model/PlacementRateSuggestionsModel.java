package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Suggestions de taux de rendement (pessimiste / correct / optimiste) pour les placements, en
 * fractions (0,017 pour 1,7 %). Ce sont des repères : rien n'est appliqué automatiquement.
 *
 * @param amplitude    écart (fraction) entre le taux « correct » et les taux pessimiste/optimiste
 * @param suggestions  un élément par placement
 * @param notes        limites générales de la méthode
 */
public record PlacementRateSuggestionsModel(
        BigDecimal amplitude,
        List<Item> suggestions,
        List<String> notes) {

    /** Nature de l'information proposée pour un placement. */
    public enum Kind {
        /** Trois taux proposés, applicables en un clic. */
        SUGGESTION,
        /** Simple taux de repère (aucun scénario proposé). */
        REFERENCE,
        /** Aucune source publique défendable pour ce placement. */
        NONE
    }

    /**
     * @param benchmark     référence utilisée (« Livret A », « Taux à 10 ans zone euro AAA »), null si aucune
     * @param suggestedPess taux pessimiste proposé (kind SUGGESTION uniquement)
     * @param suggestedCorr taux correct proposé (kind SUGGESTION uniquement)
     * @param suggestedOpti taux optimiste proposé (kind SUGGESTION uniquement)
     * @param referenceRate taux de repère en taux annuel effectif (kind REFERENCE uniquement)
     * @param currentPess   taux pessimiste actuellement saisi
     * @param currentCorr   taux correct actuellement saisi
     * @param currentOpti   taux optimiste actuellement saisi
     * @param basis         explication de la source de la valeur, prête à afficher
     * @param caveat        réserve à afficher avec la valeur (donnée ancienne, convention...), null sinon
     */
    public record Item(
            String placementId,
            String label,
            String bucket,
            Kind kind,
            String benchmark,
            BigDecimal suggestedPess,
            BigDecimal suggestedCorr,
            BigDecimal suggestedOpti,
            BigDecimal referenceRate,
            BigDecimal currentPess,
            BigDecimal currentCorr,
            BigDecimal currentOpti,
            String basis,
            String caveat) {
    }
}
