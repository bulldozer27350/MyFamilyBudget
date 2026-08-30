package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.CashflowYearDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel.RetirementPersonModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel.SalaryHistoryModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

class OverviewServiceImplTest {

    private OverviewServiceImpl overviewService;
    private OverviewMapper mapper;
    private PersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        mapper = new OverviewMapper();
        persistenceManager = new PersistenceManager();
        overviewService = new OverviewServiceImpl(mapper, persistenceManager);
    }


    @Test
    @DisplayName("Should calculate financial projections and retirement metrics accurately")
    void testBuildOverview_NominalCase() {
        // Given
        SettingsModel settings = new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal("10000"), 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015"));

        IncomeModel income1 = new IncomeModel("inc_1", "Salaire 1", new BigDecimal("3500"), "2026-01-01", "2048-12-31",
                new BigDecimal("0.01"), "cat_1", null);

        ChargeModel charge1 = new ChargeModel("chg_1", "Loyer", new BigDecimal("1200"), "2026-01-01", "2048-12-31",
                new BigDecimal("0.02"), "cat_2", null);

        PlacementModel placement1 = new PlacementModel("plc_1", "PEA", "Actions", new BigDecimal("20000"), "2026-01-01",
                new BigDecimal("300"), "2026-01-01", "2048-12-31", new BigDecimal("0.03"), new BigDecimal("0.05"),
                new BigDecimal("0.07"), false, null);

        RealEstateModel realEstate1 = new RealEstateModel("re_1", "RP Paris", "Résidence Principale",
                new BigDecimal("350000"), 2026, new BigDecimal("0.02"), null);

        SalaryHistoryModel salary2023 = new SalaryHistoryModel(2023, new BigDecimal("42000"));
        SalaryHistoryModel salary2024 = new SalaryHistoryModel(2024, new BigDecimal("44000"));
        SalaryHistoryModel salary2025 = new SalaryHistoryModel(2025, new BigDecimal("46000"));

        RetirementPersonModel person1 = new RetirementPersonModel("p_1", "Moe", 1985, "Salaire 1", 120, "2025-12-31",
                List.of(salary2023, salary2024, salary2025), new BigDecimal("3500"), new BigDecimal("0.0051"));

        RetirementModel retirement = new RetirementModel(List.of(person1), new BigDecimal("47100"),
                new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01"));

        BudgetDataModel budgetData = new BudgetDataModel(settings, List.of(income1), List.of(charge1),
                List.of(placement1), List.of(realEstate1), retirement, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null);

        this.persistenceManager.setBudgetData(budgetData); // Save to persistence for retrieval in service

        // When
        OverviewResponseDto response = this.overviewService.getOverview(false).getBody();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRetireYear()).isEqualTo(2049); // 1985 + 64
        assertThat(response.getYears()).contains(2026, 2049, 2070); // Birth + simulateUntilAge 85 -> 2070
        assertThat(response.getPatrimoineActuel()).isEqualByComparingTo("20000");

        // Verify Cashflow Year 2026
        CashflowYearDto cf2026 = response.getCashflow().get(0);
        assertThat(cf2026.getYear()).isEqualTo(2026);
        assertThat(cf2026.getIncome()).isEqualByComparingTo("42000"); // 3500 * 12
        assertThat(cf2026.getCharges()).isEqualByComparingTo("14400"); // 1200 * 12
        assertThat(cf2026.getSavings()).isEqualByComparingTo("3600"); // 300 * 12

        // Verify Pensions & Retirement Metrics
        assertThat(response.getTotalPensions()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getRetirePatrimoine().getCorr()).isGreaterThan(response.getRetirePatrimoine().getPess());
        assertThat(response.getRetirePatrimoine().getOpti()).isGreaterThan(response.getRetirePatrimoine().getCorr());
        assertThat(response.getFireRente().getCorr()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should apply deflator correctly in constant euros mode")
    void testBuildOverview_ConstantEuros() {
        // Given
        SettingsModel settings = new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal("10000"), 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015"));

        RealEstateModel realEstate1 = new RealEstateModel("re_1", "Appartement", "Investissement",
                new BigDecimal("200000"), 2026, new BigDecimal("0.02"), null);

        BudgetDataModel budgetData = new BudgetDataModel(settings, List.of(), List.of(), List.of(),
                List.of(realEstate1), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), null);

        this.persistenceManager.setBudgetData(budgetData); // Save to persistence for retrieval in service
        // When
        OverviewResponseDto currentEurosResponse = this.overviewService.getOverview(false).getBody();
        OverviewResponseDto constantEurosResponse = this.overviewService.getOverview(true).getBody();

        // Then
        assertThat(constantEurosResponse.getRetirePatrimoine().getPess())
                .isLessThan(currentEurosResponse.getRetirePatrimoine().getPess());
    }

    @Test
    @DisplayName("Should calculate retirement projection trimestres and pensions correctly")
    void testRetirementProjection() {
        // Given
        SettingsModel settings = new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015"));

        com.moe.myfamilybudget.server.internal.model.IncomeModel income = new com.moe.myfamilybudget.server.internal.model.IncomeModel(
                "inc_1", "Salaire Moe", new BigDecimal("4000"), "2026-01-01", "2048-12-31", new BigDecimal("0.01"),
                "cat_1", null);

        RetirementModel.RetirementPersonModel person = new RetirementModel.RetirementPersonModel("p_1", "Moe", 1985,
                "Salaire Moe", 100, "2025-12-31",
                List.of(new RetirementModel.SalaryHistoryModel(2025, new BigDecimal("48000"))), new BigDecimal("2000"),
                new BigDecimal("0.0051"));

        BudgetDataModel budgetData = new BudgetDataModel(settings, List.of(income), List.of(), List.of(), List.of(),
                new RetirementModel(List.of(person), new BigDecimal("47100"), new BigDecimal("0.015"),
                        new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);

        // When
        OverviewServiceImpl.RetirementProjection projection = overviewService.computeRetirementProjection(budgetData,
                person, 2049);

        // Then
        assertThat(projection.ageDepart()).isEqualTo(64);
        assertThat(projection.trimestresValides()).isEqualTo(100);
        // Future trimestres: 2026 to 2049 inclusive = 24 years * 4 = 96 trimestres.
        // Total = 196 > 172
        assertThat(projection.trimestresEstimesDepart()).isEqualTo(192);
        assertThat(projection.tauxApplique()).isGreaterThan(new BigDecimal("0.50")); // Surcote applied
        assertThat(projection.pensionTotaleMensuelle()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should include loans (passif) in the data returned by /overview")
    void testBuildOverview_LoansAreReturnedInDataDto() {
        // Given
        SettingsModel settings = new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015"));

        LoanModel loan1 = new LoanModel("loan_1", "Pret RP", new BigDecimal("180000"), new BigDecimal("0.0080"),
                new BigDecimal("950"), new BigDecimal("15"), "2020-01-01", "2045-01-01");

        BudgetDataModel budgetData = new BudgetDataModel(settings, List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(loan1));

        this.persistenceManager.setBudgetData(budgetData); // Save to persistence for retrieval in service

        // When
        OverviewResponseDto response = this.overviewService.getOverview(false).getBody();

        // Then : le passif doit être visible dans data.loans (utilisé par LoansSummaryCard
        // et par l'export PDF du bilan patrimonial dans patrimoine-report.js)
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getLoans()).isNotNull();
        assertThat(response.getData().getLoans()).hasSize(1);
        assertThat(response.getData().getLoans().get(0).getCrd()).isEqualByComparingTo("180000");
    }
}
