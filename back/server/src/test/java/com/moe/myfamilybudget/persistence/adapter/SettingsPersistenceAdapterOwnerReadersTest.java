package com.moe.myfamilybudget.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;
import com.moe.myfamilybudget.transition.model.SettingsModel;

/**
 * SILO-100 -- Les paramètres lus par propriétaire (Retraite, Fiscalité, Trésorerie, Simulation, Hypothèses
 * économiques) sont la projection exacte de {@code SettingsReader#getSettings()}, y compris après une mutation
 * par les ports d'écriture existants.
 */
@DisplayName("SILO-100 -- SettingsPersistenceAdapter : lectures par propriétaire")
class SettingsPersistenceAdapterOwnerReadersTest {

    private PersistenceManager persistenceManager;
    private SettingsPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        adapter = new SettingsPersistenceAdapter(persistenceManager);
    }

    @Test
    @DisplayName("base vierge : les valeurs par défaut de SettingsModel sont reprises par propriétaire")
    void defaultsAreProjectedPerOwner() {
        assertThat(adapter.getRetirementSettings().getEffectiveBirthYear()).isEqualTo(1985);
        assertThat(adapter.getRetirementSettings().getEffectiveRetireAge()).isEqualTo(64);
        assertThat(adapter.getTaxSettings().getEffectiveChildExitAge()).isEqualTo(21);
        assertThat(adapter.getTaxSettings().getEffectiveTaxAbattement()).isEqualByComparingTo("0.10");
        assertThat(adapter.getTresorerieSettings().pivotMode()).isEqualTo("manual");
        assertThat(adapter.getTresorerieSettings().getEffectiveStartBalance()).isEqualByComparingTo("0");
        assertThat(adapter.getTresorerieSettings().getEffectiveSweepEnabled()).isFalse();
        assertThat(adapter.getSimulationSettings().getEffectiveSimulateUntilAge()).isEqualTo(85);
        assertThat(adapter.getEconomicAssumptions().getEffectiveInflationRate()).isEqualByComparingTo("0.02");
    }

    @Test
    @DisplayName("après import : chaque propriétaire relit exactement ses champs")
    void importedSettingsAreProjectedPerOwner() {
        SettingsModel settings = new SettingsModel(1990, 62, 90, new BigDecimal("0.025"), "2026-01-01", "auto",
                new BigDecimal("5000"), 23, new BigDecimal("0.05"), true, new BigDecimal("20000"),
                new BigDecimal("1000"), new BigDecimal("500"));
        persistenceManager.setBudgetData(persistenceManager.getBudgetData().withSettings(settings));

        assertThat(adapter.getRetirementSettings().birthYear()).isEqualTo(1990);
        assertThat(adapter.getRetirementSettings().retireAge()).isEqualTo(62);
        assertThat(adapter.getTaxSettings().childExitAge()).isEqualTo(23);
        assertThat(adapter.getTaxSettings().taxAbattement()).isEqualByComparingTo("0.05");
        assertThat(adapter.getTresorerieSettings().pivotDate()).isEqualTo("2026-01-01");
        assertThat(adapter.getTresorerieSettings().pivotMode()).isEqualTo("auto");
        assertThat(adapter.getTresorerieSettings().startBalance()).isEqualByComparingTo("5000");
        assertThat(adapter.getTresorerieSettings().getEffectiveSweepEnabled()).isTrue();
        assertThat(adapter.getTresorerieSettings().cashCeiling()).isEqualByComparingTo("20000");
        assertThat(adapter.getTresorerieSettings().cashFloor()).isEqualByComparingTo("1000");
        assertThat(adapter.getTresorerieSettings().cashAlertThreshold()).isEqualByComparingTo("500");
        assertThat(adapter.getSimulationSettings().simulateUntilAge()).isEqualTo(90);
        assertThat(adapter.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("les écritures Simulation et Hypothèses économiques sont relues par leur propre reader")
    void writesAreReadBackByOwnReaders() {
        adapter.updateSimulateUntilAge(95);
        adapter.updateInflationRate(new BigDecimal("0.03"));

        assertThat(adapter.getSimulationSettings().simulateUntilAge()).isEqualTo(95);
        assertThat(adapter.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.03");
        assertThat(adapter.getSettings().simulateUntilAge()).isEqualTo(95);
    }
}
