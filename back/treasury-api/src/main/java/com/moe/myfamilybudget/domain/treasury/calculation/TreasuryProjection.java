package com.moe.myfamilybudget.domain.treasury.calculation;

import java.util.List;

import com.moe.myfamilybudget.domain.treasury.model.CashflowYearModel;
import com.moe.myfamilybudget.domain.treasury.model.VariablePreviewModel;

/**
 * Projection de flux de trésorerie (RF-401, voir doc/architecture/06-domaine-tresorerie.md et
 * doc/architecture/11-domaine-overview.md).
 *
 * <p>Produite par {@link TresorerieCalculationService#compute(TreasuryProjectionInput)} à partir
 * de {@link TreasuryProjectionInput}, sans dépendance à {@code BudgetDataModel}.
 *
 * @param years           années couvertes par la projection
 * @param cashflow        flux de trésorerie annuel détaillé
 * @param variablePreview aperçu des revenus variables sur les premières années
 * @param previewYears    années couvertes par l'aperçu des revenus variables
 */
public record TreasuryProjection(
        List<Integer> years,
        List<CashflowYearModel> cashflow,
        List<VariablePreviewModel> variablePreview,
        List<Integer> previewYears) {

    public TreasuryProjection {
        years = years != null ? List.copyOf(years) : List.of();
        cashflow = cashflow != null ? List.copyOf(cashflow) : List.of();
        variablePreview = variablePreview != null ? List.copyOf(variablePreview) : List.of();
        previewYears = previewYears != null ? List.copyOf(previewYears) : List.of();
    }
}
