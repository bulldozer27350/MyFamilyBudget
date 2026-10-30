package com.moe.myfamilybudget.web.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.ParametresApi;
import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.usecase.DefaultSettingsUseCase;
import com.moe.myfamilybudget.application.usecase.settings.SettingsUseCase;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Contrôleur REST implémentant le contrat OpenAPI ParametresApi : façade mince, tout le traitement
 * métier et l'orchestration sont portés par {@link SettingsUseCase} (R-88, SILO-319).
 *
 * <p>Ce contrôleur ne garde que la conversion HTTP ({@link ResponseEntity}).
 */
@Service
@RestController
public class ParametersServiceImpl implements ParametresApi {

    private final SettingsUseCase settingsUseCase;

    @Autowired
    public ParametersServiceImpl(SettingsUseCase settingsUseCase) {
        this.settingsUseCase = settingsUseCase;
    }

    /**
     * Constructeur complet conservé pour la rétrocompatibilité des tests unitaires existants.
     */
    public ParametersServiceImpl(
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader,
            RetirementReader retirementReader,
            SettingsMapper settingsMapper,
            ObjectifsSettingsService objectifsSettingsService,
            PatrimoineCommandService patrimoineCommandService,
            SiloMutationLock siloMutationLock,
            SettingsCommandRouter settingsCommandRouter,
            TransactionRunner transactionRunner) {
        this(new DefaultSettingsUseCase(
                retirementSettingsReader, taxSettingsReader, tresorerieSettingsReader,
                simulationSettingsReader, economicAssumptionsReader, patrimoineReader,
                bankReader, retirementReader, settingsMapper, objectifsSettingsService,
                patrimoineCommandService, siloMutationLock, settingsCommandRouter,
                transactionRunner));
    }

    @Override
    public ResponseEntity<Object> getSettings() {
        return ResponseEntity.ok(settingsUseCase.getSettings());
    }

    @Override
    public ResponseEntity<Void> saveSettings(Object body) {
        settingsUseCase.saveSettings(body);
        return ResponseEntity.ok().build();
    }
}
