package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Analyse d'un prêt (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel.LoanItem}.
 */
public record LoanItemModel(
        String id,
        String label,
        BigDecimal crd,
        BigDecimal rate,
        BigDecimal monthly,
        BigDecimal insurance,
        Integer remainingMonths,
        BigDecimal remainingInterest,
        BigDecimal earlyRepaymentIndemnity,
        LoanRepaymentAdviceModel repayment,
        LoanRenegotiationAdviceModel renegotiation) {
}
