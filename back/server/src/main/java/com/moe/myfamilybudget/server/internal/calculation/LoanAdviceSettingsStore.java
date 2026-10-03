package com.moe.myfamilybudget.server.internal.calculation;

import java.util.Optional;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;

/**
 * Persistance des hypothèses de l'analyse des prêts modifiées par l'utilisateur.
 */
public interface LoanAdviceSettingsStore {

    /** Hypothèses enregistrées, ou vide s'il n'y en a pas (ou si elles sont illisibles). */
    Optional<LoanAdviceParameters> load();

    void save(LoanAdviceParameters parameters);
}
