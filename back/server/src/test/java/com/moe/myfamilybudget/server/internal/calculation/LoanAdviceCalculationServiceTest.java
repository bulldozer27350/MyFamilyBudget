package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.LoanItem;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RenegotiationVerdict;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RepayVerdict;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

/**
 * Les valeurs attendues ont été calculées indépendamment (script Python reproduisant
 * projectLoanCrdToDate() de calculations.js et l'amortissement mensuel), pas déduites du code testé.
 */
class LoanAdviceCalculationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    private static final double EUR = 0.02;

    private final LoanAdviceCalculationService service = new LoanAdviceCalculationService();

    private static final List<AssetCategoryModel> CATEGORIES = List.of(
            new AssetCategoryModel("c1", "💶", "Livrets", "cash"),
            new AssetCategoryModel("c2", "🛡️", "Assurance-vie euros", "fondsEuros"),
            new AssetCategoryModel("c3", "📈", "PEA", "actions"));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Prêt de référence : 150 000 € au 1er septembre 2026, 5 %, mensualité 1 000 € dont 40 € d'assurance. */
    private static LoanModel loanA() {
        return new LoanModel("a", "Prêt A", bd("150000"), bd("0.05"), bd("1000"), bd("40"), "2026-09-01", null);
    }

    private static PlacementModel placement(String label, String category, String rateCorr) {
        return new PlacementModel("p-" + label, label, category, bd("10000"), "2026-09-01", bd("0"), null, null,
                bd("0.01"), bd(rateCorr), bd("0.05"), false, "");
    }

    private LoanAdviceResultModel run(List<LoanModel> loans, List<PlacementModel> placements, String marketRate) {
        return runWith(loans, placements, LoanAdviceParameters.defaults(marketRate == null ? null : bd(marketRate)));
    }

    private LoanAdviceResultModel runWith(List<LoanModel> loans, List<PlacementModel> placements, LoanAdviceParameters p) {
        return service.compute(loans, placements, CATEGORIES, p, TODAY);
    }

    // ------------------------------------------------------------------ CRD projeté

    @Test
    @DisplayName("projectCrd() : une échéance de septembre appliquée sur un prêt référencé au 1er septembre")
    void projectCrdSingleStep() {
        // intérêts 150000 * 5 % / 12 = 625 ; capital amorti = 960 - 625 = 335
        assertEquals(149665.00, LoanAdviceCalculationService.projectCrd(loanA(), TODAY), EUR);
    }

    @Test
    @DisplayName("projectCrd() : reproduit projectLoanCrdToDate du front sur 21 mois")
    void projectCrdMatchesFrontOnSeveralMonths() {
        LoanModel loan = new LoanModel("x", "x", bd("200000"), bd("0.03"), bd("1000"), bd("0"), "2025-01-01", null);
        assertEquals(189233.30, LoanAdviceCalculationService.projectCrd(loan, TODAY), EUR);
    }

    @Test
    @DisplayName("projectCrd() : le capital est soldé une fois la date de fin atteinte")
    void projectCrdZeroAfterEndDate() {
        LoanModel loan = new LoanModel("x", "x", bd("100000"), bd("0.03"), bd("1000"), bd("0"), "2026-01-01", "2026-06-01");
        assertEquals(0, LoanAdviceCalculationService.projectCrd(loan, TODAY), EUR);
    }

    @Test
    @DisplayName("Un prêt soldé ou sans capital est exclu de l'analyse")
    void settledLoansAreSkipped() {
        LoanModel settled = new LoanModel("s", "Soldé", bd("100000"), bd("0.03"), bd("1000"), bd("0"), "2026-01-01", "2026-06-01");
        LoanModel empty = new LoanModel("e", "Vide", bd("0"), bd("0.03"), bd("1000"), bd("0"), null, null);

        LoanAdviceResultModel result = run(List.of(settled, empty), List.of(), null);

        assertTrue(result.loans().isEmpty());
    }

    @Test
    @DisplayName("Listes nulles : aucun prêt, pas d'exception")
    void nullLists() {
        LoanAdviceResultModel result = service.compute(null, null, null, LoanAdviceParameters.defaults(null), TODAY);

        assertTrue(result.loans().isEmpty());
    }

    // ------------------------------------------------------------------ échéancier et IRA

    @Test
    @DisplayName("Durée restante, intérêts restants et IRA du prêt de référence")
    void remainingScheduleAndIndemnity() {
        LoanItem item = run(List.of(loanA()), List.of(), null).loans().get(0);

        assertEquals(149665.00, item.crd().doubleValue(), EUR);
        assertEquals(253, item.remainingMonths());
        assertEquals(92446.07, item.remainingInterest().doubleValue(), 0.05);
        // 5 % / 2 = 2,5 % du capital < plafond 3 % : 149665 * 2,5 %
        assertEquals(3741.63, item.earlyRepaymentIndemnity().doubleValue(), 0.02);
    }

    @Test
    @DisplayName("IRA plafonnée à 3 % du capital restant dû quand 6 mois d'intérêts dépassent ce plafond")
    void indemnityCappedAtThreePercent() {
        LoanModel loan = new LoanModel("h", "Taux élevé", bd("100000"), bd("0.08"), bd("1200"), bd("0"), "2026-09-01", null);

        LoanItem item = run(List.of(loan), List.of(), null).loans().get(0);

        // capital projeté 100000 - (1200 - 666,67) = 99466,67 ; 3 % de ce capital
        assertEquals(99466.67 * 0.03, item.earlyRepaymentIndemnity().doubleValue(), 0.05);
    }

    @Test
    @DisplayName("Prêt qui ne s'amortit pas et sans date de fin : durée restante indéterminée")
    void nonAmortizingWithoutEndDate() {
        LoanModel loan = new LoanModel("n", "In fine", bd("200000"), bd("0.06"), bd("500"), bd("0"), "2026-09-01", null);

        LoanItem item = run(List.of(loan), List.of(), "0.03").loans().get(0);

        assertNull(item.remainingMonths());
        assertNull(item.remainingInterest());
        assertEquals(RenegotiationVerdict.NON_ELIGIBLE, item.renegotiation().verdict());
    }

    // ------------------------------------------------------------------ remboursement anticipé

    @Test
    @DisplayName("Prêt à 5 % contre un livret à 2 % : REMBOURSER")
    void repayWhenLoanCostsMoreThanCashYield() {
        LoanItem item = run(List.of(loanA()), List.of(placement("Livret A", "Livrets", "0.02")), null).loans().get(0);

        assertEquals(RepayVerdict.REMBOURSER, item.repayment().verdict());
        // 5 % + assurance 40 * 12 / 149665
        assertEquals(0.053207, item.repayment().loanEffectiveCost().doubleValue(), 1e-5);
        assertEquals(0.02, item.repayment().alternativeNetYield().doubleValue(), 1e-9);
        assertEquals("Livret A", item.repayment().alternativeLabel());
        assertTrue(item.repayment().annualSaving().doubleValue() > 4000);
    }

    @Test
    @DisplayName("Prêt à 5 % contre un livret à 6 % : CONSERVER")
    void keepWhenSavingsYieldMore() {
        LoanItem item = run(List.of(loanA()), List.of(placement("Livret", "Livrets", "0.06")), null).loans().get(0);

        assertEquals(RepayVerdict.CONSERVER, item.repayment().verdict());
    }

    @Test
    @DisplayName("Écart inférieur à la marge : NEUTRE")
    void neutralWhenGapBelowMargin() {
        LoanItem item = run(List.of(loanA()), List.of(placement("Livret", "Livrets", "0.052")), null).loans().get(0);

        assertEquals(RepayVerdict.NEUTRE, item.repayment().verdict());
    }

    @Test
    @DisplayName("Le rendement d'un fonds en euros est pris net de PFU : 6 % brut = 4,2 % net, donc REMBOURSER")
    void fondsEurosYieldIsNetOfFlatTax() {
        LoanItem item = run(List.of(loanA()), List.of(placement("AV euros", "Assurance-vie euros", "0.06")), null)
                .loans().get(0);

        assertEquals(0.042, item.repayment().alternativeNetYield().doubleValue(), 1e-9);
        assertEquals(RepayVerdict.REMBOURSER, item.repayment().verdict());
    }

    @Test
    @DisplayName("Les placements risqués (actions) ne sont pas une alternative au remboursement : INCONNU")
    void riskyPlacementsAreNotAnAlternative() {
        LoanItem item = run(List.of(loanA()), List.of(placement("PEA", "PEA", "0.10")), null).loans().get(0);

        assertEquals(RepayVerdict.INCONNU, item.repayment().verdict());
        assertNull(item.repayment().alternativeNetYield());
    }

    @Test
    @DisplayName("Le meilleur rendement net parmi les placements liquides est retenu")
    void bestNetLiquidAlternativeIsChosen() {
        List<PlacementModel> placements = List.of(
                placement("Livret bas", "Livrets", "0.015"),
                placement("Livret haut", "Livrets", "0.025"),
                placement("PEA", "PEA", "0.09"));

        LoanItem item = run(List.of(loanA()), placements, null).loans().get(0);

        assertEquals("Livret haut", item.repayment().alternativeLabel());
    }

    @Test
    @DisplayName("Une IRA non amortie avant la fin du prêt ramène le verdict à NEUTRE")
    void indemnityNotRecoveredBeforeEnd() {
        LoanModel loan = new LoanModel("e", "Bientôt soldé", bd("100000"), bd("0.06"), bd("35000"), bd("0"),
                "2026-09-01", "2026-12-01");

        LoanItem item = run(List.of(loan), List.of(placement("Livret", "Livrets", "0.03")), null).loans().get(0);

        assertEquals(2, item.remainingMonths());
        assertEquals(RepayVerdict.NEUTRE, item.repayment().verdict());
        // 1 965 € d'indemnité pour 1 965 € d'économie par an : 12 mois (l'arrondi flottant peut donner 13)
        assertTrue(item.repayment().indemnityPaybackMonths() >= 12);
    }

    // ------------------------------------------------------------------ renégociation

    private static LoanModel loanR() {
        return new LoanModel("r", "Résidence", bd("250000"), bd("0.045"), bd("1450"), bd("50"), "2026-09-01", null);
    }

    @Test
    @DisplayName("Sans taux de marché, la renégociation est INCONNU")
    void renegotiationUnknownWithoutMarketRate() {
        LoanItem item = run(List.of(loanR()), List.of(), null).loans().get(0);

        assertEquals(RenegotiationVerdict.INCONNU, item.renegotiation().verdict());
    }

    @Test
    @DisplayName("Taux du prêt proche du marché : TAUX_PROCHE_MARCHE")
    void renegotiationCloseToMarket() {
        LoanItem item = run(List.of(loanR()), List.of(), "0.043").loans().get(0);

        assertEquals(RenegotiationVerdict.TAUX_PROCHE_MARCHE, item.renegotiation().verdict());
    }

    @Test
    @DisplayName("Capital restant dû sous le seuil : NON_ELIGIBLE")
    void renegotiationNotEligibleForSmallLoans() {
        LoanModel small = new LoanModel("s", "Petit prêt", bd("30000"), bd("0.05"), bd("400"), bd("0"), "2026-09-01", null);

        LoanItem item = run(List.of(small), List.of(), "0.03").loans().get(0);

        assertEquals(RenegotiationVerdict.NON_ELIGIBLE, item.renegotiation().verdict());
    }

    @Test
    @DisplayName("4,5 % contre 3 % de marché sur 295 mois restants : RENEGOCIER, économie nette d'environ 52 700 €")
    void renegotiationWorthwhile() {
        LoanItem item = run(List.of(loanR()), List.of(), "0.03").loans().get(0);

        assertEquals(295, item.remainingMonths());
        assertEquals(RenegotiationVerdict.RENEGOCIER, item.renegotiation().verdict());
        assertEquals(0.015, item.renegotiation().gapRate().doubleValue(), 1e-9);
        assertEquals(1196.82, item.renegotiation().newMonthly().doubleValue(), 0.02);
        assertEquals(203.18, item.renegotiation().monthlyGain().doubleValue(), 0.02);
        assertEquals(59810.23, item.renegotiation().grossSaving().doubleValue(), 0.5);
        // IRA 5614,59 + frais fixes 1500
        assertEquals(7114.59, item.renegotiation().costs().doubleValue(), 0.05);
        assertEquals(52695.64, item.renegotiation().netSaving().doubleValue(), 0.5);
        assertEquals(36, item.renegotiation().paybackMonths());
    }

    @Test
    @DisplayName("Des frais trop élevés rendent la renégociation NON_RENTABLE")
    void renegotiationNotProfitableWithHighCosts() {
        LoanAdviceParameters expensive = new LoanAdviceParameters(bd("0.03"), bd("0.005"), bd("0.007"),
                bd("70000"), 84, bd("100000"), bd("0.30"));

        LoanItem item = runWith(List.of(loanR()), List.of(), expensive).loans().get(0);

        assertEquals(RenegotiationVerdict.NON_RENTABLE, item.renegotiation().verdict());
        assertTrue(item.renegotiation().netSaving().signum() < 0);
    }

    // ------------------------------------------------------------------ paramètres et hypothèses

    @Test
    @DisplayName("Un taux de marché hors plage est ignoré et signalé dans les notes")
    void implausibleMarketRateIsIgnored() {
        LoanAdviceResultModel result = run(List.of(loanR()), List.of(), "0.5");

        assertNull(result.marketRateUsed());
        assertEquals(RenegotiationVerdict.INCONNU, result.loans().get(0).renegotiation().verdict());
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("ignoré")));
    }

    @Test
    @DisplayName("Les hypothèses par défaut sont renvoyées avec le résultat")
    void assumptionsAreExposed() {
        LoanAdviceResultModel result = run(List.of(loanR()), List.of(), "0.03");

        assertNotNull(result.assumptions());
        assertEquals("immobilier", result.assumptions().loanType());
        assertEquals(0.007, result.assumptions().renegotiationMinGapRate().doubleValue(), 1e-9);
        assertEquals(84, result.assumptions().renegotiationMinRemainingMonths());
        assertEquals(0.03, result.marketRateUsed().doubleValue(), 1e-9);
    }

    @Test
    @DisplayName("parseDate() accepte YYYY-MM-DD et YYYY-MM, rejette le reste")
    void parseDateFormats() {
        assertEquals(LocalDate.of(2026, 9, 1), LoanAdviceCalculationService.parseDate("2026-09-01"));
        assertEquals(LocalDate.of(2026, 9, 1), LoanAdviceCalculationService.parseDate("2026-09"));
        assertNull(LoanAdviceCalculationService.parseDate("demain"));
        assertNull(LoanAdviceCalculationService.parseDate(null));
        assertNull(LoanAdviceCalculationService.parseDate(" "));
    }
}
