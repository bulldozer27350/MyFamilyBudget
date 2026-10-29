package com.moe.myfamilybudget.application.settings;

import java.util.Optional;

import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;

/**
 * Port de persistance des hypothèses de l'analyse des prêts modifiées par l'utilisateur.
 *
 * <p>R-86 (SILO-317) : port créé pour permettre à {@code DefaultLoanMarketUseCase} (module
 * {@code application}) d'accéder aux paramètres des prêts sans dépendre du module {@code server}.
 * L'implémentation JPA ({@code JpaLoanAdviceSettingsStore} dans {@code server.internal.calculation})
 * reste en place jusqu'à R-02 (sortie des stores de paramètres Crédit vers
 * {@code credit-api}/{@code credit-core}).
 *
 * <p>Ce port n'expose pas de méthode {@code save} : la validation est portée par
 * {@link LoanAdviceSettingsPort#save(LoanAdviceParameters)}, laissant la logique métier de
 * validation dans {@code LoanAdviceSettingsService}.
 */
public interface LoanAdviceSettingsPort {

    /** Hypothèses enregistrées, ou vide s'il n'y en a pas (ou si elles sont illisibles). */
    Optional<LoanAdviceParameters> load();

    /** Enregistre de nouvelles hypothèses (validation préalable par l'appelant). */
    void save(LoanAdviceParameters parameters);

    /** Valeurs par défaut (sans taux de marché). */
    default LoanAdviceParameters defaults() {
        return LoanAdviceParameters.defaults(null);
    }

    /** Hypothèses en vigueur : enregistrées si elles existent, sinon les valeurs par défaut. */
    default LoanAdviceParameters current() {
        return load().orElseGet(this::defaults);
    }
}
