package com.moe.myfamilybudget.server.internal.factory;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.factory.TaxInputFactory;
import com.moe.myfamilybudget.application.factory.TaxSimulationPeriodResolver;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.tax.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.domain.tax.calculation.TaxablePensionIncome;
import com.moe.myfamilybudget.domain.tax.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;

/**
 * RF-200 / RF-202 : vérifie la construction de {@link TaxCalculationInput} par
 * {@link TaxInputFactory} (période fournie explicitement, pension issue de
 * {@link RetirementProjection}) — pas un test de composant du domaine (voir RF-203).
 */
class TaxInputFactoryTest {

    private static TaxInputFactory.Sources budgetWithSalaryOnly() {
        List<IncomeModel> incomes = List.of(
                new IncomeModel("inc1", "Salaire", new BigDecimal("4000"), "2026-01-01", "2026-12-31",
                        BigDecimal.ZERO, null, null));

        List<TaxBracketModel> brackets = List.of(
                new TaxBracketModel("tb1", new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracketModel("tb2", null, new BigDecimal("0.20")));

        List<TaxChildModel> children = List.of(
                new TaxChildModel("c1", "Enfant 1", 2015),
                new TaxChildModel("c2", "Sans date", null));

        return new TaxInputFactory.Sources(
                new RetirementSettingsModel(1985, 64),
                new TaxSettingsModel(21, new BigDecimal("0.10")),
                new BigDecimal("0.02"),
                incomes,
                List.of(),
                List.of(),
                children,
                brackets,
                List.of(new TaxRateOverrideModel(2026, new BigDecimal("0.08"))),
                List.of(new TaxActualOverrideModel(2026, new BigDecimal("3500.00"))));
    }

    private static TaxCalculationInput build(TaxInputFactory.Sources data) {
        RetirementProjection projection =
                new RetirementCalculationService().compute(new RetirementInputFactory().create(
                        data.retirementSettings(), null, data.incomes(), data.taxChildren().size()));
        TaxSimulationPeriod period = TaxSimulationPeriodResolver.resolve(new TaxSimulationPeriodResolver.Sources(
                data.retirementSettings(), new SimulationSettingsModel(85), "2026-01-01",
                data.incomes(), List.of(), List.of(), List.of(), List.of(), null));
        return TaxInputFactory.from(data, period, projection);
    }

    private static RetirementProjectionModel pension(String monthly) {
        BigDecimal m = new BigDecimal(monthly);
        return new RetirementProjectionModel(64, 0, 0, 172, false, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, m.multiply(BigDecimal.valueOf(12)), m);
    }

    @Test
    @DisplayName("from() : reprend telle quelle la période fournie, une entrée par année")
    void testPeriodIsProvidedExplicitly() {
        TaxInputFactory.Sources data = budgetWithSalaryOnly();
        TaxSimulationPeriod period = new TaxSimulationPeriod(2030, 2032);

        TaxCalculationInput input = TaxInputFactory.from(data, period, new RetirementProjection(List.of()));

        assertThat(input.period()).isEqualTo(period);
        assertThat(input.incomes()).extracting(AnnualTaxIncome::year).containsExactly(2030, 2031, 2032);
        assertThat(input.variableIncomes()).hasSize(3);
        assertThat(input.retirementIncome()).hasSize(3);
    }

    @Test
    @DisplayName("from() : pension mensuelle du moteur Retraite -> montant annuel (x12) à partir de l'année de départ")
    void testRetirementIncomeFromProjection() {
        TaxInputFactory.Sources data = budgetWithSalaryOnly();
        int retireYear = 1985 + 64;
        TaxSimulationPeriod period = new TaxSimulationPeriod(retireYear - 1, retireYear + 1);
        RetirementProjection projection = new RetirementProjection(List.of(pension("1500"), pension("500")));

        TaxCalculationInput input = TaxInputFactory.from(data, period, projection);

        assertThat(input.retirementIncome()).extracting(TaxablePensionIncome::year)
                .containsExactly(retireYear - 1, retireYear, retireYear + 1);
        assertThat(input.retirementIncome().get(0).amount()).isEqualByComparingTo("0");
        // (1500 + 500) * 12, inflation 2 % appliquée dès l'année suivante
        assertThat(input.retirementIncome().get(1).amount()).isEqualByComparingTo("24000.00");
        assertThat(input.retirementIncome().get(2).amount()).isEqualByComparingTo("24480.00");
    }

    @Test
    @DisplayName("from() : une projection retraite absente ou à pension nulle donne une pension nulle")
    void testRetirementIncomeZeroWithoutPension() {
        TaxSimulationPeriod period = new TaxSimulationPeriod(2049, 2050);

        TaxCalculationInput none = TaxInputFactory.from(budgetWithSalaryOnly(), period, null);
        TaxCalculationInput zero = TaxInputFactory.from(budgetWithSalaryOnly(), period,
                new RetirementProjection(List.of(pension("0"))));

        assertThat(none.retirementIncome()).extracting(TaxablePensionIncome::amount)
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo(BigDecimal.ZERO));
        assertThat(zero.retirementIncome()).extracting(TaxablePensionIncome::amount)
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from() : household reprend birthYear/retireAge/childExitAge/taxAbattement des settings")
    void testHouseholdParameters() {
        TaxCalculationInput input = build(budgetWithSalaryOnly());

        assertThat(input.household().birthYear()).isEqualTo(1985);
        assertThat(input.household().retireAge()).isEqualTo(64);
        assertThat(input.household().childExitAge()).isEqualTo(21);
        assertThat(input.household().taxAbattement()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("from() : incomes contient le revenu annuel 2026 = 4000 * 12 = 48000, une entrée par année de la période")
    void testAnnualIncomes() {
        TaxInputFactory.Sources data = budgetWithSalaryOnly();
        TaxCalculationInput input = build(data);

        int expectedYears = input.period().endYear() - input.period().startYear() + 1;
        assertThat(input.incomes()).hasSize(expectedYears);

        AnnualTaxIncome year2026 = input.incomes().stream()
                .filter(i -> i.year() == 2026).findFirst().orElseThrow();
        assertThat(year2026.amount()).isEqualByComparingTo("48000.00");
    }

    @Test
    @DisplayName("from() : sans revenu variable configuré, variableIncomes est à zéro pour chaque année")
    void testVariableIncomesZeroWhenNoneConfigured() {
        TaxCalculationInput input = build(budgetWithSalaryOnly());

        assertThat(input.variableIncomes())
                .extracting(AnnualVariableIncome::taxableAmount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from() : sans personne en retraite configurée, retirementIncome est à zéro pour chaque année")
    void testRetirementIncomeZeroWhenNoRetirementConfigured() {
        TaxCalculationInput input = build(budgetWithSalaryOnly());

        assertThat(input.retirementIncome())
                .extracting(TaxablePensionIncome::amount)
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("from() : childBirthYears ne retient que les enfants avec une année de naissance connue")
    void testChildBirthYearsIgnoresNullBirthYear() {
        TaxCalculationInput input = build(budgetWithSalaryOnly());

        assertThat(input.childBirthYears()).containsExactly(2015);
    }

    @Test
    @DisplayName("from() : brackets/rateOverrides/actualOverrides reprennent les montants du modèle source")
    void testBracketsAndOverrides() {
        TaxCalculationInput input = build(budgetWithSalaryOnly());

        assertThat(input.brackets()).hasSize(2);
        assertThat(input.brackets().get(0).upTo()).isEqualByComparingTo("10000");
        assertThat(input.brackets().get(1).rate()).isEqualByComparingTo("0.20");

        assertThat(input.rateOverrides()).hasSize(1);
        assertThat(input.rateOverrides().get(0).year()).isEqualTo(2026);
        assertThat(input.rateOverrides().get(0).rate()).isEqualByComparingTo("0.08");

        assertThat(input.actualOverrides()).hasSize(1);
        assertThat(input.actualOverrides().get(0).year()).isEqualTo(2026);
        assertThat(input.actualOverrides().get(0).amount()).isEqualByComparingTo("3500.00");
    }
}
