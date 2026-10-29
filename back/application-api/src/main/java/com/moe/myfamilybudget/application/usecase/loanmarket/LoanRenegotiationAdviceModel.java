package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Estimation d'une renégociation à durée restante identique (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel.RenegotiationAdvice}.
 *
 * @param gapRate       taux du prêt moins taux de marché
 * @param newMonthly    mensualité hors assurance au taux de marché
 * @param monthlyGain   économie mensuelle sur la mensualité hors assurance
 * @param grossSaving   intérêts restants actuels moins intérêts au taux de marché
 * @param costs         indemnité de remboursement anticipé + frais fixes estimés
 * @param netSaving     économie nette (grossSaving - costs)
 * @param paybackMonths mois pour amortir les coûts grâce à l'économie mensuelle
 */
public record LoanRenegotiationAdviceModel(
        LoanRenegotiationVerdict verdict,
        BigDecimal gapRate,
        BigDecimal newMonthly,
        BigDecimal monthlyGain,
        BigDecimal grossSaving,
        BigDecimal costs,
        BigDecimal netSaving,
        Integer paybackMonths,
        String reason) {
}
