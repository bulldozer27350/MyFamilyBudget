package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Suggestions de taux de rendement (pessimiste / correct / optimiste) pour les placements exposées
 * par l'application (R-86, miroir applicatif de
 * {@code com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel}, SILO-317).
 *
 * <p>Ce type vit dans {@code application-api} ; l'implémentation ({@code DefaultLoanMarketUseCase})
 * traduit les résultats du service de calcul ({@code PlacementRateSuggestionService}) vers ce
 * modèle avant de les remonter au module {@code web}.
 *
 * @param amplitude   écart (fraction) entre le taux « correct » et les taux pessimiste/optimiste
 * @param suggestions un élément par placement
 * @param notes       limites générales de la méthode
 */
public record RateSuggestionsModel(
        BigDecimal amplitude,
        List<RateSuggestionItemModel> suggestions,
        List<String> notes) {
}
