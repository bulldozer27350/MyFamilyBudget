package com.moe.myfamilybudget.server.internal.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaxCalculatorTest {

    @Test
    @DisplayName("partsForYear() doit calculer correctement le nombre de parts fiscales")
    void testPartsForYear() {
        int exitAge = 21;
        int year = 2026;

        // Sans enfant : 2 parts
        double parts0 = TaxCalculator.partsForYear(List.of(), exitAge, year);
        assertEquals(2.0, parts0);

        // 1 enfant (< 21 ans) : 2.5 parts
        TaxChildModel c1 = new TaxChildModel("c1", "Enfant 1", 2015);
        double parts1 = TaxCalculator.partsForYear(List.of(c1), exitAge, year);
        assertEquals(2.5, parts1);

        // 2 enfants (< 21 ans) : 3.0 parts
        TaxChildModel c2 = new TaxChildModel("c2", "Enfant 2", 2018);
        double parts2 = TaxCalculator.partsForYear(List.of(c1, c2), exitAge, year);
        assertEquals(3.0, parts2);

        // 3 enfants (< 21 ans) : 4.0 parts (le 3e compte pour 1.0 part)
        TaxChildModel c3 = new TaxChildModel("c3", "Enfant 3", 2020);
        double parts3 = TaxCalculator.partsForYear(List.of(c1, c2, c3), exitAge, year);
        assertEquals(4.0, parts3);

        // Enfant ayant dépassé l'âge de rattachement (2026 - 2000 = 26 >= 21)
        TaxChildModel cAdult = new TaxChildModel("c4", "Adulte", 2000);
        double partsWithAdult = TaxCalculator.partsForYear(List.of(c1, cAdult), exitAge, year);
        assertEquals(2.5, partsWithAdult);
    }

    @Test
    @DisplayName("taxForOnePart() doit appliquer les tranches progressives avec précision")
    void testTaxForOnePart() {
        List<TaxBracketModel> brackets = List.of(
                new TaxBracketModel("tb1", new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracketModel("tb2", new BigDecimal("25000"), new BigDecimal("0.10")),
                new TaxBracketModel("tb3", null, new BigDecimal("0.30"))
        );

        // Tranche 1 (<= 10000) : 0 impôt
        BigDecimal tax1 = TaxCalculator.taxForOnePart(new BigDecimal("8000"), brackets);
        assertEquals(new BigDecimal("0.00"), tax1);

        // Tranche 2 (15000) : (15000 - 10000) * 0.10 = 500.00
        BigDecimal tax2 = TaxCalculator.taxForOnePart(new BigDecimal("15000"), brackets);
        assertEquals(new BigDecimal("500.00"), tax2);

        // Tranche 3 (35000) : (25000 - 10000) * 0.10 + (35000 - 25000) * 0.30 = 1500 + 3000 = 4500.00
        BigDecimal tax3 = TaxCalculator.taxForOnePart(new BigDecimal("35000"), brackets);
        assertEquals(new BigDecimal("4500.00"), tax3);
    }

    @Test
    @DisplayName("taxForOnePart() avec revenus nuls ou négatifs doit retourner 0")
    void testTaxForOnePartZeroOrNegative() {
        List<TaxBracketModel> brackets = List.of(
                new TaxBracketModel("tb1", new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracketModel("tb2", null, new BigDecimal("0.10"))
        );

        assertEquals(new BigDecimal("0.00"), TaxCalculator.taxForOnePart(BigDecimal.ZERO, brackets));
        assertEquals(new BigDecimal("0.00"), TaxCalculator.taxForOnePart(new BigDecimal("-500"), brackets));
        assertEquals(new BigDecimal("0.00"), TaxCalculator.taxForOnePart(null, brackets));
    }

    @Test
    @DisplayName("computeTaxYearly() doit calculer l'impôt prévisionnel, réel et le taux PAS")
    void testComputeTaxYearly() {
        SettingsModel settings = new SettingsModel(
                1985, 64, 85, new BigDecimal("0.02"), "", "manual", BigDecimal.ZERO, 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );

        List<TaxBracketModel> brackets = List.of(
                new TaxBracketModel("tb1", new BigDecimal("10000"), BigDecimal.ZERO),
                new TaxBracketModel("tb2", null, new BigDecimal("0.20"))
        );

        List<IncomeModel> incomes = List.of(
                new IncomeModel("inc1", "Salaire", new BigDecimal("4000"), "2026-01-01", "2026-12-31", BigDecimal.ZERO, null, null)
        );

        BudgetDataModel data = new BudgetDataModel(
                settings,
                incomes,
                List.of(), List.of(), List.of(), null,
                List.of(),
                brackets,
                List.of(new TaxRateOverrideModel(2026, new BigDecimal("0.08"))),
                List.of(new TaxActualOverrideModel(2026, new BigDecimal("3500.00"))),
                List.of(), List.of(), List.of(), List.of(), null
        );

        List<TaxYearlyModel> yearly = TaxCalculator.computeTaxYearly(data, List.of(2026), incomes);

        assertThat(yearly).hasSize(1);
        TaxYearlyModel y2026 = yearly.get(0);

        assertEquals(2026, y2026.year());
        assertEquals(2.0, y2026.parts());
        // Brut annuel = 4000 * 12 = 48000. Abattement 10% -> Taxable = 43200
        assertEquals(new BigDecimal("43200.00"), y2026.taxableIncome());
        // Quote-part = 43200 / 2 = 21600.
        // Impôt 1 part = (21600 - 10000) * 0.20 = 2320. Impôt total 2 parts = 4640.00
        assertEquals(new BigDecimal("4640.00"), y2026.taxForecast());
        // Tax actual override pour 2026 = 3500.00
        assertEquals(new BigDecimal("3500.00"), y2026.taxActual());
        // Rate PAS override pour 2026 = 0.08
        assertEquals(new BigDecimal("0.08"), y2026.ratePAS());
        // Retenu à la source = 0.08 * 48000 = 3840.00
        assertEquals(new BigDecimal("3840.00"), y2026.withheld());
    }

    @Test
    @DisplayName("buildTaxPreview() doit filtrer la fenêtre de 6 ans à partir de l'année courante")
    void testBuildTaxPreview() {
        List<TaxYearlyModel> list = List.of(
                new TaxYearlyModel(2023, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2024, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2025, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2026, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2027, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2028, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new TaxYearlyModel(2029, 2.0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        );

        List<TaxYearlyModel> preview = TaxCalculator.buildTaxPreview(list, 2026);
        assertThat(preview).hasSize(4);
        assertEquals(2026, preview.get(0).year());
        assertEquals(2029, preview.get(3).year());
    }
}
