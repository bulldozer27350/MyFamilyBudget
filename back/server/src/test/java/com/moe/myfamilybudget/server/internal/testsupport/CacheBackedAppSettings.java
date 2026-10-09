package com.moe.myfamilybudget.server.internal.testsupport;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;
import com.moe.myfamilybudget.persistence.PersistenceManager;

/**
 * Silo Parametres de test (R-50) pour les tests de services qui n'exercent pas la persistance du silo : tant qu'une
 * valeur n'a pas ete ecrite par un command service, la lecture retombe sur le {@code SettingsModel} du cache du
 * {@link PersistenceManager} de test (c'est ainsi que ces tests posent la profondeur de simulation et l'inflation
 * depuis un {@code BudgetDataModel}) ; une valeur ecrite est ensuite relue telle quelle. Une seule instance est
 * partagee par {@code PersistenceManager}, pour que les lecteurs et les command services d'un meme test voient le
 * meme etat. La vraie persistance ({@code JpaAppSettingsStore}) est couverte par {@code JpaAppSettingsStoreTest} et
 * par {@code PersistenceAdaptersJpaRoundTripTest}.
 */
public final class CacheBackedAppSettings implements SimulationSettingsReader, EconomicAssumptionsReader,
        SimulationSettingsWriter, EconomicAssumptionsWriter {

    private static final Map<PersistenceManager, CacheBackedAppSettings> BY_MANAGER =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final PersistenceManager persistenceManager;
    private Integer simulateUntilAge;
    private BigDecimal inflationRate;

    private CacheBackedAppSettings(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    /** Instance partagee du {@code persistenceManager} donne. */
    public static CacheBackedAppSettings of(PersistenceManager persistenceManager) {
        return BY_MANAGER.computeIfAbsent(persistenceManager, CacheBackedAppSettings::new);
    }

    @Override
    public SimulationSettingsModel getSimulationSettings() {
        return new SimulationSettingsModel(simulateUntilAge != null ? simulateUntilAge
                : persistenceManager.getBudgetData().getEffectiveSettings().simulateUntilAge());
    }

    @Override
    public EconomicAssumptionsModel getEconomicAssumptions() {
        return new EconomicAssumptionsModel(inflationRate != null ? inflationRate
                : persistenceManager.getBudgetData().getEffectiveSettings().inflationRate());
    }

    @Override
    public void updateSimulateUntilAge(Object value) {
        simulateUntilAge = value instanceof Number number ? Integer.valueOf(number.intValue())
                : parseInteger(value);
    }

    @Override
    public void updateInflationRate(Object value) {
        inflationRate = value instanceof BigDecimal bd ? bd
                : value instanceof Number number ? BigDecimal.valueOf(number.doubleValue())
                : parseBigDecimal(value);
    }

    private static Integer parseInteger(Object value) {
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 85;
        }
    }

    private static BigDecimal parseBigDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return new BigDecimal("0.02");
        }
    }
}
