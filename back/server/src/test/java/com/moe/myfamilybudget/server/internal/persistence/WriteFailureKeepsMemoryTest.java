package com.moe.myfamilybudget.server.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BankPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ObjectifRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RetirementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;

/**
 * VT-330 -- Protege l'ordre voulu {@code DB -> memoire} de {@code BudgetCacheStore.applyAndPersist} :
 * quand la persistance echoue, l'ancien etat memoire reste celui servi par {@link PersistenceManager} et
 * par les ports de lecture, la nouvelle valeur n'est jamais servie "artificiellement" et aucun
 * {@link BudgetMutatedEvent} n'est publie.
 *
 * <p>Deux points de defaillance sont simules sur des repositories mockes : l'ecriture de l'entite
 * principale (premier acces base) et l'ecriture des lignes enfants (base deja partiellement modifiee).
 * Dans les deux cas l'exception d'origine doit remonter telle quelle a l'appelant. La persistance JPA
 * reelle (rollback effectif) releve de VT-320 / VT-340.
 */
class WriteFailureKeepsMemoryTest {

    private static final IllegalStateException DB_DOWN = new IllegalStateException("simulated database failure");

    private static final RetirementModel RETIREMENT_BEFORE = new RetirementModel(List.of(), new BigDecimal("47100"),
            new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-01-01", new BigDecimal("0.01"));

    private static final RetirementModel RETIREMENT_AFTER = new RetirementModel(List.of(), new BigDecimal("50000"),
            new BigDecimal("0.02"), new BigDecimal("1.5"), "2026-01-01", new BigDecimal("0.02"));

    private static final IncomeModel INCOME_REPLACEMENT = new IncomeModel("inc_replacement", "Prime",
            new BigDecimal("999"), "2026-01-01", "2053-12-31", BigDecimal.ZERO, "", "");

    private BudgetDataRepository budgetDataRepository;
    private IncomeRepository incomeRepository;
    private ApplicationEventPublisher eventPublisher;
    private PersistenceManager persistenceManager;

    private BudgetPersistenceAdapter budgetAdapter;
    private PatrimoinePersistenceAdapter patrimoineAdapter;
    private RetirementPersistenceAdapter retirementAdapter;
    private TaxPersistenceAdapter taxAdapter;
    private BankPersistenceAdapter bankAdapter;
    private SettingsPersistenceAdapter settingsAdapter;

    private BudgetDataModel before;

    @BeforeEach
    void setUp() {
        budgetDataRepository = mock(BudgetDataRepository.class);
        incomeRepository = mock(IncomeRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        databaseWorks();

        persistenceManager = new PersistenceManager(
                budgetDataRepository,
                mock(SettingsRepository.class),
                incomeRepository,
                mock(ChargeRepository.class),
                mock(PlacementRepository.class),
                mock(RealEstateRepository.class),
                mock(OneOffExpenseRepository.class),
                mock(TransferRepository.class),
                mock(VariableIncomeRepository.class),
                mock(VariableOverrideRepository.class),
                mock(TaxChildRepository.class),
                mock(TaxBracketRepository.class),
                mock(TaxRateOverrideRepository.class),
                mock(TaxActualOverrideRepository.class),
                mock(AssetCategoryRepository.class),
                mock(RetirementRepository.class),
                mock(BankImportRepository.class),
                mock(LoanRepository.class),
                mock(ObjectifRepository.class),
                mock(GoalRepository.class),
                mock(CreditLoanRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                mock(BankImportDocumentRepository.class),
                mock(PlatformTransactionManager.class),
                eventPublisher);
        persistenceManager.init();

        budgetAdapter = new BudgetPersistenceAdapter(persistenceManager);
        patrimoineAdapter = new PatrimoinePersistenceAdapter(persistenceManager);
        retirementAdapter = new RetirementPersistenceAdapter(persistenceManager);
        taxAdapter = new TaxPersistenceAdapter(persistenceManager);
        bankAdapter = new BankPersistenceAdapter(persistenceManager);
        settingsAdapter = new SettingsPersistenceAdapter(persistenceManager);

        // Etat de reference non trivial, ecrit alors que la base fonctionne.
        persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_seed", "label", "Salaire", "monthly", new BigDecimal("2500"))));
        persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_other", "label", "Freelance", "monthly", new BigDecimal("400"))));
        persistenceManager.write(m -> m.savePatrimoineRow("placements",
                Map.of("id", "plc_seed", "label", "Livret", "balance", new BigDecimal("1000"))));
        persistenceManager.write(m -> m.updateRetirement(RETIREMENT_BEFORE));

        before = persistenceManager.getBudgetData();
        clearInvocations(eventPublisher);
    }

    // =========================================================================
    // ECHEC A L'ECRITURE DE L'ENTITE PRINCIPALE
    // =========================================================================

    @Test
    @DisplayName("Entite principale en echec : aucune mutation ne modifie l'etat memoire")
    void mainEntityFailureKeepsPreviousState() {
        databaseFailsOnMainEntity();

        Map<String, Runnable> mutations = mutationsKeepingIncomes();
        mutations.put("resetData", persistenceManager::resetData);
        mutations.put("setBudgetData(null)", () -> persistenceManager.setBudgetData(null));

        assertEveryMutationFailsAndLeavesMemoryUntouched(mutations);
    }

    // =========================================================================
    // ECHEC PENDANT L'ECRITURE DES LIGNES ENFANTS
    // =========================================================================

    @Test
    @DisplayName("Ligne enfant en echec (base deja partiellement ecrite) : l'etat memoire reste l'ancien")
    void childRowFailureKeepsPreviousState() {
        databaseFailsOnChildRows();

        assertEveryMutationFailsAndLeavesMemoryUntouched(mutationsKeepingIncomes());
    }

    // =========================================================================
    // LA NOUVELLE VALEUR N'EST PAS SERVIE
    // =========================================================================

    @Test
    @DisplayName("La nouvelle valeur n'est servie ni par le gestionnaire ni par les ports de lecture")
    void newValueIsNotServedAfterFailure() {
        databaseFailsOnMainEntity();

        assertThatThrownBy(() -> persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER))).isSameAs(DB_DOWN);
        assertThatThrownBy(() -> persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_new", "label", "Prime", "monthly", new BigDecimal("999"))))).isSameAs(DB_DOWN);

        assertThat(retirementAdapter.getRetirement()).isEqualTo(RETIREMENT_BEFORE);
        assertThat(persistenceManager.getBudgetData().retirement()).isEqualTo(RETIREMENT_BEFORE);
        assertThat(budgetAdapter.getIncomes()).extracting(IncomeModel::id)
                .containsExactly("inc_seed", "inc_other");
    }

    // =========================================================================
    // RETOUR A LA NORMALE
    // =========================================================================

    @Test
    @DisplayName("Une fois la base revenue, la meme mutation aboutit et n'est visible qu'a ce moment-la")
    void writeSucceedsOnceDatabaseRecovers() {
        databaseFailsOnMainEntity();
        assertThatThrownBy(() -> persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER))).isSameAs(DB_DOWN);
        assertThat(retirementAdapter.getRetirement()).isEqualTo(RETIREMENT_BEFORE);
        verifyNoInteractions(eventPublisher);

        databaseWorks();
        persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER));

        assertThat(retirementAdapter.getRetirement()).isEqualTo(RETIREMENT_AFTER);
        verify(eventPublisher).publishEvent(any(BudgetMutatedEvent.class));
    }

    // =========================================================================
    // OUTILLAGE
    // =========================================================================

    /** Mutations qui laissent au moins un revenu en base : elles atteignent toutes l'ecriture des lignes enfants. */
    private Map<String, Runnable> mutationsKeepingIncomes() {
        Map<String, Runnable> mutations = new LinkedHashMap<>();
        mutations.put("addTresorerieRow", () -> persistenceManager.write(m -> m.addTresorerieRow("incomes",
                Map.of("id", "inc_new", "label", "Prime", "monthly", new BigDecimal("999")))));
        mutations.put("updateTresorerieRow", () -> persistenceManager.write(m -> m.updateTresorerieRow("incomes", "inc_seed",
                "monthly", new BigDecimal("1"))));
        mutations.put("removeTresorerieRow",
                () -> persistenceManager.write(m -> m.removeTresorerieRow("incomes", "inc_seed")));
        mutations.put("savePatrimoineRow", () -> persistenceManager.write(m -> m.savePatrimoineRow("placements",
                Map.of("id", "plc_seed", "label", "Livret modifie", "balance", new BigDecimal("5")))));
        mutations.put("deletePatrimoineRow",
                () -> persistenceManager.write(m -> m.deletePatrimoineRow("placements", "plc_seed")));
        mutations.put("updateRetirement", () -> persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER)));
        mutations.put("updateTaxConfig", () -> persistenceManager.write(m -> m.updateTaxConfig(
                List.of(new TaxChildModel("tc_new", "Emma", 2015)), null, null, null)));
        mutations.put("updateTaxSettings", () -> persistenceManager.write(m -> m.updateTaxSettings("retireAge", 60)));
        mutations.put("addAssetCategory", () -> persistenceManager.write(m -> m.addAssetCategory(
                new AssetCategoryModel("cat_new", "icon", "Nouvelle categorie", "bucket", "#ffffff"))));
        mutations.put("updateBankImport", () -> persistenceManager.write(m -> m.updateBankImport(
                new BankImportModel(List.of(), List.of(), List.of()))));
        mutations.put("setBudgetData", () -> persistenceManager.setBudgetData(
                before.withIncomes(List.of(INCOME_REPLACEMENT))));
        return mutations;
    }

    private void assertEveryMutationFailsAndLeavesMemoryUntouched(Map<String, Runnable> mutations) {
        List<IncomeModel> incomes = budgetAdapter.getIncomes();
        var placements = patrimoineAdapter.getPlacements();
        var assetCategories = patrimoineAdapter.getAssetCategories();
        var taxChildren = taxAdapter.getTaxChildren();
        var bankImport = bankAdapter.getBankImport();
        var settings = settingsAdapter.getSettings();

        mutations.forEach((name, mutation) -> {
            assertThatThrownBy(mutation::run).as(name).isSameAs(DB_DOWN);

            assertThat(persistenceManager.getBudgetData()).as(name).isSameAs(before);
            assertThat(budgetAdapter.getIncomes()).as(name).isEqualTo(incomes);
            assertThat(patrimoineAdapter.getPlacements()).as(name).isEqualTo(placements);
            assertThat(patrimoineAdapter.getAssetCategories()).as(name).isEqualTo(assetCategories);
            assertThat(retirementAdapter.getRetirement()).as(name).isEqualTo(RETIREMENT_BEFORE);
            assertThat(taxAdapter.getTaxChildren()).as(name).isEqualTo(taxChildren);
            assertThat(bankAdapter.getBankImport()).as(name).isEqualTo(bankImport);
            assertThat(settingsAdapter.getSettings()).as(name).isEqualTo(settings);
        });

        assertThat(incomes).extracting(IncomeModel::id).containsExactly("inc_seed", "inc_other");
        assertThat(placements).hasSize(1);
        // Aucune mutation en echec ne doit avoir declenche de controle des notifications.
        verifyNoInteractions(eventPublisher);
    }

    private void databaseWorks() {
        reset(budgetDataRepository, incomeRepository);
        doAnswer(invocation -> invocation.getArgument(0)).when(budgetDataRepository).save(any());
    }

    private void databaseFailsOnMainEntity() {
        databaseWorks();
        doThrow(DB_DOWN).when(budgetDataRepository).save(any());
    }

    private void databaseFailsOnChildRows() {
        databaseWorks();
        doThrow(DB_DOWN).when(incomeRepository).save(any());
    }
}
