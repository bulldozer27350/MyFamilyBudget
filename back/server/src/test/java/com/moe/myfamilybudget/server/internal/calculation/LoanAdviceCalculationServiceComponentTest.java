package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.LoanItem;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RepayVerdict;

/**
 * Tests de composant du moteur d'analyse des prêts (RF-803) : construits uniquement avec
 * {@link LoanAdviceInput}, {@link LoanInput} et {@link LiquidPlacementAlternative}, sans
 * {@code BudgetDataModel}, sans modèle persistant, sans factory et sans contexte Spring. Les
 * valeurs attendues sont celles, calculées indépendamment, de {@code LoanAdviceCalculationServiceTest}.
 */
class LoanAdviceCalculationServiceComponentTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 19);
    private static final double EUR = 0.02;

    private final LoanAdviceCalculationService service = new LoanAdviceCalculationService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Prêt de référence : 150 000 € au 1er septembre 2026, 5 %, mensualité 1 000 € dont 40 € d'assurance. */
    private static LoanInput loanA() {
        return new LoanInput("a", "Prêt A", bd("150000"), bd("0.05"), bd("1000"), bd("40"), "2026-09-01", null, null);
    }

    /** Alternative dont le bucket est déjà résolu (rôle de l'appelant, pas du moteur). */
    private static LiquidPlacementAlternative alternative(String label, String bucket, String rateCorr) {
        return new LiquidPlacementAlternative(label, bd(rateCorr), bucket);
    }

    private LoanItem runOne(List<LiquidPlacementAlternative> alternatives) {
        LoanAdviceResultModel result = service.compute(new LoanAdviceInput(List.of(loanA()), alternatives,
                LoanAdviceParameters.defaults(null), null, TODAY));
        return result.loans().get(0);
    }

    @Test
    @DisplayName("projectCrd() : une échéance de septembre appliquée sur un prêt référencé au 1er septembre")
    void projectCrdSingleStep() {
        assertEquals(149665.00, LoanAdviceCalculationService.projectCrd(loanA(), TODAY), EUR);
    }

    @Test
    @DisplayName("Entrées vides : aucun prêt, pas d'exception")
    void emptyInput() {
        LoanAdviceResultModel result = service.compute(
                new LoanAdviceInput(null, null, LoanAdviceParameters.defaults(null), null, TODAY));

        assertTrue(result.loans().isEmpty());
    }

    @Test
    @DisplayName("Durée restante, intérêts restants et IRA du prêt de référence")
    void remainingScheduleAndIndemnity() {
        LoanItem item = runOne(List.of());

        assertEquals(149665.00, item.crd().doubleValue(), EUR);
        assertEquals(253, item.remainingMonths());
        assertEquals(92446.07, item.remainingInterest().doubleValue(), 0.05);
        assertEquals(3741.63, item.earlyRepaymentIndemnity().doubleValue(), 0.02);
    }

    @Test
    @DisplayName("Prêt à 5 % contre un livret à 2 % : REMBOURSER")
    void repayWhenLoanCostsMoreThanCashYield() {
        LoanItem item = runOne(List.of(alternative("Livret A", "cash", "0.02")));

        assertEquals(RepayVerdict.REMBOURSER, item.repayment().verdict());
        assertEquals(0.053207, item.repayment().loanEffectiveCost().doubleValue(), 1e-5);
        assertEquals(0.02, item.repayment().alternativeNetYield().doubleValue(), 1e-9);
        assertEquals("Livret A", item.repayment().alternativeLabel());
    }

    @Test
    @DisplayName("Prêt à 5 % contre un livret à 6 % : CONSERVER")
    void keepWhenSavingsYieldMore() {
        LoanItem item = runOne(List.of(alternative("Livret", "cash", "0.06")));

        assertEquals(RepayVerdict.CONSERVER, item.repayment().verdict());
    }

    @Test
    @DisplayName("Le rendement d'un fonds en euros est pris net de PFU : 6 % brut = 4,2 % net, donc REMBOURSER")
    void fondsEurosYieldIsNetOfFlatTax() {
        LoanItem item = runOne(List.of(alternative("AV euros", "fondsEuros", "0.06")));

        assertEquals(0.042, item.repayment().alternativeNetYield().doubleValue(), 1e-9);
        assertEquals(RepayVerdict.REMBOURSER, item.repayment().verdict());
    }

    @Test
    @DisplayName("Un bucket risqué (actions) n'est pas une alternative au remboursement : INCONNU")
    void riskyBucketIsNotAnAlternative() {
        LoanItem item = runOne(List.of(alternative("PEA", "actions", "0.10")));

        assertEquals(RepayVerdict.INCONNU, item.repayment().verdict());
        assertNull(item.repayment().alternativeNetYield());
    }
}
