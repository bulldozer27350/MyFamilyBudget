package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.CategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.MatchingLinkModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.MatchingModel;

class AnalyseCalculatorTest {

    @Test
    @DisplayName("computeAnalyse doit calculer correctement les KPIs, catégories et lignes d'atterrissage sur le modèle interne")
    void testComputeAnalyse() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", BigDecimal.ZERO, 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );
        ChargeModel charge = new ChargeModel("c1", "Loyer", new BigDecimal("1000"), "2026-01-01", "2030-12-31", new BigDecimal("0.01"), "cat1", "");
        IncomeModel income = new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2026-01-01", "2030-12-31", BigDecimal.ZERO, "cat2", "");
        PlacementModel placement = new PlacementModel("p1", "Livret A", "Épargne", new BigDecimal("5000"), "2026-01-01", new BigDecimal("200"), "2026-01-01", "2030-12-31", new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"), false, "");

        BudgetDataModel data = new BudgetDataModel(
                settings,
                List.of(income),
                List.of(charge),
                List.of(placement),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        CategoryModel cat1 = new CategoryModel("cat1", "Logement", "Dépense", "Oui");
        CategoryModel cat2 = new CategoryModel("cat2", "Salaire", "Revenu", "Non");

        BankTransactionModel tx1 = new BankTransactionModel(
                "tx1", "2026-08-05", "Loyer août", new BigDecimal("-1000"), "cat1", "done", null
        );

        MatchingLinkModel link1 = new MatchingLinkModel("c1", List.of("tx1"));
        MatchingModel matching = new MatchingModel("2026-08", List.of(link1));

        BankImportModel bankImport = new BankImportModel(
                null,
                List.of(cat1, cat2),
                List.of(),
                List.of(tx1),
                List.of(matching)
        );

        AnalyseResultModel result = AnalyseCalculator.computeAnalyse(data, bankImport, 12);

        assertThat(result).isNotNull();
        assertThat(result.kpis()).isNotNull();
        assertThat(result.landingData()).isNotEmpty();
        assertThat(result.categorySummaries()).isNotEmpty();
        assertThat(result.monthlyCompareData()).isNotEmpty();
        assertThat(result.currentMonthISO()).isNotNull();
    }
}
