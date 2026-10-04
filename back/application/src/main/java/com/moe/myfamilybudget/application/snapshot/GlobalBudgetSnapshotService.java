package com.moe.myfamilybudget.application.snapshot;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.application.mapper.OverviewMapper;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.transition.port.BudgetMutationLock;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.transition.port.GlobalBudgetSnapshotWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;

/**
 * CLEAN-020 : frontière explicite des opérations transverses qui justifient encore le snapshot
 * global {@link BudgetDataModel} : export ({@code GET /budget}), import ({@code POST /budget/import}),
 * reset ({@code POST /budget/reset}) et, plus généralement, sauvegarde/restauration du budget complet.
 *
 * <p>Ce composant est le <strong>seul</strong> point d'application qui assemble le snapshot à partir des
 * ports de lecture et qui écrit via {@link GlobalBudgetSnapshotWriter} (import, reset) ; MAVEN-103 : le port
 * est implémenté par {@code persistence} (qui délègue à {@code PersistenceManager}), ce qui permet à ce composant de
 * vivre dans {@code application} sans dépendre de {@code persistence}.
 * Le contrôleur ({@code SystemeServiceImpl}) ne manipule plus ni {@code BudgetDataModel} ni
 * {@code PersistenceManager}. Aucun service métier local ne doit passer par ici.
 *
 * <p>VT-340 : import et reset écrivent dans deux domaines (budget, puis paramètres Objectifs) ; ils sont donc
 * transactionnels pour que l'échec de la seconde écriture annule la première (base et cache mémoire). Le verrou du
 * budget est pris en premier.
 *
 * <p>SILO-119 (lot A) : l'export et la réponse du reset ne passent plus par {@code BudgetDataModel} ; la vue de
 * façade {@link BudgetFacadeView} est assemblée directement depuis les ports de lecture des silos. Seul le chemin
 * d'écriture (import via {@code OverviewMapper#toInternalModel} et {@link GlobalBudgetSnapshotWriter}) garde le
 * modèle global, jusqu'au lot B (ports {@code replace}/{@code reset} par silo).
 */
@Service
public class GlobalBudgetSnapshotService {

    private final BudgetMutationLock budgetMutationLock;
    private final GlobalBudgetSnapshotWriter snapshotWriter;
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
            BudgetMutationLock budgetMutationLock,
            GlobalBudgetSnapshotWriter snapshotWriter,
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
        this.budgetMutationLock = budgetMutationLock;
        this.snapshotWriter = snapshotWriter;
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
        return overviewMapper.toBudgetDataDto(composeFacadeView());
    }

    /**
     * Import du budget complet. Un corps {@code null} n'écrit rien et renvoie l'état courant.
     * Écrit le budget puis les paramètres Objectifs dans la même transaction.
     */
    @Transactional
    public BudgetDataDto importSnapshot(BudgetDataDto body) {
        if (body != null) {
            budgetMutationLock.lockForCurrentTransaction();
            BudgetDataModel model = overviewMapper.toInternalModel(body);
            snapshotWriter.setBudgetData(model);
            objectifsSettingsService.save(overviewMapper.toObjectifsParameters(body));
        }
        return export();
    }

    /** Réinitialisation du budget complet et des paramètres Objectifs (même transaction). */
    @Transactional
    public BudgetDataDto reset() {
        budgetMutationLock.lockForCurrentTransaction();
        snapshotWriter.resetData();
        objectifsSettingsService.reset();
        return export();
    }

    /** Vue de façade assemblée fragment par fragment depuis les ports de lecture (SILO-119, lot A). */
    private BudgetFacadeView composeFacadeView() {
        return new BudgetFacadeView(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), retirementReader.getRetirement(),
                taxReader.getTaxChildren(), taxReader.getTaxBrackets(), taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(), patrimoineReader.getTransfers(),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(), bankReader.getBankImport(),
                patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals(),
                objectifsSettingsService.current());
    }
}
