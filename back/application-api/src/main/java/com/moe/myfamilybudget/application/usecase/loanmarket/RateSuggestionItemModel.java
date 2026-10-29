package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Suggestion de taux pour un placement (R-86, SILO-317).
 *
 * <p>Miroir applicatif de
 * {@code com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel.Item}.
 *
 * @param placementId   identifiant du placement
 * @param label         libellé du placement
 * @param bucket        classe d'actif résolue
 * @param kind          nature de l'information ({@link RateSuggestionKind})
 * @param benchmark     référence utilisée (« Livret A », « Taux à 10 ans zone euro AAA »), null si aucune
 * @param suggestedPess taux pessimiste proposé (kind SUGGESTION uniquement)
 * @param suggestedCorr taux correct proposé (kind SUGGESTION uniquement)
 * @param suggestedOpti taux optimiste proposé (kind SUGGESTION uniquement)
 * @param referenceRate taux de repère en taux annuel effectif (kind REFERENCE uniquement)
 * @param currentPess   taux pessimiste actuellement saisi
 * @param currentCorr   taux correct actuellement saisi
 * @param currentOpti   taux optimiste actuellement saisi
 * @param basis         explication de la source de la valeur, prête à afficher
 * @param caveat        réserve à afficher avec la valeur (donnée ancienne, convention…), null sinon
 */
public record RateSuggestionItemModel(
        String placementId,
        String label,
        String bucket,
        RateSuggestionKind kind,
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
