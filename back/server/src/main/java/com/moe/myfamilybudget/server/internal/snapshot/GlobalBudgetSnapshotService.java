package com.moe.myfamilybudget.server.internal.snapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * CLEAN-020 : frontière explicite des opérations transverses qui justifient encore le snapshot
 * global {@link BudgetDataModel} : export ({@code GET /budget}), import ({@code POST /budget/import}),
 * reset ({@code POST /budget/reset}) et, plus généralement, sauvegarde/restauration du budget complet.
 *
 * <p>Ce composant est le <strong>seul</strong> point d'application qui assemble le snapshot à partir des
 * ports de lecture et qui appelle {@link PersistenceManager#setBudgetData} / {@link PersistenceManager#resetData}.
 * Le contrôleur ({@code SystemeServiceImpl}) ne manipule plus ni {@code BudgetDataModel} ni
 * {@code PersistenceManager}. Aucun service métier local ne doit passer par ici.
 *
 * <p>VT-340 : import et reset écrivent dans deux domaines (budget, puis paramètres Objectifs) ; ils sont donc
 * transactionnels pour que l'échec de la seconde écriture annule la première (base et cache mémoire). Le verrou du
 * budget est pris en premier.
 *
 * <p>Le snapshot reste un {@code ASSEMBLY-TEMP} jusqu'à la fin des {@code DB-xxx} (voir
 * {@code doc/architecture/19-inventaire-budget-data-model.md}).
 */
@Service
public class GlobalBudgetSnapshotService {

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

    public GlobalBudgetSnapshotService(
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

    /** Export du budget complet (lecture seule). */
    public BudgetDataDto export() {
        return overviewMapper.toBudgetDataDto(composeSnapshot(), objectifsSettingsService.current());
    }

    /**
     * Import du budget complet. Un corps {@code null} n'écrit rien et renvoie l'état courant.
     * Écrit le budget puis les paramètres Objectifs dans la même transaction.
     */
    @Transactional
    public BudgetDataDto importSnapshot(BudgetDataDto body) {
        if (body != null) {
            persistenceManager.lockForCurrentTransaction();
            BudgetDataModel model = overviewMapper.toInternalModel(body);
            persistenceManager.setBudgetData(model);
            objectifsSettingsService.save(overviewMapper.toObjectifsParameters(body));
        }
        return export();
    }

    /** Réinitialisation du budget complet et des paramètres Objectifs (même transaction). */
    @Transactional
    public BudgetDataDto reset() {
        persistenceManager.lockForCurrentTransaction();
        BudgetDataModel reset = persistenceManager.resetData();
        objectifsSettingsService.reset();
        return overviewMapper.toBudgetDataDto(reset, objectifsSettingsService.current());
    }

    private BudgetDataModel composeSnapshot() {
        return new BudgetDataModel(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), retirementReader.getRetirement(),
                taxReader.getTaxChildren(), taxReader.getTaxBrackets(), taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(), patrimoineReader.getTransfers(),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(), bankReader.getBankImport(),
                patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals());
    }
}
