package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.OverviewInput;
import com.moe.myfamilybudget.server.internal.calculation.PatrimoineProjection;
import com.moe.myfamilybudget.server.internal.calculation.RealEstateProjection;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.ChargeModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.domain.budget.TripleAmountModel;

/**
 * RF-900 : vérifie la création d'{@link OverviewInput} par {@link OverviewInputFactory}
 * à partir de {@link BudgetDataModel}.
 */
class OverviewInputFactoryTest {

    private final OverviewInputFactory factory = new OverviewInputFactory();

    private static SettingsModel settings() {
        return new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", new BigDecimal("5000"),
                21, BigDecimal.ZERO);
    }

    private static BudgetDataModel budget(
            SettingsModel settings,
            List<IncomeModel> incomes,
            List<ChargeModel> charges,
            List<PlacementModel> placements,
            List<RealEstateModel> realEstates) {
        return new BudgetDataModel(
                settings,
                incomes,
                charges,
                placements,
                realEstates,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("Assemblage complet d'OverviewInput à partir du budget")
    void fullOverviewInputAssembly() {
        IncomeModel income = new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2026-01-01", "2050-12-31",
                null, "", "");
        ChargeModel charge = new ChargeModel("c1", "Loyer", new BigDecimal("800"), "2026-01-01", "2050-12-31",
                null, "", "");
        PlacementModel p1 = new PlacementModel("p1", "Livret A", "cat1", new BigDecimal("10000"), "2026-01-01",
                new BigDecimal("200"), "2026-01-01", "2040-12-31", new BigDecimal("0.02"),
                new BigDecimal("0.02"), new BigDecimal("0.02"), false, "");
        PlacementModel p2 = new PlacementModel("p2", "PEA Bloqué", "cat2", new BigDecimal("5000"), "2026-01-01",
                BigDecimal.ZERO, null, null, new BigDecimal("0.05"),
                new BigDecimal("0.05"), new BigDecimal("0.05"), true, "");

        RealEstateModel re = new RealEstateModel("re1", "Résidence principale", "Maison",
                new BigDecimal("200000"), 2026, new BigDecimal("0.02"), "");

        BudgetDataModel data = budget(settings(), List.of(income), List.of(charge), List.of(p1, p2), List.of(re));

        OverviewInput input = factory.from(data, false, 2026);

        // 1. Projections présentes et non nulles
        assertThat(input.treasuryProjection()).isNotNull();
        assertThat(input.patrimoineProjection()).isNotNull();
        assertThat(input.retirementProjection()).isNotNull();
        assertThat(input.taxProjection()).isNotNull();
        assertThat(input.realEstateProjection()).isNotNull();
        assertThat(input.parameters()).isNotNull();

        // 2. Paramètres
        assertThat(input.parameters().retireYear()).isEqualTo(1985 + 64); // 2049
        assertThat(input.parameters().currentYear()).isEqualTo(2026);
        assertThat(input.parameters().pivotBalance()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(input.parameters().inflationRate()).isEqualByComparingTo(new BigDecimal("0.02"));
        assertThat(input.parameters().useConstantEuros()).isFalse();

        // 3. Trésorerie
        assertThat(input.treasuryProjection().cashflow()).isNotEmpty();
        assertThat(input.treasuryProjection().years()).contains(2026, 2049);

        // 4. Patrimoine
        PatrimoineProjection patProj = input.patrimoineProjection();
        assertThat(patProj.currentTotalBalance()).isEqualByComparingTo(new BigDecimal("15000")); // 10000 + 5000
        assertThat(patProj.excludedPlacementLabels()).containsExactly("PEA Bloqué");

        // 5. Immobilier : 200 000 projeté de 2026 à 2049 (23 ans) à 2 %
        RealEstateProjection reProj = input.realEstateProjection();
        assertThat(reProj.currentTotalValue()).isEqualByComparingTo(new BigDecimal("200000"));
        double expectedFactor = Math.pow(1.02, 23);
        BigDecimal expectedNominal = new BigDecimal("200000").multiply(BigDecimal.valueOf(expectedFactor));
        assertThat(reProj.nominalValueAtRetire()).isEqualByComparingTo(expectedNominal);
        assertThat(reProj.items()).hasSize(1);
        assertThat(reProj.items().get(0).label()).isEqualTo("Résidence principale");
    }

    @Test
    @DisplayName("PatrimoineProjection : financialOnlyPatrimoine filtre les placements exclus")
    void financialOnlyPatrimoineExcludesMarkedPlacements() {
        IncomeModel income = new IncomeModel("i1", "Salaire", new BigDecimal("3000"), "2026-01-01", "2050-12-31",
                null, "", "");
        PlacementModel p1 = new PlacementModel("p1", "Disponible", "cat1", new BigDecimal("10000"), "2026-01-01",
                BigDecimal.ZERO, null, null, new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), false, "");
        PlacementModel p2 = new PlacementModel("p2", "Exclu", "cat2", new BigDecimal("20000"), "2026-01-01",
                BigDecimal.ZERO, null, null, new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), true, "");

        BudgetDataModel data = budget(settings(), List.of(income), List.of(), List.of(p1, p2), List.of());
        OverviewInput input = factory.from(data, false, 2026);

        PatrimoineProjection pat = input.patrimoineProjection();
        TripleAmountModel financialOnly = pat.financialOnlyPatrimoine(0, BigDecimal.ONE);

        // Seul "Disponible" (10 000) doit être compté, pas "Exclu" (20 000)
        assertThat(financialOnly.corr()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(financialOnly.pess()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(financialOnly.opti()).isEqualByComparingTo(new BigDecimal("10000"));
    }
}
