package com.moe.myfamilybudget.application.usecase.loanmarket;

/**
 * Verdict sur la renégociation d'un prêt (R-86, SILO-317).
 *
 * <p>Miroir applicatif de {@code domain.credit.model.LoanAdviceResultModel.RenegotiationVerdict}.
 */
public enum LoanRenegotiationVerdict {
    /** Économie nette positive après indemnités et frais. */
    RENEGOCIER,
    /** L'écart de taux existe mais les indemnités et frais l'annulent. */
    NON_RENTABLE,
    /** Taux du prêt proche ou inférieur au taux de marché. */
    TAUX_PROCHE_MARCHE,
    /** Capital restant dû ou durée restante sous les seuils habituels. */
    NON_ELIGIBLE,
    /** Taux de marché non renseigné. */
    INCONNU
}
