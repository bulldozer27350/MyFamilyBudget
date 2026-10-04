package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.transition.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.transition.model.SimulationSettingsModel;

/**
 * SILO-119 (lot B1) -- Chaque silo remplace et réinitialise uniquement ses propres données : les ports
 * {@code *SnapshotWriter} ne touchent ni les paramètres ni les listes des autres silos.
 */
@DisplayName("SILO-119 (lot B1) -- remplacement et réinitialisation par silo")
class SnapshotFragmentWritersTest {

    private PersistenceManager persistenceManager;
    private RetirementPersistenceAdapter retirement;
    private TaxPersistenceAdapter tax;
    private TresoreriePersistenceAdapter tresorerie;
    private PatrimoinePersistenceAdapter patrimoine;
    private LoanPersistenceAdapter loans;
    private GoalPersistenceAdapter goals;
    private BankPersistenceAdapter bank;
    private SimulationSettingsSnapshotAdapter simulation;
    private EconomicAssumptionsSnapshotAdapter economic;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        retirement = new RetirementPersistenceAdapter(persistenceManager);
        tax = new TaxPersistenceAdapter(persistenceManager);
        tresorerie = new TresoreriePersistenceAdapter(persistenceManager);
        patrimoine = new PatrimoinePersistenceAdapter(persistenceManager);
        loans = new LoanPersistenceAdapter(persistenceManager);
        goals = new GoalPersistenceAdapter(persistenceManager);
        bank = new BankPersistenceAdapter(persistenceManager);
        simulation = new SimulationSettingsSnapshotAdapter(persistenceManager);
        economic = new EconomicAssumptionsSnapshotAdapter(persistenceManager);
    }

    private SettingsModel settings() {
        return persistenceManager.getBudgetData().settings();
    }

    @Test
    @DisplayName("Retraite : replace puis reset ne touchent ni la fiscalité ni la trésorerie")
    void retirementReplaceAndResetAreIsolated() {
        tax.replace(new TaxSettingsModel(25, new BigDecimal("0.12")), null, null, null, null);
        SettingsModel before = settings();

        RetirementModel plan = new RetirementModel(Collections.emptyList(), new BigDecimal("48000"),
                new BigDecimal("0.02"), new BigDecimal("1.4386"), "2025-11-01", new BigDecimal("0.01"));
        retirement.replace(new RetirementSettingsModel(1990, 60), plan);

        assertThat(settings().birthYear()).isEqualTo(1990);
        assertThat(settings().retireAge()).isEqualTo(60);
        assertThat(persistenceManager.getBudgetData().retirement().pass2026()).isEqualByComparingTo("48000");
        assertThat(settings().childExitAge()).isEqualTo(before.childExitAge());
        assertThat(settings().taxAbattement()).isEqualByComparingTo(before.taxAbattement());
        assertThat(settings().pivotMode()).isEqualTo(before.pivotMode());

        retirement.reset();

        assertThat(settings().birthYear()).isEqualTo(1985);
        assertThat(settings().retireAge()).isEqualTo(64);
        assertThat(persistenceManager.getBudgetData().retirement().pass2026()).isEqualByComparingTo("47100");
        assertThat(settings().childExitAge()).isEqualTo(25);
    }

    @Test
    @DisplayName("Fiscalité : replace (listes absentes = vides) puis reset (barème par défaut)")
    void taxReplaceAndResetAreIsolated() {
        retirement.replace(new RetirementSettingsModel(1975, 66), persistenceManager.getBudgetData().retirement());

        tax.replace(new TaxSettingsModel(25, new BigDecimal("0.12")), null,
                List.of(new TaxBracketModel("tb_x", new BigDecimal("1000"), new BigDecimal("0.05"))), null, null);

        assertThat(settings().childExitAge()).isEqualTo(25);
        assertThat(settings().taxAbattement()).isEqualByComparingTo("0.12");
        assertThat(persistenceManager.getBudgetData().taxBrackets()).hasSize(1);
        assertThat(persistenceManager.getBudgetData().taxChildren()).isEmpty();
        assertThat(settings().birthYear()).isEqualTo(1975);

        tax.reset();

        assertThat(settings().childExitAge()).isEqualTo(21);
        assertThat(settings().taxAbattement()).isEqualByComparingTo("0.10");
        assertThat(persistenceManager.getBudgetData().taxBrackets()).hasSize(5);
        assertThat(persistenceManager.getBudgetData().taxBrackets().get(0).id()).isEqualTo("tb_1");
        assertThat(settings().birthYear()).isEqualTo(1975);
        assertThat(settings().retireAge()).isEqualTo(66);
    }

    @Test
    @DisplayName("Trésorerie : replace des paramètres puis reset aux valeurs par défaut")
    void tresorerieReplaceAndResetAreIsolated() {
        tresorerie.replace(new TresorerieSettingsModel("2027-01-01", "auto", new BigDecimal("1500"), true,
                new BigDecimal("9000"), new BigDecimal("500"), new BigDecimal("200")), null, null, null, null, null);

        assertThat(settings().pivotDate()).isEqualTo("2027-01-01");
        assertThat(settings().pivotMode()).isEqualTo("auto");
        assertThat(settings().startBalance()).isEqualByComparingTo("1500");
        assertThat(settings().sweepEnabled()).isTrue();
        assertThat(settings().cashCeiling()).isEqualByComparingTo("9000");
        assertThat(settings().cashFloor()).isEqualByComparingTo("500");
        assertThat(settings().cashAlertThreshold()).isEqualByComparingTo("200");
        assertThat(persistenceManager.getBudgetData().getEffectiveIncomes()).isEmpty();
        assertThat(settings().birthYear()).isEqualTo(1985);

        tresorerie.reset();

        assertThat(settings().pivotMode()).isEqualTo("manual");
        assertThat(settings().startBalance()).isEqualByComparingTo("0");
        assertThat(settings().sweepEnabled()).isFalse();
        assertThat(settings().cashCeiling()).isNull();
        assertThat(settings().cashFloor()).isNull();
        assertThat(settings().cashAlertThreshold()).isNull();
        assertThat(settings().birthYear()).isEqualTo(1985);
    }

    @Test
    @DisplayName("Simulation et hypothèses économiques : replace, replace(null) = défaut, reset")
    void simulationAndEconomicAssumptionsAreIsolated() {
        simulation.replace(new SimulationSettingsModel(90));
        economic.replace(new EconomicAssumptionsModel(new BigDecimal("0.03")));

        assertThat(settings().simulateUntilAge()).isEqualTo(90);
        assertThat(settings().inflationRate()).isEqualByComparingTo("0.03");

        simulation.replace(null);
        assertThat(settings().simulateUntilAge()).isEqualTo(85);
        assertThat(settings().inflationRate()).isEqualByComparingTo("0.03");

        economic.reset();
        assertThat(settings().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(settings().simulateUntilAge()).isEqualTo(85);

        simulation.replace(new SimulationSettingsModel(95));
        simulation.reset();
        assertThat(settings().simulateUntilAge()).isEqualTo(85);
    }

    @Test
    @DisplayName("Patrimoine, prêts, objectifs, banque : replace(null) et reset laissent des données vides")
    void listBasedSilosResetToEmpty() {
        patrimoine.replace(null, null, null, null);
        loans.replace(null);
        goals.replace(null);
        bank.reset();

        assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveRealEstate()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveTransfers()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveLoans()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveObjectifs()).isEmpty();
        assertThat(persistenceManager.getBudgetData().bankImport()).isNotNull();

        patrimoine.reset();
        loans.reset();
        goals.reset();

        assertThat(persistenceManager.getBudgetData().getEffectivePlacements()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveLoans()).isEmpty();
        assertThat(persistenceManager.getBudgetData().getEffectiveObjectifs()).isEmpty();
        assertThat(settings().birthYear()).isEqualTo(1985);
    }
}
