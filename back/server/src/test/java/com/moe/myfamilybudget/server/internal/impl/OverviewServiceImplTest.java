package com.moe.myfamilybudget.server.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.CashflowYearDto;
import com.moe.myfamilybudget.api.model.ChargeDto;
import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.RetirementDto;
import com.moe.myfamilybudget.api.model.RetirementPersonDto;
import com.moe.myfamilybudget.api.model.SalaryHistoryDto;
import com.moe.myfamilybudget.api.model.SettingsDto;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

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
                .isInstanceOf(IllegalArgumentException.class).hasMessage("BudgetDataDto cannot be null");
    }

    @Test
    @DisplayName("Should calculate financial projections and retirement metrics accurately")
    void testBuildOverview_NominalCase() {
        // Given
        SettingsDto settings = new SettingsDto();
        settings.setBirthYear(1985);
        settings.setRetireAge(64);
        settings.setSimulateUntilAge(85);
        settings.setInflationRate(new BigDecimal("0.02"));
        settings.setPivotDate("2026-01-01");
        settings.setPivotMode("manual");
        settings.setStartBalance(new BigDecimal("10000"));
        settings.setChildExitAge(21);
        settings.setTaxAbattement(new BigDecimal("0.10"));
        settings.setPass2026(new BigDecimal("47100"));
        settings.setPassGrowthRate(new BigDecimal("0.015"));

        IncomeDto income1 = new IncomeDto();
        income1.setId("inc_1");
        income1.setLabel("Salaire 1");
        income1.setMonthly(new BigDecimal("3500"));
        income1.setStart("2026-01-01");
        income1.setEnd("2048-12-31");
        income1.setGrowthRate(new BigDecimal("0.01"));
        income1.setCategoryId("cat_1");

        ChargeDto charge1 = new ChargeDto();
        charge1.setId("chg_1");
        charge1.setLabel("Loyer");
        charge1.setMonthly(new BigDecimal("1200"));
        charge1.setStart("2026-01-01");
        charge1.setEnd("2048-12-31");
        charge1.setGrowthRate(new BigDecimal("0.02"));
        charge1.setCategoryId("cat_2");

        PlacementDto placement1 = new PlacementDto();
        placement1.setId("plc_1");
        placement1.setLabel("PEA");
        placement1.setCategory("Actions");
        placement1.setBalance(new BigDecimal("20000"));
        placement1.setMonthlyFrom("2026-01-01");
        placement1.setMonthly(new BigDecimal("300"));
        placement1.setBalanceDate("2026-01-01");
        placement1.setMonthlyUntil("2048-12-31");
        placement1.setRatePess(new BigDecimal("0.03"));
        placement1.setRateCorr(new BigDecimal("0.05"));
        placement1.setRateOpti(new BigDecimal("0.07"));
        placement1.setExcludedFromRetirement(false);

        RealEstateDto realEstate1 = new RealEstateDto();
        realEstate1.setId("re_1");
        realEstate1.setLabel("RP Paris");
        realEstate1.setType("Résidence Principale");
        realEstate1.setCurrentValue(new BigDecimal("350000"));
        realEstate1.setValuationYear(2026);
        realEstate1.setAnnualGrowthRate(new BigDecimal("0.02"));

        SalaryHistoryDto salary2023 = new SalaryHistoryDto();
        salary2023.setYear(2023);
        salary2023.setSalary(new BigDecimal("42000"));
        SalaryHistoryDto salary2024 = new SalaryHistoryDto();
        salary2024.setYear(2024);
        salary2024.setSalary(new BigDecimal("44000"));
        SalaryHistoryDto salary2025 = new SalaryHistoryDto();
        salary2025.setYear(2025);
        salary2025.setSalary(new BigDecimal("46000"));

        RetirementPersonDto person1 = new RetirementPersonDto();
        person1.setId("p_1");
        person1.setName("Moe");
        person1.setBirthYear(1985);
        person1.setIncomeLabel("Salaire 1");
        person1.setTrimestresValides(120);
        person1.setTrimestresDate("2025-12-31");
        person1.setSalaryHistory(List.of(salary2023, salary2024, salary2025));
        person1.setAgircPoints(new BigDecimal("3500"));
        person1.setRatioPointsParEuro(new BigDecimal("0.0051"));

        RetirementDto retirement = new RetirementDto();
        retirement.setPeople(List.of(person1));
        retirement.setPass2026(new BigDecimal("47100"));
        retirement.setPassGrowthRate(new BigDecimal("0.015"));
        retirement.setAgircPointValue(new BigDecimal("1.4386"));
        retirement.setAgircPointDateGlobal("2025-01-01");
        retirement.setAgircPointGrowthRate(new BigDecimal("0.01"));

        BudgetDataDto budgetData = new BudgetDataDto();
        budgetData.setSettings(settings);
        budgetData.setIncomes(List.of(income1));
        budgetData.setCharges(List.of(charge1));
        budgetData.setPlacements(List.of(placement1));
        budgetData.setRealEstate(List.of(realEstate1));
        budgetData.setRetirement(retirement);

        // When
        OverviewResponseDto response = overviewService.buildOverview(budgetData, false).getBody();

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
        SettingsDto settings = new SettingsDto();
        settings.setBirthYear(1985);
        settings.setRetireAge(64);
        settings.setSimulateUntilAge(85);
        settings.setInflationRate(new BigDecimal("0.02"));
        settings.setPivotDate("2026-01-01");
        settings.setPivotMode("manual");
        settings.setStartBalance(new BigDecimal("10000"));
        settings.setChildExitAge(21);
        settings.setTaxAbattement(new BigDecimal("0.10"));
        settings.setPass2026(new BigDecimal("47100"));
        settings.setPassGrowthRate(new BigDecimal("0.015"));

        RealEstateDto realEstate1 = new RealEstateDto();
        realEstate1.setId("re_1");
        realEstate1.setLabel("Appartement");
        realEstate1.setType("Investissement");
        realEstate1.setCurrentValue(new BigDecimal("200000"));
        realEstate1.setValuationYear(2026);
        realEstate1.setAnnualGrowthRate(new BigDecimal("0.02"));

        BudgetDataDto budgetData = new BudgetDataDto();
        budgetData.setSettings(settings);
        budgetData.setIncomes(List.of());
        budgetData.setCharges(List.of());
        budgetData.setPlacements(List.of());
        budgetData.setRealEstate(List.of(realEstate1));
        budgetData.setRetirement(null);
        
        
        
//        settings, List.of(), List.of(), List.of(), List.of(realEstate1),
//                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null);

        // When
        OverviewResponseDto currentEurosResponse = overviewService.buildOverview(budgetData, false).getBody();
        OverviewResponseDto constantEurosResponse = overviewService.buildOverview(budgetData, true).getBody();

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
}
