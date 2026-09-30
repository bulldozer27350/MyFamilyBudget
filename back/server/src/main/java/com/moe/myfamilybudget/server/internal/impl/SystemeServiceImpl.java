package com.moe.myfamilybudget.server.internal.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SystemeApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.BudgetReader;
import com.moe.myfamilybudget.server.internal.port.GoalReader;
import com.moe.myfamilybudget.server.internal.port.LoanReader;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.RetirementReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;
import com.moe.myfamilybudget.server.internal.port.TaxReader;

/**
 * RF-B01 (voir doc/architecture/13-persistance.md) : la lecture ({@code getBudgetFull} et la
 * relecture après import) compose les huit ports de domaine plutôt que d'appeler
 * {@code persistenceManager.getBudgetData()}, comme {@code OverviewServiceImpl} (export intégral
 * du budget). L'import et la réinitialisation restent, eux, hors périmètre de ce patch : ce sont
 * des mutations transverses à tous les domaines, qu'aucun {@code CommandService} de domaine
 * (RF-A00) ne couvre — {@link PersistenceManager} reste ici le point d'écriture global assumé.
 *
 * <p>VT-340 : {@code importJSON} et {@code resetData} écrivent dans deux domaines (budget, puis
 * paramètres Objectifs) ; elles sont donc {@code @Transactional} pour que l'échec de la seconde écriture
 * annule la première (base et cache mémoire).
 */
@RestController
public class SystemeServiceImpl implements SystemeApi {

    private final PersistenceManager persistenceManager;
    private final OverviewMapper overviewMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final SettingsReader settingsReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final BankReader bankReader;
    private final LoanReader loanReader;
    private final GoalReader goalReader;

    public SystemeServiceImpl(
            PersistenceManager persistenceManager,
            OverviewMapper overviewMapper,
            ObjectifsSettingsService objectifsSettingsService,
            SettingsReader settingsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            BankReader bankReader,
            LoanReader loanReader,
            GoalReader goalReader) {
        this.persistenceManager = persistenceManager;
        this.overviewMapper = overviewMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.settingsReader = settingsReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.bankReader = bankReader;
        this.loanReader = loanReader;
        this.goalReader = goalReader;
    }

    private BudgetDataModel composeBudgetData() {
        return new BudgetDataModel(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), retirementReader.getRetirement(),
                taxReader.getTaxChildren(), taxReader.getTaxBrackets(), taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(), patrimoineReader.getTransfers(),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(), bankReader.getBankImport(),
                patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals());
    }

    @Override
    public ResponseEntity<BudgetDataDto> getBudgetFull() {
        BudgetDataModel model = composeBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(model, objectifsSettingsService.current()));
    }

    @Override
    @Transactional
    public ResponseEntity<BudgetDataDto> importJSON(BudgetDataDto body) {
        if (body != null) {
            persistenceManager.lockForCurrentTransaction();
            BudgetDataModel model = overviewMapper.toInternalModel(body);
            persistenceManager.setBudgetData(model);
            objectifsSettingsService.save(overviewMapper.toObjectifsParameters(body));
        }
        BudgetDataModel current = composeBudgetData();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(current, objectifsSettingsService.current()));
    }

    @Override
    @Transactional
    public ResponseEntity<BudgetDataDto> resetData() {
        persistenceManager.lockForCurrentTransaction();
        BudgetDataModel reset = persistenceManager.resetData();
        objectifsSettingsService.reset();
        return ResponseEntity.ok(overviewMapper.toBudgetDataDto(reset, objectifsSettingsService.current()));
    }
}
