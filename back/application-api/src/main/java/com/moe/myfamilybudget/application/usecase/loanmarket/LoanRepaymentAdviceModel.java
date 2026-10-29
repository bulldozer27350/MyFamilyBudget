package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Comparaison coût du prêt / rendement net du meilleur placement liquide sans risque (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel.RepaymentAdvice}.
 *
 * @param loanEffectiveCost      taux du prêt majoré du coût annuel de l'assurance rapporté au capital
 * @param alternativeNetYield    meilleur rendement net d'impôt parmi les placements liquides, null si inconnu
 * @param alternativeLabel       libellé du placement retenu
 * @param annualSaving           économie annuelle estimée si le prêt était soldé (peut être négative)
 * @param indemnityPaybackMonths mois nécessaires pour amortir l'indemnité de remboursement anticipé
 */
public record LoanRepaymentAdviceModel(
        LoanRepayVerdict verdict,
        BigDecimal loanEffectiveCost,
        BigDecimal alternativeNetYield,
        String alternativeLabel,
        BigDecimal annualSaving,
        Integer indemnityPaybackMonths,
        String reason) {
}
