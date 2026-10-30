package com.moe.myfamilybudget.domain.credit.core;

import java.math.BigDecimal;

import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceSettingsService;
import com.moe.myfamilybudget.domain.credit.port.LoanAdviceSettingsStore;

/**
 * Hypothèses de l'analyse des prêts modifiables depuis l'application. Sans enregistrement, les
 * valeurs par défaut de {@link LoanAdviceParameters#defaults(BigDecimal)} s'appliquent.
 *
 * <p>Le taux de marché saisi ici est celui utilisé tant qu'aucune source automatique n'est branchée ;
 * un paramètre de requête peut toujours le remplacer ponctuellement (simulation).
 */
public class DefaultLoanAdviceSettingsService implements LoanAdviceSettingsService {

    private static final BigDecimal MAX_MARGIN_RATE = new BigDecimal("0.05");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");

    private final LoanAdviceSettingsStore store;

    public DefaultLoanAdviceSettingsService(LoanAdviceSettingsStore store) {
        this.store = store;
    }

    /** Hypothèses en vigueur : enregistrées si elles existent, sinon celles par défaut. */
    @Override
    public LoanAdviceParameters current() {
        return store.load().orElseGet(this::defaults);
    }

    /** Valeurs par défaut (sans taux de marché). */
    @Override
    public LoanAdviceParameters defaults() {
        return LoanAdviceParameters.defaults(null);
    }

    /**
     * Valide puis enregistre les hypothèses.
     *
     * @throws IllegalArgumentException si une valeur est absente ou hors plage (traduit en 400)
     */
    @Override
    public LoanAdviceParameters save(LoanAdviceParameters parameters) {
        validate(parameters);
        store.save(parameters);
        return parameters;
    }

    public static void validate(LoanAdviceParameters p) {
        BigDecimal market = p.marketRate();
        if (market != null && (market.signum() <= 0 || market.compareTo(LoanAdviceParameters.MAX_PLAUSIBLE_MARKET_RATE) > 0)) {
            throw new IllegalArgumentException("Le taux de marché doit être supérieur à 0 % et ne pas dépasser 30 %.");
        }
        requireRange(p.repayMarginRate(), BigDecimal.ZERO, MAX_MARGIN_RATE,
                "L'écart de remboursement doit être compris entre 0 % et 5 %.");
        requireRange(p.renegotiationMinGapRate(), BigDecimal.ZERO, MAX_MARGIN_RATE,
                "L'écart minimal de renégociation doit être compris entre 0 % et 5 %.");
        requireRange(p.renegotiationMinCrd(), BigDecimal.ZERO, MAX_AMOUNT,
                "Le capital restant dû minimal doit être compris entre 0 et 10 000 000 €.");
        if (p.renegotiationMinRemainingMonths() < 0 || p.renegotiationMinRemainingMonths() > 600) {
            throw new IllegalArgumentException("La durée restante minimale doit être comprise entre 0 et 600 mois.");
        }
        requireRange(p.renegotiationFixedCosts(), BigDecimal.ZERO, MAX_AMOUNT,
                "Les frais fixes de renégociation doivent être compris entre 0 et 10 000 000 €.");
        requireRange(p.flatTaxRate(), BigDecimal.ZERO, BigDecimal.ONE,
                "La fiscalité doit être comprise entre 0 % et 100 %.");
    }

    private static void requireRange(BigDecimal value, BigDecimal min, BigDecimal max, String message) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
