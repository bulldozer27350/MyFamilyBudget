package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaAppSettingsStore;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaEconomicAssumptionsSnapshotWriter;
import com.moe.myfamilybudget.domain.settings.core.persistence.JpaSimulationSettingsSnapshotWriter;
import com.moe.myfamilybudget.domain.settings.model.AppSettingsMutatedEvent;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryAppSettingsRepository;

/**
 * R-50 -- Adaptateur du silo Parametres : lecture et ecriture directes de {@code simulateUntilAge} et
 * {@code inflationRate}, valeurs par defaut en l'absence de ligne, ports d'import et de reinitialisation, et
 * publication d'un evenement apres chaque ecriture. Le repository est en memoire ; le round-trip contre une vraie
 * base est couvert par {@code PersistenceAdaptersJpaRoundTripTest}.
 */
@DisplayName("R-50 -- JpaAppSettingsStore")
class JpaAppSettingsStoreTest {

    private final List<Object> events = new ArrayList<>();
    private AppSettingsRepository repository;
    private JpaAppSettingsStore store;
    private JpaSimulationSettingsSnapshotWriter simulationSnapshot;
    private JpaEconomicAssumptionsSnapshotWriter economicSnapshot;

    @BeforeEach
    void setUp() {
        repository = InMemoryAppSettingsRepository.create();
        store = new JpaAppSettingsStore(repository, events::add);
        simulationSnapshot = new JpaSimulationSettingsSnapshotWriter(store);
        economicSnapshot = new JpaEconomicAssumptionsSnapshotWriter(store);
    }

    @Test
    @DisplayName("sans ligne : les lectures renvoient les valeurs par defaut historiques et ne creent rien")
    void defaultsWhenNoRow() {
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(repository.count()).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("la premiere ecriture cree la ligne, l'autre parametre garde sa valeur par defaut")
    void firstWriteCreatesTheRowWithDefaultsForTheOtherParameter() {
        store.updateSimulateUntilAge(90);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(90);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");

        store.updateInflationRate(new BigDecimal("0.03"));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(90);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.03");
    }

    @Test
    @DisplayName("les valeurs sont lues comme avant : nombre, texte avec virgule, valeur illisible = defaut")
    void readsValuesLeniently() {
        store.updateSimulateUntilAge("92");
        store.updateInflationRate("0,035");
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(92);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.035");

        store.updateSimulateUntilAge(88.0);
        store.updateInflationRate(0.025);
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(88);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.025");

        store.updateSimulateUntilAge("abc");
        store.updateInflationRate("xyz");
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");

        store.updateSimulateUntilAge(null);
        store.updateInflationRate(null);
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
    }

    @Test
    @DisplayName("import : replace, replace(null) = defaut, reset ; chaque port ne touche que son parametre")
    void snapshotWritersAreIsolated() {
        simulationSnapshot.replace(new SimulationSettingsModel(90));
        economicSnapshot.replace(new EconomicAssumptionsModel(new BigDecimal("0.03")));

        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(90);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.03");

        simulationSnapshot.replace(null);
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.03");

        economicSnapshot.reset();
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);

        simulationSnapshot.replace(new SimulationSettingsModel(95));
        simulationSnapshot.reset();
        assertThat(store.getSimulationSettings().simulateUntilAge()).isEqualTo(85);

        economicSnapshot.replace(null);
        assertThat(store.getEconomicAssumptions().inflationRate()).isEqualByComparingTo("0.02");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("chaque ecriture publie un evenement de mutation des parametres")
    void everyWritePublishesAnEvent() {
        store.updateSimulateUntilAge(90);
        store.updateInflationRate(new BigDecimal("0.03"));
        simulationSnapshot.replace(new SimulationSettingsModel(91));
        simulationSnapshot.reset();
        economicSnapshot.replace(new EconomicAssumptionsModel(new BigDecimal("0.04")));
        economicSnapshot.reset();

        assertThat(events).containsExactly(
                new AppSettingsMutatedEvent("updateSimulateUntilAge"),
                new AppSettingsMutatedEvent("updateInflationRate"),
                new AppSettingsMutatedEvent("replaceSimulation"),
                new AppSettingsMutatedEvent("resetSimulation"),
                new AppSettingsMutatedEvent("replaceEconomicAssumptions"),
                new AppSettingsMutatedEvent("resetEconomicAssumptions"));
    }
}
