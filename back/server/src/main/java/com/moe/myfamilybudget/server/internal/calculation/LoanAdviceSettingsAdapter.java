package com.moe.myfamilybudget.server.internal.calculation;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.application.settings.LoanAdviceSettingsPort;
import com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters;

/**
 * Adaptateur qui expose {@link LoanAdviceSettingsPort} (port de {@code application.settings})
 * en déléguant à {@link LoanAdviceSettingsStore} (persistance) et en délégant la validation à
 * {@link LoanAdviceSettingsService} (R-86, SILO-317).
 *
 * <p>Cet adaptateur reste dans {@code server.internal.calculation} jusqu'à R-02 (sortie des
 * stores de paramètres Crédit vers {@code credit-api}/{@code credit-core}).
 */
@Component
class LoanAdviceSettingsAdapter implements LoanAdviceSettingsPort {

    private final LoanAdviceSettingsStore store;
    private final LoanAdviceSettingsService settingsService;

    LoanAdviceSettingsAdapter(LoanAdviceSettingsStore store, LoanAdviceSettingsService settingsService) {
        this.store = store;
        this.settingsService = settingsService;
    }

    @Override
    public Optional<LoanAdviceParameters> load() {
        return store.load();
    }

    /**
     * Valide puis enregistre les hypothèses.
     *
     * @throws IllegalArgumentException si une valeur est absente ou hors plage (traduit en 400)
     */
    @Override
    public void save(LoanAdviceParameters parameters) {
        LoanAdviceSettingsService.validate(parameters);
        store.save(parameters);
    }
}
