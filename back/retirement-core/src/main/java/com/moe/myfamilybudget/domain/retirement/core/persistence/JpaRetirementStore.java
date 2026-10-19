package com.moe.myfamilybudget.domain.retirement.core.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementMutatedEvent;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSnapshotWriter;
import com.moe.myfamilybudget.domain.retirement.port.RetirementWriter;

/**
 * Adaptateur JPA du silo Retraite (R-40, lot B, meme gabarit que {@code JpaTaxStore}/R-42) : implemente
 * {@link RetirementReader}, {@link RetirementWriter}, {@link RetirementSettingsReader} et
 * {@link RetirementSnapshotWriter} directement sur {@code pension_plan} (+ {@code pension_person},
 * {@code pension_salary}) et {@code pension_settings}, sans passer par le cache global
 * ({@code PersistenceManager}) ni par {@code RetirementPersistenceAdapter} (retire).
 *
 * <p>Comme pour la Fiscalite, chaque table n'a jamais plus d'une ligne : toute ecriture efface puis
 * reinsere (pas de mise a jour cellule a cellule). {@code pension_plan} etant un objet (pas une liste),
 * une lecture a vide retombe sur {@link #DEFAULT_RETIREMENT} plutot que {@code null}, pour que l'absence de
 * ligne (base neuve, avant toute sauvegarde) se comporte comme les valeurs par defaut historiques
 * (voir {@code BudgetCacheStore#createDefaultBudgetData}), que les appelants (facades REST, calcul de
 * projection) continuent de recevoir sans verification de nullite supplementaire.
 */
@Component
@Primary
public class JpaRetirementStore
        implements RetirementReader, RetirementWriter, RetirementSettingsReader, RetirementSnapshotWriter {

    private static final int DEFAULT_BIRTH_YEAR = 1985;
    private static final int DEFAULT_RETIRE_AGE = 64;
    private static final BigDecimal DEFAULT_PASS_2026 = new BigDecimal("47100");
    private static final BigDecimal DEFAULT_PASS_GROWTH_RATE = new BigDecimal("0.015");
    private static final BigDecimal DEFAULT_AGIRC_POINT_VALUE = new BigDecimal("1.4386");
    private static final String DEFAULT_AGIRC_POINT_DATE_GLOBAL = "2025-11-01";
    private static final BigDecimal DEFAULT_AGIRC_POINT_GROWTH_RATE = new BigDecimal("0.01");

    private static final RetirementModel DEFAULT_RETIREMENT = new RetirementModel(
            List.of(), DEFAULT_PASS_2026, DEFAULT_PASS_GROWTH_RATE, DEFAULT_AGIRC_POINT_VALUE,
            DEFAULT_AGIRC_POINT_DATE_GLOBAL, DEFAULT_AGIRC_POINT_GROWTH_RATE);

    private final PensionPlanRepository planRepository;
    private final PensionSettingsRepository settingsRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaRetirementStore(PensionPlanRepository planRepository, PensionSettingsRepository settingsRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.planRepository = planRepository;
        this.settingsRepository = settingsRepository;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------------------------------
    // RetirementReader
    // -------------------------------------------------------------------------------------------------

    @Override
    public RetirementModel getRetirement() {
        return planRepository.findFirstByOrderByIdAsc()
                .map(PensionEntityMapper::toModel)
                .orElse(DEFAULT_RETIREMENT);
    }

    // -------------------------------------------------------------------------------------------------
    // RetirementSettingsReader
    // -------------------------------------------------------------------------------------------------

    @Override
    public RetirementSettingsModel getRetirementSettings() {
        return settingsRepository.findFirstByOrderByIdAsc()
                .map(PensionSettingsMapper::toModel)
                .orElseGet(() -> new RetirementSettingsModel(DEFAULT_BIRTH_YEAR, DEFAULT_RETIRE_AGE));
    }

    // -------------------------------------------------------------------------------------------------
    // RetirementWriter
    // -------------------------------------------------------------------------------------------------

    @Override
    public void updateRetirement(RetirementModel retirement) {
        rewritePlan(retirement);
        publishMutated("updateRetirement");
    }

    @Override
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        if (field == null) {
            return;
        }
        switch (field) {
            case BIRTH_YEAR -> updateSettingsField(e -> e.setBirthYear(toInteger(value, DEFAULT_BIRTH_YEAR)));
            case RETIRE_AGE -> updateSettingsField(e -> e.setRetireAge(toInteger(value, DEFAULT_RETIRE_AGE)));
            case PASS_2026, PASS_GROWTH_RATE -> updatePass(field, value);
        }
        publishMutated("updateRetirementSetting");
    }

    // -------------------------------------------------------------------------------------------------
    // RetirementSnapshotWriter (SILO-119 lot B1)
    // -------------------------------------------------------------------------------------------------

    @Override
    public void replace(RetirementSettingsModel settings, RetirementModel retirement) {
        Integer birthYear = settings != null ? settings.birthYear() : DEFAULT_BIRTH_YEAR;
        Integer retireAge = settings != null ? settings.retireAge() : DEFAULT_RETIRE_AGE;
        rewriteSettings(new RetirementSettingsModel(birthYear, retireAge));
        rewritePlan(retirement);
        publishMutated("replace");
    }

    @Override
    public void reset() {
        rewriteSettings(null);
        rewritePlan(null);
        publishMutated("reset");
    }

    // -------------------------------------------------------------------------------------------------
    // Prive
    // -------------------------------------------------------------------------------------------------

    private void updatePass(RetirementSettingField field, Object value) {
        RetirementModel base = getRetirement();
        BigDecimal pass2026 = base.pass2026();
        BigDecimal passGrowthRate = base.passGrowthRate();
        if (field == RetirementSettingField.PASS_2026) {
            pass2026 = toBigDecimal(value, DEFAULT_PASS_2026);
        } else if (field == RetirementSettingField.PASS_GROWTH_RATE) {
            passGrowthRate = toBigDecimal(value, DEFAULT_PASS_GROWTH_RATE);
        }
        rewritePlan(new RetirementModel(base.getEffectivePeople(), pass2026, passGrowthRate,
                base.agircPointValue(), base.agircPointDateGlobal(), base.agircPointGrowthRate()));
    }

    private void updateSettingsField(Consumer<PensionSettingsEntity> mutation) {
        PensionSettingsEntity entity = settingsRepository.findFirstByOrderByIdAsc()
                .orElseGet(PensionSettingsEntity::new);
        mutation.accept(entity);
        settingsRepository.save(entity);
    }

    private void rewritePlan(RetirementModel model) {
        planRepository.deleteAll();
        planRepository.flush();
        if (model != null) {
            planRepository.save(PensionEntityMapper.toEntity(model));
        }
    }

    private void rewriteSettings(RetirementSettingsModel model) {
        settingsRepository.deleteAll();
        settingsRepository.flush();
        if (model != null) {
            settingsRepository.save(PensionSettingsMapper.toEntity(model));
        }
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new RetirementMutatedEvent(mutationKind));
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
        } catch (RuntimeException e) {
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
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
