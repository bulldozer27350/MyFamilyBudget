package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat de l'analyse des prêts (rembourser ? renégocier ?). Tous les taux sont des fractions
 * (0,035 pour 3,5 %), tous les montants sont en euros.
 *
 * @param marketRateUsed  taux de marché retenu pour l'analyse de renégociation, null si non renseigné
 * @param assumptions     hypothèses appliquées, à afficher à côté des résultats
 * @param loans           un élément par prêt en cours (capital restant dû positif)
 * @param notes           limites générales de l'estimation
 */
public record LoanAdviceResultModel(
        BigDecimal marketRateUsed,
        Assumptions assumptions,
        List<LoanItem> loans,
        List<String> notes) {

    /** Verdict sur le remboursement anticipé. */
    public enum RepayVerdict {
        /** Le coût du prêt dépasse nettement le rendement net d'un placement sans risque. */
        REMBOURSER,
        /** Écart trop faible pour trancher sur les seuls taux. */
        NEUTRE,
        /** Le prêt coûte nettement moins cher que ce que rapporte l'épargne. */
        CONSERVER,
        /** Aucun placement liquide avec un taux renseigné : comparaison impossible. */
        INCONNU
    }

    /** Verdict sur la renégociation. */
    public enum RenegotiationVerdict {
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

    /** Hypothèses de l'analyse (paramétrables). */
    public record Assumptions(
            String loanType,
            BigDecimal repayMarginRate,
            BigDecimal renegotiationMinGapRate,
            BigDecimal renegotiationMinCrd,
            Integer renegotiationMinRemainingMonths,
            BigDecimal renegotiationFixedCosts,
            BigDecimal flatTaxRate) {
    }

    /** Analyse d'un prêt. */
    public record LoanItem(
            String id,
            String label,
            BigDecimal crd,
            BigDecimal rate,
            BigDecimal monthly,
            BigDecimal insurance,
            Integer remainingMonths,
            BigDecimal remainingInterest,
            BigDecimal earlyRepaymentIndemnity,
            RepaymentAdvice repayment,
            RenegotiationAdvice renegotiation) {
    }

    /**
     * Comparaison coût du prêt / rendement net du meilleur placement liquide sans risque.
     *
     * @param loanEffectiveCost      taux du prêt majoré du coût annuel de l'assurance rapporté au capital
     * @param alternativeNetYield    meilleur rendement net d'impôt parmi les placements liquides, null si inconnu
     * @param alternativeLabel       libellé du placement retenu
     * @param annualSaving           économie annuelle estimée si le prêt était soldé (peut être négative)
     * @param indemnityPaybackMonths mois nécessaires pour amortir l'indemnité de remboursement anticipé
     */
    public record RepaymentAdvice(
            RepayVerdict verdict,
            BigDecimal loanEffectiveCost,
            BigDecimal alternativeNetYield,
            String alternativeLabel,
            BigDecimal annualSaving,
            Integer indemnityPaybackMonths,
            String reason) {
    }

    /**
     * Estimation d'une renégociation à durée restante identique.
     *
     * @param gapRate         taux du prêt moins taux de marché
     * @param newMonthly      mensualité hors assurance au taux de marché
     * @param monthlyGain     économie mensuelle sur la mensualité hors assurance
     * @param grossSaving     intérêts restants actuels moins intérêts au taux de marché
     * @param costs           indemnité de remboursement anticipé + frais fixes estimés
     * @param netSaving       économie nette (grossSaving - costs)
     * @param paybackMonths   mois pour amortir les coûts grâce à l'économie mensuelle
     */
    public record RenegotiationAdvice(
            RenegotiationVerdict verdict,
            BigDecimal gapRate,
            BigDecimal newMonthly,
            BigDecimal monthlyGain,
            BigDecimal grossSaving,
            BigDecimal costs,
            BigDecimal netSaving,
            Integer paybackMonths,
            String reason) {
    }
}
