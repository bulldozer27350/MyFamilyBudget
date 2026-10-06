package com.moe.myfamilybudget.application.snapshot;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.application.mapper.BudgetFacadeView;
import com.moe.myfamilybudget.application.mapper.BudgetSnapshotFragments;
import com.moe.myfamilybudget.application.mapper.OverviewMapper;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.bankpointage.port.BankSnapshotWriter;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.credit.port.LoanSnapshotWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalSnapshotWriter;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSnapshotWriter;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSnapshotWriter;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineSnapshotWriter;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsSnapshotWriter;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsSnapshotWriter;

/**
 * Opérations transverses sur le budget complet : export ({@code GET /budget}), import
 * ({@code POST /budget/import}) et reset ({@code POST /budget/reset}). Aucun service métier local ne doit
 * passer par ici.
 *
 * <p>SILO-119 (lot B2) : plus aucun passage par {@code BudgetDataModel}. L'export assemble
 * {@link BudgetFacadeView} depuis les ports de lecture des silos ; l'import décompose le JSON en fragments
 * ({@link BudgetSnapshotFragments}) et appelle le port {@code replace} de chaque silo ; le reset appelle le
 * port {@code reset} de chaque silo. Le format JSON est inchangé.
 *
 * <p>VT-340 : import et reset écrivent dans plusieurs silos puis dans les paramètres Objectifs ; ils sont
 * transactionnels pour que l'échec d'une écriture annule les précédentes (base et cache mémoire). SILO-206 : les
 * verrous des silos écrits (tous, ici) sont pris en premier, via {@link SiloMutationLock}.
 *
 * <p>SILO-205 : la transaction est ouverte via le port {@link TransactionRunner} (plus d'{@code @Transactional}) ;
 * le verrou reste la première instruction exécutée dans la transaction.
 */
@Service
public class GlobalBudgetSnapshotService {

    private static final Set<MutationSilo> ALL_SILOS = EnumSet.allOf(MutationSilo.class);

    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;
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
    private final RetirementSnapshotWriter retirementWriter;
    private final TaxSnapshotWriter taxWriter;
    private final TresorerieSnapshotWriter tresorerieWriter;
    private final SimulationSettingsSnapshotWriter simulationWriter;
    private final EconomicAssumptionsSnapshotWriter economicAssumptionsWriter;
    private final PatrimoineSnapshotWriter patrimoineWriter;
    private final LoanSnapshotWriter loanWriter;
    private final GoalSnapshotWriter goalWriter;
    private final BankSnapshotWriter bankWriter;

    public GlobalBudgetSnapshotService(
            SiloMutationLock siloMutationLock,
            OverviewMapper overviewMapper,
            ObjectifsSettingsService objectifsSettingsService,
            SettingsReader settingsReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            TaxReader taxReader,
            BankReader bankReader,
            LoanReader loanReader,
            GoalReader goalReader,
            RetirementSnapshotWriter retirementWriter,
            TaxSnapshotWriter taxWriter,
            TresorerieSnapshotWriter tresorerieWriter,
            SimulationSettingsSnapshotWriter simulationWriter,
            EconomicAssumptionsSnapshotWriter economicAssumptionsWriter,
            PatrimoineSnapshotWriter patrimoineWriter,
            LoanSnapshotWriter loanWriter,
            GoalSnapshotWriter goalWriter,
            BankSnapshotWriter bankWriter,
            TransactionRunner transactionRunner) {
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
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
        this.retirementWriter = retirementWriter;
        this.taxWriter = taxWriter;
        this.tresorerieWriter = tresorerieWriter;
        this.simulationWriter = simulationWriter;
        this.economicAssumptionsWriter = economicAssumptionsWriter;
        this.patrimoineWriter = patrimoineWriter;
        this.loanWriter = loanWriter;
        this.goalWriter = goalWriter;
        this.bankWriter = bankWriter;
    }

    /** Export du budget complet (lecture seule). */
    public BudgetDataDto export() {
        return overviewMapper.toBudgetDataDto(composeFacadeView());
    }

    /**
     * Import du budget complet. Un corps {@code null} n'écrit rien et renvoie l'état courant.
     * Écrit chaque silo puis les paramètres Objectifs dans la même transaction.
     */
    public BudgetDataDto importSnapshot(BudgetDataDto body) {
        return transactionRunner.inTransaction(() -> {
            if (body != null) {
                siloMutationLock.lockForCurrentTransaction(ALL_SILOS);
                BudgetSnapshotFragments f = overviewMapper.toSnapshotFragments(body);
                retirementWriter.replace(f.retirementSettings(), f.retirement());
                taxWriter.replace(f.taxSettings(), f.taxChildren(), f.taxBrackets(), f.taxRateOverrides(),
                        f.taxActualOverrides());
                tresorerieWriter.replace(f.tresorerieSettings(), f.incomes(), f.charges(), f.oneoff(),
                        f.variableIncomes(), f.variableOverrides());
                simulationWriter.replace(f.simulationSettings());
                economicAssumptionsWriter.replace(f.economicAssumptions());
                patrimoineWriter.replace(f.placements(), f.realEstate(), f.transfers(), f.assetCategories());
                loanWriter.replace(f.loans());
                goalWriter.replace(f.goals());
                bankWriter.replace(f.bankImport());
                objectifsSettingsService.save(f.objectifsParameters());
            }
            return export();
        });
    }

    /** Réinitialisation du budget complet et des paramètres Objectifs (même transaction). */
    public BudgetDataDto reset() {
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(ALL_SILOS);
            retirementWriter.reset();
            taxWriter.reset();
            tresorerieWriter.reset();
            simulationWriter.reset();
            economicAssumptionsWriter.reset();
            patrimoineWriter.reset();
            loanWriter.reset();
            goalWriter.reset();
            bankWriter.reset();
            objectifsSettingsService.reset();
            return export();
        });
    }

    /** Vue de façade assemblée fragment par fragment depuis les ports de lecture (SILO-119, lot A). */
    private BudgetFacadeView composeFacadeView() {
        return new BudgetFacadeView(
                settingsReader.getSettings(), budgetReader.getIncomes(), budgetReader.getCharges(),
                patrimoineReader.getPlacements(), patrimoineReader.getRealEstate(), retirementReader.getRetirement(),
                taxReader.getTaxChildren(), taxReader.getTaxBrackets(), taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(), budgetReader.getOneoffExpenses(),
                PatrimoineTransferConverter.toBudget(patrimoineReader.getTransfers()),
                budgetReader.getVariableIncomes(), budgetReader.getVariableOverrides(), bankReader.getBankImport(),
                patrimoineReader.getAssetCategories(), loanReader.getLoans(), goalReader.getGoals(),
                objectifsSettingsService.current());
    }
}
