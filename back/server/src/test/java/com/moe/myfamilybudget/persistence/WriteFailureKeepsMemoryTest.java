package com.moe.myfamilybudget.persistence;

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

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.persistence.adapter.PatrimoinePersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.RetirementPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
import com.moe.myfamilybudget.persistence.adapter.TaxPersistenceAdapter;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.persistence.repository.SettingsRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

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
    private PlacementRepository placementRepository;
    private ApplicationEventPublisher eventPublisher;
    private PersistenceManager persistenceManager;

    private PatrimoinePersistenceAdapter patrimoineAdapter;
    private RetirementPersistenceAdapter retirementAdapter;
    private TaxPersistenceAdapter taxAdapter;
    private SettingsPersistenceAdapter settingsAdapter;

    private BudgetDataModel before;

    @BeforeEach
    void setUp() {
        budgetDataRepository = mock(BudgetDataRepository.class);
        placementRepository = mock(PlacementRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        databaseWorks();

        persistenceManager = new PersistenceManager(
                budgetDataRepository,
                mock(SettingsRepository.class),
                placementRepository,
                mock(RealEstateRepository.class),
                mock(AssetCategoryRepository.class),
                mock(GoalRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                mock(BankImportDocumentRepository.class),
                mock(WealthPlacementRepository.class),
                mock(WealthRealEstateRepository.class),
                mock(WealthCategoryRepository.class),
                mock(PensionSettingsRepository.class),
                mock(FiscalSettingsRepository.class),
                mock(CashflowSettingsRepository.class),
                mock(PlatformTransactionManager.class),
                eventPublisher);
        persistenceManager.init();

        patrimoineAdapter = new PatrimoinePersistenceAdapter(persistenceManager);
        retirementAdapter = new RetirementPersistenceAdapter(persistenceManager);
        taxAdapter = new TaxPersistenceAdapter(persistenceManager);
        settingsAdapter = new SettingsPersistenceAdapter(persistenceManager);

        // Etat de reference non trivial, ecrit alors que la base fonctionne.
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
        assertThatThrownBy(() -> persistenceManager.write(m -> m.savePatrimoineRow("placements",
                Map.of("id", "plc_new", "label", "PEA", "balance", new BigDecimal("999"))))).isSameAs(DB_DOWN);

        assertThat(retirementAdapter.getRetirement()).isEqualTo(RETIREMENT_BEFORE);
        assertThat(persistenceManager.getBudgetData().retirement()).isEqualTo(RETIREMENT_BEFORE);
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

    /** Mutations qui laissent au moins un placement en base : elles atteignent toutes l'ecriture des lignes enfants. */
    private Map<String, Runnable> mutationsKeepingIncomes() {
        Map<String, Runnable> mutations = new LinkedHashMap<>();
        mutations.put("savePatrimoineRow", () -> persistenceManager.write(m -> m.savePatrimoineRow("placements",
                Map.of("id", "plc_seed", "label", "Livret modifie", "balance", new BigDecimal("5")))));
        mutations.put("deletePatrimoineRow",
                () -> persistenceManager.write(m -> m.deletePatrimoineRow("placements", "plc_seed")));
        mutations.put("updateRetirement", () -> persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER)));
        mutations.put("updateTaxConfig", () -> persistenceManager.write(m -> m.updateTaxConfig(
                List.of(new TaxChildModel("tc_new", "Emma", 2015)), null, null, null)));
        mutations.put("updateRetirementSetting", () -> persistenceManager.write(m -> m.updateRetirementSetting(
                RetirementSettingField.RETIRE_AGE, 60)));
        mutations.put("addAssetCategory", () -> persistenceManager.write(m -> m.addAssetCategory(
                new AssetCategoryModel("cat_new", "icon", "Nouvelle categorie", "bucket", "#ffffff"))));
        mutations.put("setBudgetData", () -> persistenceManager.setBudgetData(
                before.withPlacements(List.of())));
        return mutations;
    }

    private void assertEveryMutationFailsAndLeavesMemoryUntouched(Map<String, Runnable> mutations) {
        var placements = patrimoineAdapter.getPlacements();
        var assetCategories = patrimoineAdapter.getAssetCategories();
        var taxChildren = taxAdapter.getTaxChildren();
        var settings = settingsAdapter.getSettings();

        mutations.forEach((name, mutation) -> {
            assertThatThrownBy(mutation::run).as(name).isSameAs(DB_DOWN);

            assertThat(persistenceManager.getBudgetData()).as(name).isSameAs(before);
            assertThat(patrimoineAdapter.getPlacements()).as(name).isEqualTo(placements);
            assertThat(patrimoineAdapter.getAssetCategories()).as(name).isEqualTo(assetCategories);
            assertThat(retirementAdapter.getRetirement()).as(name).isEqualTo(RETIREMENT_BEFORE);
            assertThat(taxAdapter.getTaxChildren()).as(name).isEqualTo(taxChildren);
            assertThat(settingsAdapter.getSettings()).as(name).isEqualTo(settings);
        });

        assertThat(placements).hasSize(1);
        // Aucune mutation en echec ne doit avoir declenche de controle des notifications.
        verifyNoInteractions(eventPublisher);
    }

    private void databaseWorks() {
        reset(budgetDataRepository, placementRepository);
        doAnswer(invocation -> invocation.getArgument(0)).when(budgetDataRepository).save(any());
    }

    private void databaseFailsOnMainEntity() {
        databaseWorks();
        doThrow(DB_DOWN).when(budgetDataRepository).save(any());
    }

    private void databaseFailsOnChildRows() {
        databaseWorks();
        doThrow(DB_DOWN).when(placementRepository).save(any());
    }
}
