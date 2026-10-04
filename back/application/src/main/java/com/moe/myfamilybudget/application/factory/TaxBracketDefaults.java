package com.moe.myfamilybudget.application.factory;

import java.math.BigDecimal;
import java.util.List;

import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;

/**
 * Barème fiscal par défaut, appliqué quand aucune tranche n'est configurée (SILO-111).
 *
 * <p>Reprend à l'identique la règle de {@code BudgetDataModel.getEffectiveTaxBrackets()}, pour que les
 * services lisant la Fiscalité par ses ports n'aient plus besoin du modèle global.
 */
public final class TaxBracketDefaults {

    private TaxBracketDefaults() {
    }

    public static List<TaxBracketModel> orDefault(List<TaxBracketModel> configured) {
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return List.of(
                new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                new TaxBracketModel("tb_5", null, new BigDecimal("0.45")));
    }
}
