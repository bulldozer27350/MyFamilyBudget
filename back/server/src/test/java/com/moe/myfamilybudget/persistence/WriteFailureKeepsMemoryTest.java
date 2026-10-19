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

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.persistence.adapter.SettingsPersistenceAdapter;
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
import com.moe.myfamilybudget.persistence.repository.SettingsRepository;

/**
 * VT-330 -- Protege l'ordre voulu {@code DB -> memoire} de {@code BudgetCacheStore.applyAndPersist} :
 * quand la persistance echoue, l'ancien etat memoire reste celui servi par {@link PersistenceManager} et
 * par les ports de lecture, la nouvelle valeur n'est jamais servie "artificiellement" et aucun
 * {@link BudgetMutatedEvent} n'est publie.
 *
 * <p>Deux points de defaillance sont simules sur des repositories mockes : l'ecriture de l'entite
 * principale (premier acces base, {@code budgetDataRepository}) et l'ecriture d'une ligne enfant (base deja
 * partiellement modifiee, {@code placementRepository} : {@code BudgetPersistenceGateway.savePlacements} y
 * ecrit inconditionnellement a chaque {@code save} des lors que la mutation en cours laisse au moins un
 * placement). Dans les deux cas l'exception d'origine doit remonter telle quelle a l'appelant. La
 * persistance JPA reelle (rollback effectif) releve de VT-320 / VT-340.
 *
 * <p>R-40 (meme arbitrage que R-30b du 14/10/2026, option B) : ce test s'appuyait auparavant sur la Retraite
 * ({@code pensionPlanRepository}, via {@code BudgetPersistenceGateway.syncPension}) comme ligne enfant
 * ecrite inconditionnellement. R-40 (lot B) a retire cette resynchronisation (JpaRetirementStore ecrit
 * desormais directement pension_plan, sans passer par ce cache) ; le test s'appuie donc desormais sur les
 * placements, seule collection enfant encore rattachee au hub (voir le commentaire de
 * {@code BudgetPersistenceGateway.save}).
 */
class WriteFailureKeepsMemoryTest {

    private static final IllegalStateException DB_DOWN = new IllegalStateException("simulated database failure");

    private static final RetirementModel RETIREMENT_AFTER = new RetirementModel(List.of(), new BigDecimal("50000"),
            new BigDecimal("0.02"), new BigDecimal("1.5"), "2026-01-01", new BigDecimal("0.02"));

    private static final PlacementModel PLACEMENT_BEFORE = new PlacementModel("plc_1", "PEA", "Actions",
            new BigDecimal("10000"), "2026-01-01", new BigDecimal("200"), "2026-01-01", "2053-12-31",
            new BigDecimal("0.02"), new BigDecimal("0.04"), new BigDecimal("0.07"), false, "note");

    private static final PlacementModel PLACEMENT_AFTER = new PlacementModel("plc_1", "PEA", "Actions",
            new BigDecimal("12000"), "2026-01-01", new BigDecimal("200"), "2026-01-01", "2053-12-31",
            new BigDecimal("0.02"), new BigDecimal("0.04"), new BigDecimal("0.07"), false, "note mise a jour");

    private BudgetDataRepository budgetDataRepository;
    private PlacementRepository placementRepository;
    private ApplicationEventPublisher eventPublisher;
    private PersistenceManager persistenceManager;

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
                mock(GoalRepository.class),
                mock(FiscalChildRepository.class),
                mock(FiscalBracketRepository.class),
                mock(FiscalRateOverrideRepository.class),
                mock(FiscalActualOverrideRepository.class),
                mock(PensionPlanRepository.class),
                mock(BankImportDocumentRepository.class),
                mock(PensionSettingsRepository.class),
                mock(FiscalSettingsRepository.class),
                mock(CashflowSettingsRepository.class),
                mock(PlatformTransactionManager.class),
                eventPublisher);
        persistenceManager.init();

        settingsAdapter = new SettingsPersistenceAdapter(persistenceManager);

        // Etat de reference non trivial, ecrit alors que la base fonctionne. Des qu'un placement est
        // present, savePlacements ecrit inconditionnellement placementRepository a chaque mutation
        // suivante, quel que soit le domaine mute.
        persistenceManager.setBudgetData(persistenceManager.getBudgetData().withPlacements(List.of(PLACEMENT_BEFORE)));

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

        Map<String, Runnable> mutations = mutationsTriggeringChildWrite();
        mutations.put("resetData", persistenceManager::resetData);
        mutations.put("setBudgetData(null)", () -> persistenceManager.setBudgetData(null));

        assertEveryMutationFailsAndLeavesMemoryUntouched(mutations);
    }

    // =========================================================================
    // ECHEC PENDANT L'ECRITURE D'UNE LIGNE ENFANT
    // =========================================================================

    @Test
    @DisplayName("Ligne enfant en echec (base deja partiellement ecrite) : l'etat memoire reste l'ancien")
    void childRowFailureKeepsPreviousState() {
        databaseFailsOnChildRow();

        assertEveryMutationFailsAndLeavesMemoryUntouched(mutationsTriggeringChildWrite());
    }

    // =========================================================================
    // LA NOUVELLE VALEUR N'EST PAS SERVIE
    // =========================================================================

    @Test
    @DisplayName("La nouvelle valeur n'est servie ni par le gestionnaire ni par les ports de lecture")
    void newValueIsNotServedAfterFailure() {
        databaseFailsOnMainEntity();

        assertThatThrownBy(() -> persistenceManager.setBudgetData(before.withPlacements(List.of(PLACEMENT_AFTER))))
                .isSameAs(DB_DOWN);
        assertThatThrownBy(() -> persistenceManager.write(m -> m.updateRetirementSetting(
                RetirementSettingField.RETIRE_AGE, 60))).isSameAs(DB_DOWN);

        assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).isEqualTo(List.of(PLACEMENT_BEFORE));
    }

    // =========================================================================
    // RETOUR A LA NORMALE
    // =========================================================================

    @Test
    @DisplayName("Une fois la base revenue, la meme mutation aboutit et n'est visible qu'a ce moment-la")
    void writeSucceedsOnceDatabaseRecovers() {
        databaseFailsOnMainEntity();
        assertThatThrownBy(() -> persistenceManager.setBudgetData(before.withPlacements(List.of(PLACEMENT_AFTER))))
                .isSameAs(DB_DOWN);
        assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).isEqualTo(List.of(PLACEMENT_BEFORE));
        verifyNoInteractions(eventPublisher);

        databaseWorks();
        persistenceManager.setBudgetData(before.withPlacements(List.of(PLACEMENT_AFTER)));

        assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).isEqualTo(List.of(PLACEMENT_AFTER));
        verify(eventPublisher).publishEvent(any(BudgetMutatedEvent.class));
    }

    // =========================================================================
    // OUTILLAGE
    // =========================================================================

    /** Mutations qui, un placement de {@link #before} etant present, atteignent toutes savePlacements. */
    private Map<String, Runnable> mutationsTriggeringChildWrite() {
        Map<String, Runnable> mutations = new LinkedHashMap<>();
        mutations.put("updateRetirement", () -> persistenceManager.write(m -> m.updateRetirement(RETIREMENT_AFTER)));
        mutations.put("updateTaxConfig", () -> persistenceManager.write(m -> m.updateTaxConfig(
                List.of(new TaxChildModel("tc_new", "Emma", 2015)), null, null, null)));
        mutations.put("updateRetirementSetting", () -> persistenceManager.write(m -> m.updateRetirementSetting(
                RetirementSettingField.RETIRE_AGE, 60)));
        mutations.put("setBudgetData", () -> persistenceManager.setBudgetData(before.withPlacements(List.of(PLACEMENT_AFTER))));
        return mutations;
    }

    private void assertEveryMutationFailsAndLeavesMemoryUntouched(Map<String, Runnable> mutations) {
        var taxChildren = persistenceManager.getBudgetData().getEffectiveTaxChildren();
        var settings = settingsAdapter.getSettings();
        var placements = persistenceManager.getBudgetData().getEffectivePlacements();

        mutations.forEach((name, mutation) -> {
            assertThatThrownBy(mutation::run).as(name).isSameAs(DB_DOWN);

            assertThat(persistenceManager.getBudgetData()).as(name).isSameAs(before);
            assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).as(name).isEqualTo(placements);
            assertThat(persistenceManager.getBudgetData().getEffectiveTaxChildren()).as(name).isEqualTo(taxChildren);
            assertThat(settingsAdapter.getSettings()).as(name).isEqualTo(settings);
        });

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

    private void databaseFailsOnChildRow() {
        databaseWorks();
        doThrow(DB_DOWN).when(placementRepository).save(any());
    }
}
