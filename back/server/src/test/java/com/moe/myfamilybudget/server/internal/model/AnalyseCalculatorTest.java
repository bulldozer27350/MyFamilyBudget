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
                "tx1", "2026-08-05", "Loyer août", "cb", new BigDecimal("-1000"), "cat1"
        );

        MatchingLinkModel link1 = new MatchingLinkModel("c1", List.of("tx1"));
        MatchingModel matching = new MatchingModel("2026-08", List.of(link1));

        BankImportModel bankImport = new BankImportModel(
                null,
                List.of(cat1, cat2),
                List.of(),
                List.of(tx1),
                List.of(),
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

    @Test
    @DisplayName("computeAnalyse doit ventiler correctement les dépenses par catégorie pour les transactions ventilées (splits)")
    void testComputeAnalyseWithSplits() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", BigDecimal.ZERO, 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );
        ChargeModel chargeFood = new ChargeModel("c_food", "Alimentation", new BigDecimal("400"), "2026-01-01", "2030-12-31", BigDecimal.ZERO, "cat_food", "");
        ChargeModel chargeClothes = new ChargeModel("c_clothes", "Vêtements", new BigDecimal("100"), "2026-01-01", "2030-12-31", BigDecimal.ZERO, "cat_clothes", "");

        BudgetDataModel data = new BudgetDataModel(
                settings,
                List.of(),
                List.of(chargeFood, chargeClothes),
                List.of(),
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

        CategoryModel catFood = new CategoryModel("cat_food", "Alimentation", "Dépense", "Non");
        CategoryModel catClothes = new CategoryModel("cat_clothes", "Habillement", "Dépense", "Oui"); // compressible

        // 1 transaction of 100€ split: 60€ cat_food, 40€ cat_clothes
        BankImportModel.BankTransactionSplitModel s1 = new BankImportModel.BankTransactionSplitModel("s1", "cat_food", new BigDecimal("-60.00"), "Courses");
        BankImportModel.BankTransactionSplitModel s2 = new BankImportModel.BankTransactionSplitModel("s2", "cat_clothes", new BigDecimal("-40.00"), "Pantalon");
        BankTransactionModel txSplit = new BankTransactionModel(
                "tx_split", "2026-08-10", "HYPERMARCHE", "cb", new BigDecimal("-100.00"), "cat_default", List.of(s1, s2)
        );

        MatchingLinkModel linkFood = new MatchingLinkModel("c_food", List.of("tx_split#s1"));
        MatchingLinkModel linkClothes = new MatchingLinkModel("c_clothes", List.of("tx_split#s2"));
        MatchingModel matching = new MatchingModel("2026-08", List.of(linkFood, linkClothes));

        BankImportModel bankImport = new BankImportModel(
                null,
                List.of(catFood, catClothes),
                List.of(),
                List.of(txSplit),
                List.of(),
                List.of(matching)
        );

        AnalyseResultModel result = AnalyseCalculator.computeAnalyse(data, bankImport, 12);

        assertThat(result).isNotNull();
        // Check total expenses and compressible
        assertThat(result.kpis().totalExpenses()).isEqualByComparingTo("100.00");
        assertThat(result.kpis().compressibleTotal()).isEqualByComparingTo("40.00"); // only clothes is compressible

        // Check category summaries
        assertThat(result.categorySummaries()).hasSize(2);
        AnalyseCategorySummaryModel summaryFood = result.categorySummaries().stream()
                .filter(c -> "Alimentation".equals(c.label())).findFirst().orElseThrow();
        AnalyseCategorySummaryModel summaryClothes = result.categorySummaries().stream()
                .filter(c -> "Habillement".equals(c.label())).findFirst().orElseThrow();
        assertThat(summaryFood.amount()).isEqualByComparingTo("60.00");
        assertThat(summaryClothes.amount()).isEqualByComparingTo("40.00");

        // Check landing data
        AnalyseLandingRowModel landingFood = result.landingData().stream()
                .filter(l -> "c_food".equals(l.id())).findFirst().orElseThrow();
        assertThat(landingFood.reel()).isEqualByComparingTo("60.00");

        AnalyseLandingRowModel landingClothes = result.landingData().stream()
                .filter(l -> "c_clothes".equals(l.id())).findFirst().orElseThrow();
        assertThat(landingClothes.reel()).isEqualByComparingTo("40.00");
    }
}
