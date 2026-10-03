package com.moe.myfamilybudget.server.internal.calculation;

import com.moe.myfamilybudget.domain.wealth.calculation.PlacementCashflowInput;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.budget.CashflowYearModel;
import com.moe.myfamilybudget.domain.budget.VariablePreviewCellModel;
import com.moe.myfamilybudget.domain.budget.VariablePreviewModel;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementIncomeProjection;
import com.moe.myfamilybudget.domain.tax.calculation.TaxProjection;

/**
 * RF-402 : tests de composant du moteur de trésorerie, construits uniquement avec
 * {@link TreasuryProjectionInput} — aucun {@code BudgetDataModel}, aucune factory et aucun
 * contexte Spring. Chaque scénario construit un {@link TreasuryProjectionInput} minimal et vérifie
 * le comportement de {@link TresorerieCalculationService#compute(TreasuryProjectionInput)}.
 */
@DisplayName("TresorerieCalculationService - tests de composant sur TreasuryProjectionInput")
class TresorerieCalculationServiceComponentTest {

    private final TresorerieCalculationService service = new TresorerieCalculationService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static TreasuryProjectionInput minimalInput(int startYear, int endYear) {
        return new TreasuryProjectionInput(
                new TreasurySimulationPeriod(startYear, endYear),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TaxProjection(List.of()),
                new RetirementIncomeProjection(List.of()),
                new TreasuryParameters(endYear, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    // -----------------------------------------------------------------------
    // 1. Fonctions unitaires (chargeMonthlyForYear, incomeMonthlyForYear, etc.)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("chargeMonthlyForYear")
    class ChargeMonthlyForYearTests {

        @Test
        @DisplayName("Renvoie le mensuel de départ pour l'année de début")
        void startYear() {
            ChargeProjectionInput c = new ChargeProjectionInput("Loyer", bd("800"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), null);
            assertThat(TresorerieCalculationService.chargeMonthlyForYear(c, 2026, bd("0.015")))
                    .isEqualByComparingTo(bd("800"));
        }

        @Test
        @DisplayName("Applique l'inflation après la première année")
        void withInflation() {
            ChargeProjectionInput c = new ChargeProjectionInput("Loyer", bd("800"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), null);
            BigDecimal result = TresorerieCalculationService.chargeMonthlyForYear(c, 2027, bd("0.02"));
            // 800 * 1.02 = 816
            assertThat(result).isEqualByComparingTo(bd("816"));
        }

        @Test
        @DisplayName("Utilise le growthRate explicite plutôt que l'inflation")
        void withExplicitGrowth() {
            ChargeProjectionInput c = new ChargeProjectionInput("Assurance", bd("100"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), bd("0.05"));
            BigDecimal result = TresorerieCalculationService.chargeMonthlyForYear(c, 2027, bd("0.02"));
            // 100 * 1.05 = 105
            assertThat(result).isEqualByComparingTo(bd("105"));
        }

        @Test
        @DisplayName("Renvoie ZERO hors de la plage active")
        void outsideRange() {
            ChargeProjectionInput c = new ChargeProjectionInput("Loyer", bd("800"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), null);
            assertThat(TresorerieCalculationService.chargeMonthlyForYear(c, 2025, bd("0.015")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(TresorerieCalculationService.chargeMonthlyForYear(c, 2031, bd("0.015")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Renvoie ZERO pour une charge nulle")
        void nullCharge() {
            assertThat(TresorerieCalculationService.chargeMonthlyForYear(null, 2026, bd("0.015")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("chargeAnnualForYear")
    class ChargeAnnualForYearTests {

        @Test
        @DisplayName("12 mois complets pour une année entière")
        void fullYear() {
            ChargeProjectionInput c = new ChargeProjectionInput("Loyer", bd("800"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), null);
            BigDecimal result = TresorerieCalculationService.chargeAnnualForYear(c, 2026, bd("0.015"));
            // 800 * 12 = 9600
            assertThat(result).isEqualByComparingTo(bd("9600"));
        }

        @Test
        @DisplayName("Année partielle en début de charge")
        void partialStartYear() {
            ChargeProjectionInput c = new ChargeProjectionInput("Loyer", bd("800"),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2030, 12, 31), null);
            BigDecimal result = TresorerieCalculationService.chargeAnnualForYear(c, 2026, bd("0.015"));
            // 6 mois * 800 = 4800
            assertThat(result).isEqualByComparingTo(bd("4800"));
        }
    }

    @Nested
    @DisplayName("incomeMonthlyForYear")
    class IncomeMonthlyForYearTests {

        @Test
        @DisplayName("Renvoie le mensuel de départ pour l'année de début")
        void startYear() {
            IncomeProjectionInput i = new IncomeProjectionInput("Salaire", bd("3000"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.02"));
            assertThat(TresorerieCalculationService.incomeMonthlyForYear(i, 2026))
                    .isEqualByComparingTo(bd("3000"));
        }

        @Test
        @DisplayName("Applique la croissance après la première année")
        void withGrowth() {
            IncomeProjectionInput i = new IncomeProjectionInput("Salaire", bd("3000"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.02"));
            BigDecimal result = TresorerieCalculationService.incomeMonthlyForYear(i, 2027);
            // 3000 * 1.02 = 3060
            assertThat(result).isEqualByComparingTo(bd("3060"));
        }

        @Test
        @DisplayName("Renvoie ZERO hors de la plage active")
        void outsideRange() {
            IncomeProjectionInput i = new IncomeProjectionInput("Salaire", bd("3000"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.02"));
            assertThat(TresorerieCalculationService.incomeMonthlyForYear(i, 2025))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("incomeAnnualForYear")
    class IncomeAnnualForYearTests {

        @Test
        @DisplayName("12 mois complets pour une année entière")
        void fullYear() {
            IncomeProjectionInput i = new IncomeProjectionInput("Salaire", bd("3000"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.02"));
            BigDecimal result = TresorerieCalculationService.incomeAnnualForYear(i, 2026);
            // 3000 * 12 = 36000
            assertThat(result).isEqualByComparingTo(bd("36000"));
        }

        @Test
        @DisplayName("Année partielle en fin de revenu")
        void partialEndYear() {
            IncomeProjectionInput i = new IncomeProjectionInput("Salaire", bd("3000"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2028, 6, 30), bd("0.00"));
            BigDecimal result = TresorerieCalculationService.incomeAnnualForYear(i, 2028);
            // 6 mois * 3000 = 18000
            assertThat(result).isEqualByComparingTo(bd("18000"));
        }
    }

    // -----------------------------------------------------------------------
    // 2. compute() — scénarios d'intégration sur TreasuryProjectionInput
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("compute()")
    class ComputeTests {

        @Test
        @DisplayName("Projection vide : aucun flux, solde initial conservé")
        void emptyProjection() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2028),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, bd("5000"), BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            assertThat(result.years()).containsExactly(2026, 2027, 2028);
            assertThat(result.cashflow()).hasSize(3);
            // Solde conservé à 5000 chaque année (net = 0)
            for (CashflowYearModel cf : result.cashflow()) {
                assertThat(cf.net()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(cf.balance()).isEqualByComparingTo(bd("5000"));
            }
        }

        @Test
        @DisplayName("Revenu seul : solde = startBalance + revenu annuel cumulé")
        void incomeOnly() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2026),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, bd("1000"), BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            assertThat(result.cashflow()).hasSize(1);
            CashflowYearModel cf = result.cashflow().get(0);
            // income = 3000 * 12 = 36000
            assertThat(cf.income()).isEqualByComparingTo(bd("36000"));
            assertThat(cf.net()).isEqualByComparingTo(bd("36000"));
            assertThat(cf.balance()).isEqualByComparingTo(bd("37000")); // 1000 + 36000
        }

        @Test
        @DisplayName("Revenu et charges : net = revenu - charges")
        void incomeMinusCharges() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2026),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(new ChargeProjectionInput("Loyer", bd("800"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), null)),
                    List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            CashflowYearModel cf = result.cashflow().get(0);
            // income = 36000, charges = 9600, net = 26400
            assertThat(cf.income()).isEqualByComparingTo(bd("36000"));
            assertThat(cf.charges()).isEqualByComparingTo(bd("9600"));
            assertThat(cf.net()).isEqualByComparingTo(bd("26400"));
        }

        @Test
        @DisplayName("Impôts : prélèvement à la source déduit + régularisation N+1")
        void taxWithholding() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2027),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of(
                            new TaxProjection.Withholding(2026, bd("4000"), bd("3800")),
                            new TaxProjection.Withholding(2027, bd("4000"), bd("4200")))),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            // 2026 : net = 36000 - 4000(withheld) = 32000, régularisation = 0 (première année)
            CashflowYearModel cf2026 = result.cashflow().get(0);
            assertThat(cf2026.impots()).isEqualByComparingTo(bd("4000"));
            assertThat(cf2026.regularisation()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(cf2026.net()).isEqualByComparingTo(bd("32000"));

            // 2027 : régularisation = actual(2026) - withheld(2026) = 3800 - 4000 = -200
            //        net = 36000 - 4000(withheld) - (-200)(régul) = 32200
            CashflowYearModel cf2027 = result.cashflow().get(1);
            assertThat(cf2027.regularisation()).isEqualByComparingTo(bd("-200"));
            assertThat(cf2027.net()).isEqualByComparingTo(bd("32200"));
        }

        @Test
        @DisplayName("Pensions de retraite ajoutées au revenu")
        void retirementIncome() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2045, 2045),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of(
                            new RetirementIncomeProjection.AnnualPension(2045, bd("24000")))),
                    new TreasuryParameters(2045, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            CashflowYearModel cf = result.cashflow().get(0);
            assertThat(cf.income()).isEqualByComparingTo(bd("24000"));
            assertThat(cf.net()).isEqualByComparingTo(bd("24000"));
        }

        @Test
        @DisplayName("Dépenses ponctuelles déduites l'année de leur date")
        void oneOffExpenses() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2027),
                    List.of(), List.of(), List.of(),
                    List.of(new OneOffCashflow(LocalDate.of(2026, 6, 15), bd("5000"))),
                    List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, bd("10000"), BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            CashflowYearModel cf2026 = result.cashflow().get(0);
            assertThat(cf2026.oneoff()).isEqualByComparingTo(bd("5000"));
            assertThat(cf2026.net()).isEqualByComparingTo(bd("-5000"));
            assertThat(cf2026.balance()).isEqualByComparingTo(bd("5000")); // 10000 - 5000

            // 2027 : pas de dépense ponctuelle
            CashflowYearModel cf2027 = result.cashflow().get(1);
            assertThat(cf2027.oneoff()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Virements ajoutés au net")
        void transfers() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2026),
                    List.of(), List.of(), List.of(), List.of(),
                    List.of(new TransferProjection(LocalDate.of(2026, 3, 1), bd("2000"))),
                    List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            CashflowYearModel cf = result.cashflow().get(0);
            assertThat(cf.transfersY()).isEqualByComparingTo(bd("2000"));
            assertThat(cf.net()).isEqualByComparingTo(bd("2000"));
        }

        @Test
        @DisplayName("Versements placements déduits du net")
        void placementSavings() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2026),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(new PlacementCashflowInput(2026, bd("6000"))),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, bd("10000"), BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            CashflowYearModel cf = result.cashflow().get(0);
            assertThat(cf.savings()).isEqualByComparingTo(bd("6000"));
            assertThat(cf.net()).isEqualByComparingTo(bd("-6000"));
            assertThat(cf.balance()).isEqualByComparingTo(bd("4000")); // 10000 - 6000
        }

        @Test
        @DisplayName("Revenus variables : prévision par taux + override")
        void variableIncome() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2027),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(),
                    List.of(new VariableIncomeProjection("Prime", "Salaire", 2026, 2040, bd("0.10"),
                            null, List.of(new VariableIncomeProjection.Override(2026, bd("4000"), null)))),
                    List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            // 2026 : override = 4000
            CashflowYearModel cf2026 = result.cashflow().get(0);
            assertThat(cf2026.variableIncome()).isEqualByComparingTo(bd("4000"));

            // 2027 : prévision = 36000 * 0.10 = 3600
            CashflowYearModel cf2027 = result.cashflow().get(1);
            assertThat(cf2027.variableIncome()).isEqualByComparingTo(bd("3600"));
        }

        @Test
        @DisplayName("Solde cumulé sur plusieurs années")
        void cumulativeBalance() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2028),
                    List.of(new IncomeProjectionInput("Salaire", bd("1000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(new ChargeProjectionInput("Loyer", bd("500"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(), List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, bd("0"), bd("0.00")));

            TreasuryProjection result = service.compute(input);

            // net annuel = (1000 - 500) * 12 = 6000
            assertThat(result.cashflow().get(0).balance()).isEqualByComparingTo(bd("6000"));
            assertThat(result.cashflow().get(1).balance()).isEqualByComparingTo(bd("12000"));
            assertThat(result.cashflow().get(2).balance()).isEqualByComparingTo(bd("18000"));
        }
    }

    // -----------------------------------------------------------------------
    // 3. Aperçu des revenus variables (variablePreview)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("variablePreview")
    class VariablePreviewTests {

        @Test
        @DisplayName("Aperçu limité aux 4 premières années")
        void previewYearsLimitedToFour() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2035),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(),
                    List.of(new VariableIncomeProjection("Prime", "Salaire", null, null, bd("0.10"),
                            null, List.of())),
                    List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            assertThat(result.previewYears()).containsExactly(2026, 2027, 2028, 2029);
            assertThat(result.variablePreview()).hasSize(1);
            VariablePreviewModel preview = result.variablePreview().get(0);
            assertThat(preview.label()).isEqualTo("Prime");
            assertThat(preview.cells()).hasSize(4);
            // Toutes sont des prévisions (isReal = false), montant = 36000 * 0.10 = 3600
            for (VariablePreviewCellModel cell : preview.cells()) {
                assertThat(cell.isReal()).isFalse();
                assertThat(cell.amount()).isEqualByComparingTo(bd("3600"));
            }
        }

        @Test
        @DisplayName("Override marqué isReal dans l'aperçu")
        void overrideMarkedAsReal() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2026, 2028),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(),
                    List.of(new VariableIncomeProjection("Prime", "Salaire", null, null, bd("0.10"),
                            null, List.of(new VariableIncomeProjection.Override(2026, bd("5000"), null)))),
                    List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            VariablePreviewModel preview = result.variablePreview().get(0);
            // 2026 : override
            assertThat(preview.cells().get(0).isReal()).isTrue();
            assertThat(preview.cells().get(0).amount()).isEqualByComparingTo(bd("5000"));
            // 2027 : prévision
            assertThat(preview.cells().get(1).isReal()).isFalse();
            assertThat(preview.cells().get(1).amount()).isEqualByComparingTo(bd("3600"));
        }

        @Test
        @DisplayName("Revenu variable hors plage : cellule avec montant null")
        void outsideRangeNullAmount() {
            TreasuryProjectionInput input = new TreasuryProjectionInput(
                    new TreasurySimulationPeriod(2025, 2028),
                    List.of(new IncomeProjectionInput("Salaire", bd("3000"),
                            LocalDate.of(2025, 1, 1), LocalDate.of(2040, 12, 31), bd("0.00"))),
                    List.of(),
                    List.of(new VariableIncomeProjection("Prime", "Salaire", 2027, 2040, bd("0.10"),
                            null, List.of())),
                    List.of(), List.of(), List.of(),
                    new TaxProjection(List.of()),
                    new RetirementIncomeProjection(List.of()),
                    new TreasuryParameters(2060, BigDecimal.ZERO, BigDecimal.ZERO));

            TreasuryProjection result = service.compute(input);

            VariablePreviewModel preview = result.variablePreview().get(0);
            // 2025, 2026 : hors plage → null
            assertThat(preview.cells().get(0).amount()).isNull();
            assertThat(preview.cells().get(1).amount()).isNull();
            // 2027 : dans la plage
            assertThat(preview.cells().get(2).amount()).isEqualByComparingTo(bd("3600"));
        }
    }

    // -----------------------------------------------------------------------
    // 4. monthsActiveInYear
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("monthsActiveInYear")
    class MonthsActiveTests {

        @Test
        @DisplayName("Année complète = 12 mois")
        void fullYear() {
            assertThat(TresorerieCalculationService.monthsActiveInYear(
                    LocalDate.of(2020, 1, 1), LocalDate.of(2030, 12, 31), 2026)).isEqualTo(12);
        }

        @Test
        @DisplayName("Début en cours d'année")
        void partialStart() {
            assertThat(TresorerieCalculationService.monthsActiveInYear(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2030, 12, 31), 2026)).isEqualTo(9);
        }

        @Test
        @DisplayName("Fin en cours d'année")
        void partialEnd() {
            assertThat(TresorerieCalculationService.monthsActiveInYear(
                    LocalDate.of(2020, 1, 1), LocalDate.of(2026, 6, 30), 2026)).isEqualTo(6);
        }

        @Test
        @DisplayName("Année hors plage = 0 mois")
        void outsideRange() {
            assertThat(TresorerieCalculationService.monthsActiveInYear(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2030, 12, 31), 2025)).isEqualTo(0);
        }

        @Test
        @DisplayName("Null start ou end = 0 mois")
        void nullDates() {
            assertThat(TresorerieCalculationService.monthsActiveInYear(null, LocalDate.of(2030, 12, 31), 2026)).isEqualTo(0);
            assertThat(TresorerieCalculationService.monthsActiveInYear(LocalDate.of(2026, 1, 1), null, 2026)).isEqualTo(0);
        }
    }
}
