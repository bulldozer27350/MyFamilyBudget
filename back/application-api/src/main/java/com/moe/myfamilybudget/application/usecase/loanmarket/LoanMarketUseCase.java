package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Cas d'usage « Prêts et taux de marché » exposé par l'application (R-86, SILO-317).
 *
 * <p>Ce cas d'usage est transverse Patrimoine+Marché (décision du 14/10/2026) et rattaché à
 * {@code application} (pas de module dédié). Il couvre les trois endpoints de
 * {@code AnalysePretsApi} et l'endpoint de {@code SuggestionsTauxApi}.
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type d'un
 * silo ({@code domain.credit}, {@code domain.market}, {@code domain.wealth}). Le module {@code web}
 * (aujourd'hui {@code AnalysePretsServiceImpl} et {@code SuggestionsTauxServiceImpl}) convertit
 * les résultats vers le monde HTTP.
 */
public interface LoanMarketUseCase {

    /**
     * Analyse les prêts en cours : recommandation de remboursement anticipé et de renégociation.
     *
     * <p>Le taux de marché de la renégociation est choisi dans cet ordre : paramètre de requête
     * (simulation), taux saisi dans les hypothèses, puis taux moyen des nouveaux crédits de la
     * Banque de France.
     *
     * @param marketRate taux de marché de simulation, null pour utiliser la source automatique
     * @return résultat de l'analyse avec le taux retenu et son origine
     */
    LoanAdviceResultModel getAnalysePrets(BigDecimal marketRate);

    /**
     * Retourne les hypothèses actuelles et les valeurs par défaut de l'analyse des prêts.
     */
    LoanAdviceParametersModel getAnalysePretsParametres();

    /**
     * Valide et enregistre de nouvelles hypothèses.
     *
     * @param command nouvelles valeurs à enregistrer
     * @return les hypothèses telles qu'enregistrées avec les valeurs par défaut
     * @throws IllegalArgumentException si une valeur est absente ou hors plage (traduit en 400)
     */
    LoanAdviceParametersModel updateAnalysePretsParametres(LoanAdviceParametersSaveCommand command);

    /**
     * Calcule les suggestions de taux pessimiste / correct / optimiste pour les placements, à
     * partir de données publiques (Livret A, LDDS, LEP, courbe des taux BCE).
     *
     * @param amplitude écart (fraction) entre le taux « correct » et les taux pessimiste/optimiste,
     *                  null pour la valeur par défaut (1 %) ; doit être entre 0 % et 5 %
     * @throws IllegalArgumentException si l'amplitude est hors plage (traduit en 400)
     */
    RateSuggestionsModel getSuggestionsTaux(BigDecimal amplitude);
}
