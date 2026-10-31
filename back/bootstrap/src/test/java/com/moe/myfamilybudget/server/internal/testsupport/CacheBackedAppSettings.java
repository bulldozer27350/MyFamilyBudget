package com.moe.myfamilybudget.server.internal.testsupport;

import java.math.BigDecimal;

import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;

/**
 * Silo Parametres de test (R-50) pour les tests de services qui n'exercent pas la persistance du silo : tant qu'une
 * valeur n'a pas ete ecrite par un command service, la lecture retombe sur une valeur par defaut en dur (85 ans de
 * simulation, 2 % d'inflation — memes defauts que ceux portes historiquement par le hub) ; une valeur ecrite est
 * ensuite relue telle quelle. Une instance donnee doit etre passee a la fois comme lecteur et comme redacteur dans
 * un meme test, pour que les deux voient le meme etat (voir {@code new CacheBackedAppSettings()} utilise deux fois
 * dans la construction du service sous test). La vraie persistance ({@code JpaAppSettingsStore}) est couverte par
 * {@code JpaAppSettingsStoreTest} et par {@code PersistenceAdaptersJpaRoundTripTest}.
 *
 * <p>R-61-04 : ne depend plus du {@code PersistenceManager} du hub (retrait prevu par R-61,
 * {@code 26-sequencage-r61-suppression-hub.md}) ; le partage par instance de {@code PersistenceManager}
 * (WeakHashMap) est remplace par une simple instance creee par l'appelant.
 */
public final class CacheBackedAppSettings implements SimulationSettingsReader, EconomicAssumptionsReader,
        SimulationSettingsWriter, EconomicAssumptionsWriter {

    private static final int DEFAULT_SIMULATE_UNTIL_AGE = 85;
    private static final BigDecimal DEFAULT_INFLATION_RATE = new BigDecimal("0.02");

    private Integer simulateUntilAge;
    private BigDecimal inflationRate;

    public CacheBackedAppSettings() {
    }

    @Override
    public SimulationSettingsModel getSimulationSettings() {
        return new SimulationSettingsModel(simulateUntilAge != null ? simulateUntilAge : DEFAULT_SIMULATE_UNTIL_AGE);
    }

    @Override
    public EconomicAssumptionsModel getEconomicAssumptions() {
        return new EconomicAssumptionsModel(inflationRate != null ? inflationRate : DEFAULT_INFLATION_RATE);
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
            return DEFAULT_SIMULATE_UNTIL_AGE;
        }
    }

    private static BigDecimal parseBigDecimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return DEFAULT_INFLATION_RATE;
        }
    }
}
