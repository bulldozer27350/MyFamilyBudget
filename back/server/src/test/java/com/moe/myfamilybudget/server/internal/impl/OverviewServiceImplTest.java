package com.moe.myfamilybudget.server.internal.impl;

import com.moe.myfamilybudget.api.model.*;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OverviewServiceImplTest {

    private OverviewServiceImpl overviewService;
    private OverviewMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OverviewMapper();
        overviewService = new OverviewServiceImpl(mapper);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when parameters is null")
    void testBuildOverview_NullData() {
        assertThatThrownBy(() -> overviewService.buildOverview(null, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("BudgetDataDto cannot be null");
    }

    @Test
    @DisplayName("Should calculate financial projections and retirement metrics accurately")
    void testBuildOverview_NominalCase() {
        // Given
        SettingsDto settings = new SettingsDto(
            1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", new BigDecimal("10000"), 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );

        IncomeDto income1 = new IncomeDto("inc_1", "Salaire 1", new BigDecimal("3500"), "2026-01-01", "2048-12-31", new BigDecimal("0.01"), "cat_1", null);
        ChargeDto charge1 = new ChargeDto("chg_1", "Loyer", new BigDecimal("1200"), "2026-01-01", "2048-12-31", new BigDecimal("0.02"), "cat_2", null);

        PlacementDto placement1 = new PlacementDto(
            "plc_1", "PEA", "Actions", new BigDecimal("20000"), "2026-01-01",
            new BigDecimal("300"), "2026-01-01", "2048-12-31",
            new BigDecimal("0.03"), new BigDecimal("0.05"), new BigDecimal("0.07"),
            false, null
        );

        RealEstateDto realEstate1 = new RealEstateDto(
            "re_1", "RP Paris", "Résidence Principale", new BigDecimal("350000"), 2026, new BigDecimal("0.02"), null
        );

        RetirementDto.RetirementPersonDto person1 = new RetirementDto.RetirementPersonDto(
            "p_1", "Moe", 1985, "Salaire 1", 120, "2025-12-31",
            List.of(
                new RetirementDto.SalaryHistoryDto(2023, new BigDecimal("42000")),
                new RetirementDto.SalaryHistoryDto(2024, new BigDecimal("44000")),
                new RetirementDto.SalaryHistoryDto(2025, new BigDecimal("46000"))
            ),
            new BigDecimal("3500"), new BigDecimal("0.0051")
        );

        RetirementDto retirement = new RetirementDto(
            List.of(person1), new BigDecimal("47100"), new BigDecimal("0.015"),
            new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01")
        );

        BudgetDataDto budgetData = new BudgetDataDto(
            settings, List.of(income1), List.of(charge1), List.of(placement1),
            List.of(realEstate1), retirement, List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), null
        );

        // When
        OverviewResponseDto response = overviewService.buildOverview(budgetData, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.retireYear()).isEqualTo(2049); // 1985 + 64
        assertThat(response.years()).contains(2026, 2049, 2070); // Birth + simulateUntilAge 85 -> 2070
        assertThat(response.patrimoineActuel()).isEqualByComparingTo("20000");

        // Verify Cashflow Year 2026
        CashflowYearDto cf2026 = response.cashflow().get(0);
        assertThat(cf2026.year()).isEqualTo(2026);
        assertThat(cf2026.income()).isEqualByComparingTo("42000"); // 3500 * 12
        assertThat(cf2026.charges()).isEqualByComparingTo("14400"); // 1200 * 12
        assertThat(cf2026.savings()).isEqualByComparingTo("3600"); // 300 * 12

        // Verify Pensions & Retirement Metrics
        assertThat(response.totalPensions()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.retirePatrimoine().corr()).isGreaterThan(response.retirePatrimoine().pess());
        assertThat(response.retirePatrimoine().opti()).isGreaterThan(response.retirePatrimoine().corr());
        assertThat(response.fireRente().corr()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should apply deflator correctly in constant euros mode")
    void testBuildOverview_ConstantEuros() {
        // Given
        SettingsDto settings = new SettingsDto(
            1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", new BigDecimal("10000"), 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );

        RealEstateDto realEstate1 = new RealEstateDto(
            "re_1", "Appartement", "Investissement", new BigDecimal("200000"), 2026, new BigDecimal("0.02"), null
        );

        BudgetDataDto budgetData = new BudgetDataDto(
            settings, List.of(), List.of(), List.of(), List.of(realEstate1),
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null
        );

        // When
        OverviewResponseDto currentEurosResponse = overviewService.buildOverview(budgetData, false);
        OverviewResponseDto constantEurosResponse = overviewService.buildOverview(budgetData, true);

        // Then
        assertThat(constantEurosResponse.retirePatrimoine().pess())
            .isLessThan(currentEurosResponse.retirePatrimoine().pess());
    }

    @Test
    @DisplayName("Should calculate retirement projection trimestres and pensions correctly")
    void testRetirementProjection() {
        // Given
        SettingsModel settings = new SettingsModel(
            1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual", BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015")
        );

        com.moe.myfamilybudget.server.internal.model.IncomeModel income = new com.moe.myfamilybudget.server.internal.model.IncomeModel("inc_1", "Salaire Moe", new BigDecimal("4000"), "2026-01-01", "2048-12-31", new BigDecimal("0.01"), "cat_1", null);

        RetirementModel.RetirementPersonModel person = new RetirementModel.RetirementPersonModel(
            "p_1", "Moe", 1985, "Salaire Moe", 100, "2025-12-31",
            List.of(new RetirementModel.SalaryHistoryModel(2025, new BigDecimal("48000"))),
            new BigDecimal("2000"), new BigDecimal("0.0051")
        );

        BudgetDataModel budgetData = new BudgetDataModel(
            settings, List.of(income), List.of(), List.of(), List.of(),
            new RetirementModel(List.of(person), new BigDecimal("47100"), new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01")),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null
        );

        // When
        OverviewServiceImpl.RetirementProjection projection = overviewService.computeRetirementProjection(budgetData, person, 2049);

        // Then
        assertThat(projection.ageDepart()).isEqualTo(64);
        assertThat(projection.trimestresValides()).isEqualTo(100);
        // Future trimestres: 2026 to 2049 inclusive = 24 years * 4 = 96 trimestres. Total = 196 > 172
        assertThat(projection.trimestresEstimesDepart()).isEqualTo(196);
        assertThat(projection.tauxApplique()).isGreaterThan(new BigDecimal("0.50")); // Surcote applied
        assertThat(projection.pensionTotaleMensuelle()).isGreaterThan(BigDecimal.ZERO);
    }
}
