package com.moe.myfamilybudget.domain.analysis.calculation;

import java.util.Collections;
import java.util.List;

/**
 * Rapprochements d'un mois entre lignes budgétaires et transactions, tels que le silo Analyse les lit
 * (SILO-133) : aucun type du silo Banque/Pointage.
 *
 * @param month mois au format {@code YYYY-MM}
 * @param links liens de rapprochement du mois
 */
public record AnalysisMatching(String month, List<Link> links) {

    public AnalysisMatching {
        if (month == null) month = "";
        if (links == null) links = Collections.emptyList();
    }

    /**
     * Lien entre une ligne budgétaire et des transactions.
     *
     * @param budgetLineId identifiant de la ligne budgétaire
     * @param txIds        identifiants de transactions ({@code txId} ou {@code txId#splitId})
     */
    public record Link(String budgetLineId, List<String> txIds) {

        public Link {
            if (budgetLineId == null) budgetLineId = "";
            if (txIds == null) txIds = Collections.emptyList();
        }
    }
}
