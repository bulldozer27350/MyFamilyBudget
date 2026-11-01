package com.moe.myfamilybudget.domain.analysis.calculation;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Transaction bancaire telle que le silo Analyse la lit (SILO-133, voir doc/architecture/21-plan-silotage.md).
 *
 * <p>Contrat propre à Analyse : il ne référence aucun type du silo Banque/Pointage. L'application traduit
 * les transactions de l'import bancaire vers ce type et ne transporte que les champs utiles au calcul.
 *
 * @param id         identifiant de la transaction (clé des rapprochements, sans ventilation)
 * @param date       date au format ISO {@code YYYY-MM-DD}
 * @param amount     montant signé (négatif pour un débit)
 * @param categoryId catégorie de la transaction, chaîne vide si aucune
 * @param splits     ventilations éventuelles du montant par catégorie
 */
public record AnalysisTransaction(
        String id,
        String date,
        BigDecimal amount,
        String categoryId,
        List<Split> splits) {

    public AnalysisTransaction {
        if (id == null) id = "";
        if (date == null) date = "";
        if (amount == null) amount = BigDecimal.ZERO;
        if (categoryId == null) categoryId = "";
        if (splits == null) splits = Collections.emptyList();
    }

    /**
     * Ventilation d'une transaction (référencée par les rapprochements sous la forme {@code txId#splitId}).
     *
     * @param id         identifiant de la ventilation
     * @param categoryId catégorie de la ventilation, chaîne vide si aucune
     * @param amount     montant signé de la ventilation
     */
    public record Split(String id, String categoryId, BigDecimal amount) {

        public Split {
            if (id == null) id = "";
            if (categoryId == null) categoryId = "";
            if (amount == null) amount = BigDecimal.ZERO;
        }
    }
}
