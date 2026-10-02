package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.AnnualTaxIncome;
import com.moe.myfamilybudget.domain.retirement.calculation.AnnualTaxableRetirementIncome;
import com.moe.myfamilybudget.server.internal.calculation.AnnualVariableIncome;
import com.moe.myfamilybudget.server.internal.calculation.TaxActualOverride;
import com.moe.myfamilybudget.server.internal.calculation.TaxBracket;
import com.moe.myfamilybudget.server.internal.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.server.internal.calculation.TaxHouseholdParameters;
import com.moe.myfamilybudget.server.internal.calculation.TaxRateOverride;
import com.moe.myfamilybudget.server.internal.calculation.TaxSimulationPeriod;

/**
 * Tests de composant du moteur fiscal (RF-203) : uniquement {@link TaxCalculationInput}, aucun
 * {@code BudgetDataModel}, aucun contexte Spring.
 */
class TaxCalculatorTest {

    private static final int YEAR = 2026;

    private static List<TaxBracket> standardBrackets() {
        return List.of(
                new TaxBracket(new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracket(null, new BigDecimal("0.20")));
    }

    private static TaxCalculationInput input(
            TaxSimulationPeriod period,
            BigDecimal abattement,
            List<Integer> childBirthYears,
            List<TaxBracket> brackets,
            List<AnnualTaxIncome> incomes,
            List<AnnualVariableIncome> variableIncomes,
            List<AnnualTaxableRetirementIncome> retirementIncome,
            List<TaxRateOverride> rateOverrides,
            List<TaxActualOverride> actualOverrides) {
        return new TaxCalculationInput(
                period,
                new TaxHouseholdParameters(1985, 64, 21, abattement),
                incomes,
                variableIncomes,
                childBirthYears,
                brackets,
                rateOverrides,
                actualOverrides,
                retirementIncome);
    }

    private static TaxCalculationInput salaryInput(
            BigDecimal annualSalary, List<Integer> childBirthYears, List<TaxBracket> brackets) {
        return input(new TaxSimulationPeriod(YEAR, YEAR), new BigDecimal("0.10"), childBirthYears, brackets,
                List.of(new AnnualTaxIncome(YEAR, annualSalary)), List.of(), List.of(), List.of(), List.of());
    }

    private static TaxYearlyModel only(List<TaxYearlyModel> result) {
        assertThat(result).hasSize(1);
        return result.get(0);
    }

    @Test
    @DisplayName("computeTaxYearly() : impôt prévisionnel, abattement et barème progressif")
    void testForecastWithAbattementAndBrackets() {
        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(
                salaryInput(new BigDecimal("48000"), List.of(), standardBrackets())));

        assertThat(y.year()).isEqualTo(YEAR);
        assertThat(y.parts()).isEqualTo(2.0);
        // 48000 - 10 % = 43200 ; quotient 21600 ; (21600 - 10000) * 0,20 = 2320 ; x 2 parts = 4640
        assertThat(y.taxableIncome()).isEqualByComparingTo("43200.00");
        assertThat(y.taxForecast()).isEqualByComparingTo("4640.00");
        // Sans surcharge : impôt réel = prévisionnel, taux PAS = impôt / brut, retenue = taux x brut
        assertThat(y.taxActual()).isEqualByComparingTo("4640.00");
        assertThat(y.ratePAS()).isEqualByComparingTo("0.0966666667");
        assertThat(y.withheld()).isEqualByComparingTo("4640.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : les surcharges d'impôt réel et de taux PAS remplacent le calcul")
    void testOverrides() {
        TaxCalculationInput base = salaryInput(new BigDecimal("48000"), List.of(), standardBrackets());
        TaxCalculationInput input = input(base.period(), new BigDecimal("0.10"), List.of(), standardBrackets(),
                base.incomes(), List.of(), List.of(),
                List.of(new TaxRateOverride(YEAR, new BigDecimal("0.08"))),
                List.of(new TaxActualOverride(YEAR, new BigDecimal("3500.00"))));

        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(input));

        assertThat(y.taxForecast()).isEqualByComparingTo("4640.00");
        assertThat(y.taxActual()).isEqualByComparingTo("3500.00");
        assertThat(y.ratePAS()).isEqualByComparingTo("0.08");
        // 0,08 x 48000
        assertThat(y.withheld()).isEqualByComparingTo("3840.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : revenu brut = revenus réguliers + variables imposables + pension")
    void testGrossIncomeSumsAllSources() {
        TaxCalculationInput input = input(new TaxSimulationPeriod(YEAR, YEAR), BigDecimal.ZERO, List.of(),
                standardBrackets(),
                List.of(new AnnualTaxIncome(YEAR, new BigDecimal("30000"))),
                List.of(new AnnualVariableIncome(YEAR, new BigDecimal("5000"))),
                List.of(new AnnualTaxableRetirementIncome(YEAR, new BigDecimal("10000"))),
                List.of(), List.of());

        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(input));

        // brut 45000 ; quotient 22500 ; (22500 - 10000) * 0,20 = 2500 ; x 2 parts = 5000
        assertThat(y.taxableIncome()).isEqualByComparingTo("45000.00");
        assertThat(y.taxForecast()).isEqualByComparingTo("5000.00");
        assertThat(y.ratePAS()).isEqualByComparingTo("0.1111111111");
        assertThat(y.withheld()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : plusieurs entrées pour une même année sont additionnées")
    void testDuplicateYearEntriesAreSummed() {
        TaxCalculationInput input = input(new TaxSimulationPeriod(YEAR, YEAR), new BigDecimal("0.10"),
                List.of(), standardBrackets(),
                List.of(new AnnualTaxIncome(YEAR, new BigDecimal("20000")),
                        new AnnualTaxIncome(YEAR, new BigDecimal("28000"))),
                List.of(), List.of(), List.of(), List.of());

        assertThat(only(TaxCalculator.computeTaxYearly(input)).taxForecast()).isEqualByComparingTo("4640.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : parts fiscales selon les enfants rattachés (moins de childExitAge)")
    void testPartsWithChildren() {
        BigDecimal salary = new BigDecimal("48000");

        assertThat(only(TaxCalculator.computeTaxYearly(salaryInput(salary, List.of(), standardBrackets()))).parts())
                .isEqualTo(2.0);
        assertThat(only(TaxCalculator.computeTaxYearly(salaryInput(salary, List.of(2015), standardBrackets()))).parts())
                .isEqualTo(2.5);
        assertThat(only(TaxCalculator.computeTaxYearly(
                salaryInput(salary, List.of(2015, 2018), standardBrackets()))).parts()).isEqualTo(3.0);
        // 3e enfant rattaché : +1 part
        assertThat(only(TaxCalculator.computeTaxYearly(
                salaryInput(salary, List.of(2015, 2018, 2020), standardBrackets()))).parts()).isEqualTo(4.0);
        // Enfant de 21 ans (2005) : n'est plus rattaché
        assertThat(only(TaxCalculator.computeTaxYearly(
                salaryInput(salary, List.of(2015, 2005), standardBrackets()))).parts()).isEqualTo(2.5);
        // Année de naissance inconnue : ignorée
        assertThat(only(TaxCalculator.computeTaxYearly(
                salaryInput(salary, Arrays.asList(null, 2015), standardBrackets()))).parts()).isEqualTo(2.5);
    }

    @Test
    @DisplayName("computeTaxYearly() : les tranches sont triées, la tranche sans plafond passe en dernier")
    void testUnsortedBrackets() {
        List<TaxBracket> unsorted = List.of(
                new TaxBracket(null, new BigDecimal("0.20")),
                new TaxBracket(new BigDecimal("10000"), BigDecimal.ZERO));

        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(
                salaryInput(new BigDecimal("48000"), List.of(), unsorted)));

        assertThat(y.taxForecast()).isEqualByComparingTo("4640.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : revenu sous le premier plafond ou barème vide -> impôt nul")
    void testZeroTax() {
        TaxYearlyModel belowThreshold = only(TaxCalculator.computeTaxYearly(
                salaryInput(new BigDecimal("15000"), List.of(), standardBrackets())));
        // 15000 - 10 % = 13500 ; quotient 6750 < 10000 -> tranche à 0 %
        assertThat(belowThreshold.taxForecast()).isEqualByComparingTo("0.00");

        TaxYearlyModel noBrackets = only(TaxCalculator.computeTaxYearly(
                salaryInput(new BigDecimal("48000"), List.of(), List.of())));
        assertThat(noBrackets.taxForecast()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : sans revenu, taux PAS et retenue à la source sont nuls")
    void testNoIncome() {
        TaxCalculationInput input = input(new TaxSimulationPeriod(YEAR, YEAR), new BigDecimal("0.10"),
                List.of(), standardBrackets(), List.of(), List.of(), List.of(), List.of(), List.of());

        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(input));

        assertThat(y.taxableIncome()).isEqualByComparingTo("0.00");
        assertThat(y.taxForecast()).isEqualByComparingTo("0.00");
        assertThat(y.ratePAS()).isEqualByComparingTo("0");
        assertThat(y.withheld()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : abattement absent traité comme nul")
    void testNullAbattement() {
        TaxCalculationInput input = input(new TaxSimulationPeriod(YEAR, YEAR), null, List.of(),
                standardBrackets(), List.of(new AnnualTaxIncome(YEAR, new BigDecimal("48000"))),
                List.of(), List.of(), List.of(), List.of());

        TaxYearlyModel y = only(TaxCalculator.computeTaxYearly(input));

        assertThat(y.taxableIncome()).isEqualByComparingTo("48000.00");
        // quotient 24000 ; (24000 - 10000) * 0,20 = 2800 ; x 2 parts = 5600
        assertThat(y.taxForecast()).isEqualByComparingTo("5600.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : une ligne par année de la période, années sans donnée à zéro")
    void testPeriodDrivesYears() {
        TaxCalculationInput input = input(new TaxSimulationPeriod(2026, 2028), new BigDecimal("0.10"),
                List.of(), standardBrackets(),
                List.of(new AnnualTaxIncome(2027, new BigDecimal("48000"))),
                List.of(), List.of(), List.of(), List.of());

        List<TaxYearlyModel> result = TaxCalculator.computeTaxYearly(input);

        assertThat(result).extracting(TaxYearlyModel::year).containsExactly(2026, 2027, 2028);
        assertThat(result.get(0).taxForecast()).isEqualByComparingTo("0.00");
        assertThat(result.get(1).taxForecast()).isEqualByComparingTo("4640.00");
        assertThat(result.get(2).taxForecast()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("computeTaxYearly() : entrée ou période absente -> liste vide")
    void testNullInput() {
        assertThat(TaxCalculator.computeTaxYearly(null)).isEmpty();
        assertThat(TaxCalculator.computeTaxYearly(input(null, BigDecimal.ZERO, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of()))).isEmpty();
    }

    private static List<TaxYearlyModel> yearly(int fromYear, int toYear) {
        List<TaxYearlyModel> list = new ArrayList<>();
        for (int y = fromYear; y <= toYear; y++) {
            list.add(new TaxYearlyModel(y, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return list;
    }

    @Test
    @DisplayName("buildTaxPreview() : fenêtre à partir de l'année courante")
    void testBuildTaxPreview() {
        List<TaxYearlyModel> preview = TaxCalculator.buildTaxPreview(yearly(2023, 2029), 2026);

        assertThat(preview).extracting(TaxYearlyModel::year).containsExactly(2026, 2027, 2028, 2029);
    }

    @Test
    @DisplayName("buildTaxPreview() : plafonnée à 6 ans")
    void testBuildTaxPreviewCappedAtSixYears() {
        List<TaxYearlyModel> preview = TaxCalculator.buildTaxPreview(yearly(2026, 2035), 2026);

        assertThat(preview).extracting(TaxYearlyModel::year).containsExactly(2026, 2027, 2028, 2029, 2030, 2031);
    }

    @Test
    @DisplayName("buildTaxPreview() : tout dans le passé -> les 6 dernières années ; vide -> vide")
    void testBuildTaxPreviewAllPastAndEmpty() {
        assertThat(TaxCalculator.buildTaxPreview(yearly(2019, 2025), 2030))
                .extracting(TaxYearlyModel::year).containsExactly(2020, 2021, 2022, 2023, 2024, 2025);
        assertThat(TaxCalculator.buildTaxPreview(List.of(), 2026)).isEmpty();
        assertThat(TaxCalculator.buildTaxPreview(null, 2026)).isEmpty();
    }
}
