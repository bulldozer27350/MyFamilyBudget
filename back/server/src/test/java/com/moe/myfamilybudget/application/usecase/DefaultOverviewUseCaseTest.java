package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.overview.OverviewCalculationService;
import com.moe.myfamilybudget.application.usecase.overview.OverviewResultModel;
import com.moe.myfamilybudget.application.usecase.overview.OverviewUseCase;
import com.moe.myfamilybudget.domain.retirement.core.DefaultRetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.RetirementPersonModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.SalaryHistoryModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.core.DefaultTaxCalculationService;
import com.moe.myfamilybudget.domain.treasury.core.DefaultTresorerieCalculationService;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.wealth.core.DefaultPatrimoineProjectionService;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.testsupport.CacheBackedAppSettings;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTaxStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryTreasuryStore;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryWealthStore;

/**
 * R-85 (même gabarit que {@code DefaultAnalysisUseCaseTest}, SILO-316) : le cas d'usage compose
 * l'overview sans {@code ResponseEntity} ni DTO, et traduit les types vers les modèles de
 * {@code application-api}.
 */
class DefaultOverviewUseCaseTest {

    private final InMemoryBankStore bankStore = new InMemoryBankStore();
    private InMemoryTreasuryStore treasuryStore;
    private InMemoryWealthStore wealthStore;
    private InMemoryRetirementStore retirementStore;
    private InMemoryTaxStore taxStore;
    private CacheBackedAppSettings appSettings;

    private OverviewUseCase useCase;

    @BeforeEach
    void setUp() {
        treasuryStore = new InMemoryTreasuryStore();
        wealthStore = new InMemoryWealthStore();
        retirementStore = new InMemoryRetirementStore();
        taxStore = new InMemoryTaxStore();
        appSettings = new CacheBackedAppSettings();

        PatrimoinePersistenceAdapter patrimoineAdapter = new PatrimoinePersistenceAdapter(
                wealthStore.getDelegate(), treasuryStore.getDelegate());

        useCase = new DefaultOverviewUseCase(
                new OverviewCalculationService(),
                new DefaultRetirementCalculationService(),
                new DefaultTaxCalculationService(),
                new DefaultPatrimoineProjectionService(),
                new DefaultTresorerieCalculationService(),
                retirementStore,
                taxStore,
                treasuryStore,
                appSettings,
                appSettings,
                treasuryStore,
                patrimoineAdapter,
                retirementStore,
                taxStore,
                bankStore);
    }

    private void seedBudget(TresorerieSettingsModel tresorerieSettings, List<IncomeModel> incomes,
            List<ChargeModel> charges, List<PlacementModel> placements, List<RealEstateModel> realEstate,
            Integer birthYear, Integer retireAge, RetirementModel retirement) {
        this.treasuryStore.replace(tresorerieSettings, incomes, charges, List.of(), List.of(), List.of(), List.of());
        this.wealthStore.replace(placements, realEstate, List.of(), List.of());
        this.retirementStore.replace(new RetirementSettingsModel(birthYear, retireAge), retirement);
    }

    @Test
    @DisplayName("getOverview(false) renvoie un résultat complet nominal")
    void nominalCase() {
        TresorerieSettingsModel tresorerieSettings = new TresorerieSettingsModel(
                "2026-01-01", "manual", new BigDecimal("10000"), false, null, null, null);

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
                List.of(salary2023, salary2024, salary2025), new BigDecimal("3500"), new BigDecimal("0.0051"), false);

        RetirementModel retirement = new RetirementModel(List.of(person1), new BigDecimal("47100"),
                new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01"));

        seedBudget(tresorerieSettings, List.of(income1), List.of(charge1), List.of(placement1),
                List.of(realEstate1), 1985, 64, retirement);

        OverviewResultModel result = useCase.getOverview(false);

        assertThat(result).isNotNull();
        assertThat(result.retireYear()).isEqualTo(2049);
        assertThat(result.years()).contains(2026, 2049, 2070);
        assertThat(result.patrimoineActuel()).isEqualByComparingTo("20000");
        assertThat(result.cashflow()).isNotEmpty();
        assertThat(result.cashflow().get(0).income()).isEqualByComparingTo("42000");
        assertThat(result.totalPensions()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.retirePatrimoine()).isNotNull();
        assertThat(result.retirePatrimoine().corr()).isGreaterThan(result.retirePatrimoine().pess());
        assertThat(result.fireRente().corr()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getOverview(true) applique le déflateur en euros constants")
    void constantEuros() {
        TresorerieSettingsModel tresorerieSettings = new TresorerieSettingsModel(
                "2026-01-01", "manual", new BigDecimal("10000"), false, null, null, null);

        RealEstateModel realEstate1 = new RealEstateModel("re_1", "Appartement", "Investissement",
                new BigDecimal("200000"), 2026, new BigDecimal("0.02"), null);

        seedBudget(tresorerieSettings, List.of(), List.of(), List.of(), List.of(realEstate1), 1985, 64, null);

        OverviewResultModel currentEuros = useCase.getOverview(false);
        OverviewResultModel constantEuros = useCase.getOverview(true);

        assertThat(constantEuros.useConstantEuros()).isTrue();
        assertThat(currentEuros.useConstantEuros()).isFalse();
        assertThat(constantEuros.retirePatrimoine().pess()).isLessThan(currentEuros.retirePatrimoine().pess());
    }

    @Test
    @DisplayName("getOverview(null) traite null comme false (euros courants)")
    void nullUseConstantEuros() {
        OverviewResultModel result = useCase.getOverview(null);

        assertThat(result).isNotNull();
        assertThat(result.useConstantEuros()).isFalse();
    }
}
