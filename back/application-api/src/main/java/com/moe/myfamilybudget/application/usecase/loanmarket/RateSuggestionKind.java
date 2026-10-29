package com.moe.myfamilybudget.application.usecase.loanmarket;

/**
 * Nature de l'information proposée pour un placement dans les suggestions de taux (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel.Kind}.
 */
public enum RateSuggestionKind {
    /** Trois taux proposés, applicables en un clic. */
    SUGGESTION,
    /** Simple taux de repère (aucun scénario proposé). */
    REFERENCE,
    /** Aucune source publique défendable pour ce placement. */
    NONE
}
