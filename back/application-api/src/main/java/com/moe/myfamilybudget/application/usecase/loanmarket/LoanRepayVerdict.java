package com.moe.myfamilybudget.application.usecase.loanmarket;

import java.math.BigDecimal;

/**
 * Verdict sur le remboursement anticipé d'un prêt (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel.RepayVerdict}.
 */
public enum LoanRepayVerdict {
    /** Le coût du prêt dépasse nettement le rendement net d'un placement sans risque. */
    REMBOURSER,
    /** Écart trop faible pour trancher sur les seuls taux. */
    NEUTRE,
    /** Le prêt coûte nettement moins cher que ce que rapporte l'épargne. */
    CONSERVER,
    /** Aucun placement liquide avec un taux renseigné : comparaison impossible. */
    INCONNU
}
