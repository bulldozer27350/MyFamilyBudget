package com.moe.myfamilybudget.domain.settings.core.persistence;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.settings.model.AppSettingsMutatedEvent;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsWriter;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsWriter;

/**
 * Adaptateur JPA du silo Parametres (R-50, DA-06) : lit et ecrit {@code simulateUntilAge} et {@code inflationRate}
 * directement dans la table {@code app_settings}, sans passer par le cache global ni par le
 * {@code PersistenceManager}. Implemente {@link SimulationSettingsReader}, {@link EconomicAssumptionsReader},
 * {@link SimulationSettingsWriter} et {@link EconomicAssumptionsWriter} ; les ports d'import et de reinitialisation
 * ({@code *SnapshotWriter}) portent chacun un {@code reset()} et sont donc deux classes distinctes
 * ({@link JpaSimulationSettingsSnapshotWriter}, {@link JpaEconomicAssumptionsSnapshotWriter}) qui s'appuient sur
 * les methodes {@code replace*} et {@code reset*} de cette classe.
 *
 * <p>Les ecritures ne s'appellent que dans une transaction deja ouverte ({@code TransactionRunner}) et apres la
 * prise du verrou du silo ({@code MutationSilo.SETTINGS}) par l'appelant : l'adaptateur ne porte ni
 * {@code @Transactional} ni verrou (DA-08). Les deux parametres partagent une meme ligne ; le verrou unique du silo
 * evite qu'une ecriture de l'un perde une ecriture concurrente de l'autre (DA-09).
 *
 * <p>Absence de ligne : les lectures renvoient les valeurs par defaut historiques (85 ans, 2 %), et une ecriture
 * cree la ligne avec ces valeurs pour le parametre qu'elle ne touche pas. Aucune synchronisation depuis l'ancienne
 * table {@code settings} : la ligne de {@code app_settings} a ete alimentee par le lot B1 de SILO-220 (a chaque
 * demarrage et a chaque sauvegarde) tant que la passerelle recopiait ; elle est desormais la seule source.
 *
 * <p>Apres chaque ecriture, un {@link AppSettingsMutatedEvent} est publie : l'ecoute apres commit declenche le
 * controle automatique des notifications, comme le faisait {@code BudgetMutatedEvent}.
 */
@Component
public class JpaAppSettingsStore implements SimulationSettingsReader, EconomicAssumptionsReader,
        SimulationSettingsWriter, EconomicAssumptionsWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JpaAppSettingsStore.class);

    /** Profondeur de simulation par defaut (age). */
    static final Integer DEFAULT_SIMULATE_UNTIL_AGE = 85;

    /** Taux d'inflation par defaut. */
    static final BigDecimal DEFAULT_INFLATION_RATE = new BigDecimal("0.02");

    private final AppSettingsRepository appSettingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaAppSettingsStore(AppSettingsRepository appSettingsRepository, ApplicationEventPublisher eventPublisher) {
        this.appSettingsRepository = appSettingsRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SimulationSettingsModel getSimulationSettings() {
        return appSettingsRepository.findFirstByOrderByIdAsc()
                .map(AppSettingsMapper::toSimulation)
                .orElseGet(() -> new SimulationSettingsModel(DEFAULT_SIMULATE_UNTIL_AGE));
    }

    @Override
    public EconomicAssumptionsModel getEconomicAssumptions() {
        return appSettingsRepository.findFirstByOrderByIdAsc()
                .map(AppSettingsMapper::toEconomicAssumptions)
                .orElseGet(() -> new EconomicAssumptionsModel(DEFAULT_INFLATION_RATE));
    }

    /** Met a jour {@code simulateUntilAge} ; une valeur illisible retombe sur la valeur par defaut historique. */
    @Override
    public void updateSimulateUntilAge(Object value) {
        writeSimulateUntilAge(toInteger(value, DEFAULT_SIMULATE_UNTIL_AGE));
        publishMutated("updateSimulateUntilAge");
    }

    /** Met a jour {@code inflationRate} ; une valeur illisible retombe sur la valeur par defaut historique. */
    @Override
    public void updateInflationRate(Object value) {
        writeInflationRate(toBigDecimal(value, DEFAULT_INFLATION_RATE));
        publishMutated("updateInflationRate");
    }

    /** Import : remplace le parametre de simulation ({@code null} : valeur par defaut). */
    public void replaceSimulation(SimulationSettingsModel settings) {
        writeSimulateUntilAge(settings != null ? settings.simulateUntilAge() : DEFAULT_SIMULATE_UNTIL_AGE);
        publishMutated("replaceSimulation");
    }

    /** Remet le parametre de simulation a sa valeur par defaut. */
    public void resetSimulation() {
        writeSimulateUntilAge(DEFAULT_SIMULATE_UNTIL_AGE);
        publishMutated("resetSimulation");
    }

    /** Import : remplace les hypotheses economiques ({@code null} : valeur par defaut). */
    public void replaceEconomicAssumptions(EconomicAssumptionsModel assumptions) {
        writeInflationRate(assumptions != null ? assumptions.inflationRate() : DEFAULT_INFLATION_RATE);
        publishMutated("replaceEconomicAssumptions");
    }

    /** Remet les hypotheses economiques a leurs valeurs par defaut. */
    public void resetEconomicAssumptions() {
        writeInflationRate(DEFAULT_INFLATION_RATE);
        publishMutated("resetEconomicAssumptions");
    }

    private void writeSimulateUntilAge(Integer simulateUntilAge) {
        AppSettingsEntity entity = currentOrDefaults();
        entity.setSimulateUntilAge(simulateUntilAge);
        appSettingsRepository.save(entity);
    }

    private void writeInflationRate(BigDecimal inflationRate) {
        AppSettingsEntity entity = currentOrDefaults();
        entity.setInflationRate(inflationRate);
        appSettingsRepository.save(entity);
    }

    /** Ligne courante, ou une ligne neuve portant les valeurs par defaut des deux parametres. */
    private AppSettingsEntity currentOrDefaults() {
        return appSettingsRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            AppSettingsEntity entity = new AppSettingsEntity();
            entity.setSimulateUntilAge(DEFAULT_SIMULATE_UNTIL_AGE);
            entity.setInflationRate(DEFAULT_INFLATION_RATE);
            return entity;
        });
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new AppSettingsMutatedEvent(mutationKind));
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return fallback;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            LOG.warn("Valeur numerique decimale illisible, valeur par defaut '{}' utilisee : '{}'", fallback, value, e);
            return fallback;
        }
    }

    private static Integer toInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Exception e) {
            LOG.warn("Valeur entiere illisible, valeur par defaut '{}' utilisee : '{}'", fallback, value, e);
            return fallback;
        }
    }
}
