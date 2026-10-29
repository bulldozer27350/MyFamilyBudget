package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat de l'analyse des prêts (rembourser ? renégocier ?) exposé par l'application (R-86,
 * miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel}, SILO-317).
 *
 * <p>Contrat applicatif : aucune dépendance vers {@code domain.credit}, ni vers Spring, JPA ou
 * OpenAPI. Le module {@code web} (aujourd'hui {@code AnalysePretsServiceImpl}) convertit le
 * résultat vers les DTO HTTP.
 *
 * @param marketRateUsed   taux de marché retenu pour l'analyse de renégociation, null si non renseigné
 * @param marketRateSource origine de ce taux (requête, saisie manuelle, Banque de France), null si non renseigné
 * @param assumptions      hypothèses appliquées, à afficher à côté des résultats
 * @param loans            un élément par prêt en cours (capital restant dû positif)
 * @param notes            limites générales de l'estimation
 */
public record LoanAdviceResultModel(
        BigDecimal marketRateUsed,
        String marketRateSource,
        LoanAssumptionsModel assumptions,
        List<LoanItemModel> loans,
        List<String> notes) {

    /** Même résultat avec l'origine du taux de marché renseignée (connue de l'appelant, pas du calcul). */
    public LoanAdviceResultModel withMarketRateSource(String source) {
        return new LoanAdviceResultModel(marketRateUsed, source, assumptions, loans, notes);
    }
}
