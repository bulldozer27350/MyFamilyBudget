package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.budget.CashflowYearModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.budget.TripleAmountModel;

/**
 * RF-902 : tests de composant du moteur d'apercu financier global, construits uniquement avec
 * {@link OverviewInput} -- aucun {@code BudgetDataModel}, aucune factory et aucun contexte Spring.
 * Chaque scenario construit un {@link OverviewInput} minimal et verifie le comportement de
 * {@link OverviewCalculationService#computeOverview(OverviewInput)}.
 */
@DisplayName("OverviewCalculationService - tests de composant sur OverviewInput")
class OverviewCalculationServiceComponentTest {

    private final OverviewCalculationService service = new OverviewCalculationService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static CashflowYearModel cashflow(int year, String income, String charges, String net, String balance) {
        return new CashflowYearModel(year,
                bd(income), BigDecimal.ZERO, BigDecimal.ZERO,
                bd(charges), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                bd(net), bd(balance));
    }

    private static TreasuryProjection treasury(List<Integer> years, List<CashflowYearModel> cashflow) {
        return new TreasuryProjection(years, cashflow, List.of(), List.of());
    }

    private static PatrimoineProjection patrimoine(List<Integer> years,
                                                    String label, boolean excluded,
                                                    List<String[]> rows,
                                                    String currentBalance) {
        List<PatrimoineYearModel> rowModels = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            rowModels.add(new PatrimoineYearModel(years.get(i), bd(r[0]), bd(r[1]), bd(r[2])));
        }
        PatrimoinePerPlacementModel pp = new PatrimoinePerPlacementModel(label, rowModels);
        PatrimoineYearModel total = rowModels.isEmpty()
                ? new PatrimoineYearModel(years.isEmpty() ? 0 : years.get(0), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                : rowModels.get(0);
        PatrimoineProjectionsModel projections = new PatrimoineProjectionsModel(List.of(pp), List.of(total));
        Set<String> excluded_ = excluded ? Set.of(label) : Set.of();
        return new PatrimoineProjection(projections, bd(currentBalance), excluded_);
    }

    private static RetirementProjection emptyRetirement() {
        return new RetirementProjection(List.of());
    }

    private static RetirementProjection retirement(String pensionMensuelle) {
        RetirementProjectionModel person = new RetirementProjectionModel(
                62, 168, 168, 168, false,
                bd("0.5"), BigDecimal.ZERO, BigDecimal.ZERO,
                bd("25000"), BigDecimal.ZERO,
                bd("15000"), bd("300"),
                bd("1.9"), bd("5000"),
                bd("20000"), bd(pensionMensuelle));
        return new RetirementProjection(List.of(person));
    }

    private static OverviewParameters params(int retireYear, int currentYear, boolean constantEuros, String inflation) {
        return new OverviewParameters(retireYear, bd(inflation), null, currentYear, constantEuros);
    }

    private static OverviewInput input(TreasuryProjection treasury,
                                        PatrimoineProjection patrimoine,
                                        RetirementProjection retirement,
                                        RealEstateProjection realEstate,
                                        OverviewParameters parameters) {
        return new OverviewInput(treasury, patrimoine, retirement,
                new TaxProjection(List.of()), realEstate, parameters);
    }

    // -----------------------------------------------------------------------
    // 1. Validation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Validation de l'entree")
    class ValidationTests {

        @Test
        @DisplayName("Rejette un OverviewInput null")
        void rejectNullInput() {
            assertThatThrownBy(() -> service.computeOverview(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // -----------------------------------------------------------------------
    // 2. Euros courants
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Euros courants (useConstantEuros = false)")
    class CurrentEurosTests {

        @Test
        @DisplayName("patrimoineActuel reflete currentTotalBalance du patrimoine")
        void patrimoineActuel() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2027, 2028, 2029, 2030);
            List<CashflowYearModel> cashflows = List.of(cashflow(2026, "50000", "40000", "10000", "10000"));
            PatrimoineProjection pat = patrimoine(years, "Livret A", false,
                    List.of(new String[]{"10000", "10000", "10000"},
                            new String[]{"11000", "11000", "11000"},
                            new String[]{"12000", "12000", "12000"},
                            new String[]{"13000", "13000", "13000"},
                            new String[]{"14000", "14000", "14000"}),
                    "75000");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.02"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.patrimoineActuel()).isEqualByComparingTo("75000");
        }

        @Test
        @DisplayName("fluxNetActuel est issu du cashflow de l'annee courante")
        void fluxNetActuel() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "50000", "40000", "9500", "9500"),
                    cashflow(2030, "30000", "20000", "10000", "50000"));
            PatrimoineProjection pat = patrimoine(years, "PEA", false,
                    List.of(new String[]{"100000", "100000", "100000"},
                            new String[]{"120000", "120000", "120000"}),
                    "100000");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.fluxNetActuel()).isEqualByComparingTo("9500");
        }

        @Test
        @DisplayName("retireYear est bien propage dans le resultat")
        void retireYearPropagated() {
            int retireYear = 2035;
            List<Integer> years = List.of(2026, 2035);
            List<CashflowYearModel> cashflows = List.of(cashflow(2026, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "X", false,
                    List.of(new String[]{"0", "0", "0"}, new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.retireYear()).isEqualTo(retireYear);
            assertThat(result.useConstantEuros()).isFalse();
        }

        @Test
        @DisplayName("totalPensions est la somme des pensions mensuelles de toutes les personnes")
        void totalPensions_sumOfPeople() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "24000", "0", "0"));
            RetirementProjectionModel person1 = new RetirementProjectionModel(
                    62, 168, 168, 168, false,
                    bd("0.5"), BigDecimal.ZERO, BigDecimal.ZERO,
                    bd("25000"), BigDecimal.ZERO, bd("15000"),
                    bd("300"), bd("1.9"), bd("5000"),
                    bd("20000"), bd("1500"));
            RetirementProjectionModel person2 = new RetirementProjectionModel(
                    62, 168, 168, 168, false,
                    bd("0.5"), BigDecimal.ZERO, BigDecimal.ZERO,
                    bd("25000"), BigDecimal.ZERO, bd("15000"),
                    bd("300"), bd("1.9"), bd("5000"),
                    bd("20000"), bd("800"));
            RetirementProjection ret = new RetirementProjection(List.of(person1, person2));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"0", "0", "0"}, new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, ret,
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.totalPensions()).isEqualByComparingTo("2300");
        }
    }

    // -----------------------------------------------------------------------
    // 3. Euros constants
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Euros constants (useConstantEuros = true)")
    class ConstantEurosTests {

        @Test
        @DisplayName("retireCharges est deflate par l'inflation sur les annees jusqu'a la retraite")
        void retireChargesDeflated() {
            int retireYear = 2028;
            List<Integer> years = List.of(2026, 2027, 2028);
            BigDecimal annualCharges = bd("12000");
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "50000", "20000", "30000", "30000"),
                    cashflow(2027, "50000", "20000", "30000", "60000"),
                    cashflow(2028, "50000", annualCharges.toPlainString(), "38000", "98000"));
            PatrimoineProjection pat = patrimoine(years, "PEA", false,
                    List.of(new String[]{"100000", "100000", "100000"},
                            new String[]{"110000", "110000", "110000"},
                            new String[]{"120000", "120000", "120000"}),
                    "100000");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, true, "0.02"));

            OverviewResultModel result = service.computeOverview(in);

            double expectedDeflator = Math.pow(1.0 / 1.02, 2);
            double expected = 1000.0 * expectedDeflator;
            assertThat(result.retireCharges().doubleValue())
                    .isCloseTo(expected, within(0.01));
        }

        @Test
        @DisplayName("totalPensions est deflate en euros constants")
        void totalPensionsDeflated() {
            int retireYear = 2028;
            List<Integer> years = List.of(2026, 2027, 2028);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2027, "0", "0", "0", "0"),
                    cashflow(2028, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"0", "0", "0"},
                            new String[]{"0", "0", "0"},
                            new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, retirement("2000"),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, true, "0.02"));

            OverviewResultModel result = service.computeOverview(in);

            double deflator = Math.pow(1.0 / 1.02, 2);
            double expected = 2000.0 * deflator;
            assertThat(result.totalPensions().doubleValue())
                    .isCloseTo(expected, within(0.01));
        }

        @Test
        @DisplayName("realEstateAtRetire est incluse dans retirePatrimoine")
        void realEstateIncludedInRetirePatrimoine() {
            int retireYear = 2028;
            List<Integer> years = List.of(2026, 2027, 2028);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2027, "0", "0", "0", "0"),
                    cashflow(2028, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"30000", "40000", "50000"},
                            new String[]{"31000", "41000", "51000"},
                            new String[]{"32000", "42000", "52000"}),
                    "30000");
            RealEstateProjection re = new RealEstateProjection(bd("200000"), bd("180000"), List.of());
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(), re,
                    params(retireYear, 2026, true, "0.02"));

            OverviewResultModel result = service.computeOverview(in);

            double deflator = Math.pow(1.0 / 1.02, 2);
            double financialCorr = 42000.0 * deflator;
            double realEstateDeflated = 200000.0 * deflator;
            double expectedRetirePatrimoineCorr = financialCorr + realEstateDeflated;
            assertThat(result.retirePatrimoine().corr().doubleValue())
                    .isCloseTo(expectedRetirePatrimoineCorr, within(0.01));
        }
    }

    // -----------------------------------------------------------------------
    // 4. Regle des 4 %
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Regle des 4 % (fireRente et financialOnlyRente)")
    class FourPercentRuleTests {

        @Test
        @DisplayName("fireRente.corr = retirePatrimoine.corr * 4% / 12")
        void fireRenteFormula() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "PEA", false,
                    List.of(new String[]{"200000", "300000", "400000"},
                            new String[]{"220000", "330000", "440000"}),
                    "200000");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO),
                    params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            double expected = 330000.0 * 0.04 / 12.0;
            assertThat(result.fireRente().corr().doubleValue())
                    .isCloseTo(expected, within(0.01));
        }

        @Test
        @DisplayName("financialOnlyRente.corr exclut l'immobilier")
        void financialOnlyRenteExcludesRealEstate() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "PEA", false,
                    List.of(new String[]{"200000", "300000", "400000"},
                            new String[]{"220000", "330000", "440000"}),
                    "200000");
            RealEstateProjection re = new RealEstateProjection(bd("100000"), bd("90000"), List.of());
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(), re,
                    params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            double expectedFinancialOnly = 330000.0 * 0.04 / 12.0;
            assertThat(result.financialOnlyRente().corr().doubleValue())
                    .isCloseTo(expectedFinancialOnly, within(0.01));

            double expectedFire = 430000.0 * 0.04 / 12.0;
            assertThat(result.fireRente().corr().doubleValue())
                    .isCloseTo(expectedFire, within(0.01));
        }
    }

    // -----------------------------------------------------------------------
    // 5. Placements exclus
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Exclusion de placements de la retraite")
    class ExcludedPlacementsTests {

        @Test
        @DisplayName("Un placement exclu n'est pas compte dans financialOnlyPatrimoine")
        void excludedPlacementNotCounted() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "0", "0", "0"));
            PatrimoineYearModel row1a = new PatrimoineYearModel(2026, bd("40000"), bd("50000"), bd("60000"));
            PatrimoineYearModel row1b = new PatrimoineYearModel(2030, bd("41000"), bd("51000"), bd("61000"));
            PatrimoineYearModel row2a = new PatrimoineYearModel(2026, bd("80000"), bd("100000"), bd("120000"));
            PatrimoineYearModel row2b = new PatrimoineYearModel(2030, bd("82000"), bd("102000"), bd("122000"));
            PatrimoinePerPlacementModel pp1 = new PatrimoinePerPlacementModel("PEA", List.of(row1a, row1b));
            PatrimoinePerPlacementModel pp2 = new PatrimoinePerPlacementModel("RP", List.of(row2a, row2b));
            PatrimoineProjectionsModel projections = new PatrimoineProjectionsModel(
                    List.of(pp1, pp2), List.of(row1a));
            PatrimoineProjection pat = new PatrimoineProjection(projections, bd("150000"), Set.of("RP"));
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            double expectedFinancialOnly = 51000.0 * 0.04 / 12.0;
            assertThat(result.financialOnlyRente().corr().doubleValue())
                    .isCloseTo(expectedFinancialOnly, within(0.01));
        }
    }

    // -----------------------------------------------------------------------
    // 6. Cas limites
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Cas limites")
    class EdgeCasesTests {

        @Test
        @DisplayName("Cashflow vide -> fluxNetActuel et retireCharges valent zero")
        void emptyCashflow() {
            int retireYear = 2030;
            List<Integer> years = List.of();
            PatrimoineProjection pat = new PatrimoineProjection(
                    new PatrimoineProjectionsModel(List.of(), List.of()),
                    BigDecimal.ZERO, Set.of());
            OverviewInput in = input(treasury(years, List.of()), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.fluxNetActuel()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.retireCharges()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Aucune personne -> totalPensions vaut zero")
        void noPeople_zeroPensions() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "0", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"0", "0", "0"}, new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.totalPensions()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Annee de retraite absente du cashflow -> retireCharges vaut zero")
        void retireYearAbsentInCashflow() {
            int retireYear = 2040;
            List<Integer> years = List.of(2026, 2027);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "50000", "40000", "10000", "10000"),
                    cashflow(2027, "50000", "40000", "10000", "20000"));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"0", "0", "0"}, new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.retireCharges()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Annee courante absente -> fluxNetActuel pris sur le premier cashflow disponible")
        void currentYearAbsentFallsBackToFirst() {
            int retireYear = 2030;
            List<Integer> years = List.of(2027, 2028);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2027, "50000", "40000", "7777", "7777"),
                    cashflow(2028, "50000", "40000", "8888", "8888"));
            PatrimoineProjection pat = patrimoine(years, "A", false,
                    List.of(new String[]{"0", "0", "0"}, new String[]{"0", "0", "0"}), "0");
            OverviewInput in = input(treasury(years, cashflows), pat, emptyRetirement(),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.0"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.fluxNetActuel()).isEqualByComparingTo("7777");
        }

        @Test
        @DisplayName("useConstantEuros=false -> deflateur = 1, pas d'impact sur les montants")
        void noInflationDeflatorEqualsOne() {
            int retireYear = 2030;
            List<Integer> years = List.of(2026, 2030);
            List<CashflowYearModel> cashflows = List.of(
                    cashflow(2026, "0", "0", "0", "0"),
                    cashflow(2030, "0", "24000", "0", "0"));
            PatrimoineProjection pat = patrimoine(years, "PEA", false,
                    List.of(new String[]{"100000", "120000", "140000"},
                            new String[]{"110000", "130000", "150000"}),
                    "100000");
            OverviewInput in = input(treasury(years, cashflows), pat, retirement("2000"),
                    new RealEstateProjection(BigDecimal.ZERO), params(retireYear, 2026, false, "0.99"));

            OverviewResultModel result = service.computeOverview(in);

            assertThat(result.totalPensions()).isEqualByComparingTo("2000");
            assertThat(result.retireCharges()).isEqualByComparingTo("2000");
        }
    }
}