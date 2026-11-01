package com.moe.myfamilybudget.domain.tax.core.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxMutatedEvent;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSnapshotWriter;
import com.moe.myfamilybudget.domain.tax.port.TaxWriter;

/**
 * Adaptateur JPA du silo Fiscalite (R-42, lot B) : implemente {@link TaxReader}, {@link TaxWriter},
 * {@link TaxSettingsReader} et {@link TaxSnapshotWriter} directement sur les tables {@code fiscal_*},
 * sans passer par le cache global ni par le {@code PersistenceManager}.
 *
 * <p>Le bareme par defaut (5 tranches) est un repli applique a la lecture quand {@code fiscal_bracket} est
 * vide, comme le barème historique de {@code BudgetDataModel.createDefaultBudgetData()}.
 */
@Component
@Primary
public class JpaTaxStore implements TaxReader, TaxWriter, TaxSettingsReader, TaxSnapshotWriter {

    private static final List<TaxBracketModel> DEFAULT_BRACKETS = List.of(
            new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
            new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
            new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
            new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
            new TaxBracketModel("tb_5", null, new BigDecimal("0.45")));

    private static final int DEFAULT_CHILD_EXIT_AGE = 21;
    private static final BigDecimal DEFAULT_TAX_ABATTEMENT = new BigDecimal("0.10");

    private final FiscalChildRepository childRepo;
    private final FiscalBracketRepository bracketRepo;
    private final FiscalRateOverrideRepository rateOverrideRepo;
    private final FiscalActualOverrideRepository actualOverrideRepo;
    private final FiscalSettingsRepository settingsRepo;
    private final ApplicationEventPublisher eventPublisher;

    public JpaTaxStore(FiscalChildRepository childRepo,
                       FiscalBracketRepository bracketRepo,
                       FiscalRateOverrideRepository rateOverrideRepo,
                       FiscalActualOverrideRepository actualOverrideRepo,
                       FiscalSettingsRepository settingsRepo,
                       ApplicationEventPublisher eventPublisher) {
        this.childRepo = childRepo;
        this.bracketRepo = bracketRepo;
        this.rateOverrideRepo = rateOverrideRepo;
        this.actualOverrideRepo = actualOverrideRepo;
        this.settingsRepo = settingsRepo;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // TaxReader
    // -------------------------------------------------------------------------

    @Override
    public List<TaxChildModel> getTaxChildren() {
        return FiscalEntityMapper.toChildModels(childRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TaxBracketModel> getTaxBrackets() {
        List<TaxBracketModel> models = FiscalEntityMapper.toBracketModels(bracketRepo.findAllByOrderByPositionAsc());
        return models.isEmpty() ? DEFAULT_BRACKETS : models;
    }

    @Override
    public List<TaxRateOverrideModel> getTaxRateOverrides() {
        return FiscalEntityMapper.toRateOverrideModels(rateOverrideRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TaxActualOverrideModel> getTaxActualOverrides() {
        return FiscalEntityMapper.toActualOverrideModels(actualOverrideRepo.findAllByOrderByPositionAsc());
    }

    // -------------------------------------------------------------------------
    // TaxSettingsReader
    // -------------------------------------------------------------------------

    @Override
    public TaxSettingsModel getTaxSettings() {
        return settingsRepo.findFirstByOrderByIdAsc()
                .map(FiscalSettingsMapper::toModel)
                .orElseGet(() -> new TaxSettingsModel(DEFAULT_CHILD_EXIT_AGE, DEFAULT_TAX_ABATTEMENT));
    }

    // -------------------------------------------------------------------------
    // TaxWriter
    // -------------------------------------------------------------------------

    @Override
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        if (children != null) {
            rewriteChildren(children);
        }
        if (brackets != null) {
            rewriteBrackets(brackets);
        }
        if (rateOverrides != null) {
            rewriteRateOverrides(rateOverrides);
        }
        if (actualOverrides != null) {
            rewriteActualOverrides(actualOverrides);
        }
        publishMutated("updateTaxConfig");
    }

    @Override
    public void updateTaxSettings(TaxSettingField field, Object value) {
        if (field == null) {
            return;
        }

        FiscalSettingsEntity entity = settingsRepo.findFirstByOrderByIdAsc()
                .orElseGet(FiscalSettingsEntity::new);

        switch (field) {
            case CHILD_EXIT_AGE -> entity.setChildExitAge(toInteger(value, DEFAULT_CHILD_EXIT_AGE));
            case TAX_ABATTEMENT -> entity.setTaxAbattement(toBigDecimal(value, DEFAULT_TAX_ABATTEMENT));
        }

        settingsRepo.save(entity);
        publishMutated("updateTaxSettings");
    }

    @Override
    public void resetDefaultTaxBrackets() {
        rewriteBrackets(DEFAULT_BRACKETS);
        publishMutated("resetDefaultTaxBrackets");
    }

    // -------------------------------------------------------------------------
    // TaxSnapshotWriter
    // -------------------------------------------------------------------------

    @Override
    public void replace(TaxSettingsModel settings, List<TaxChildModel> children, List<TaxBracketModel> brackets,
                        List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        rewriteChildren(orEmpty(children));
        rewriteBrackets(orEmpty(brackets));
        rewriteRateOverrides(orEmpty(rateOverrides));
        rewriteActualOverrides(orEmpty(actualOverrides));

        if (settings != null) {
            settingsRepo.deleteAll();
            settingsRepo.flush();
            settingsRepo.save(FiscalSettingsMapper.toEntity(settings));
        }

        publishMutated("replace");
    }

    @Override
    public void reset() {
        rewriteChildren(List.of());
        rewriteBrackets(List.of());
        rewriteRateOverrides(List.of());
        rewriteActualOverrides(List.of());
        settingsRepo.deleteAll();
        settingsRepo.flush();
        publishMutated("reset");
    }

    // -------------------------------------------------------------------------
    // Private rewrite helpers
    // -------------------------------------------------------------------------

    private void rewriteChildren(List<TaxChildModel> models) {
        childRepo.deleteAll();
        childRepo.flush();
        if (!models.isEmpty()) {
            childRepo.saveAll(FiscalEntityMapper.toChildEntities(models));
        }
    }

    private void rewriteBrackets(List<TaxBracketModel> models) {
        bracketRepo.deleteAll();
        bracketRepo.flush();
        if (!models.isEmpty()) {
            bracketRepo.saveAll(FiscalEntityMapper.toBracketEntities(models));
        }
    }

    private void rewriteRateOverrides(List<TaxRateOverrideModel> models) {
        rateOverrideRepo.deleteAll();
        rateOverrideRepo.flush();
        if (!models.isEmpty()) {
            rateOverrideRepo.saveAll(FiscalEntityMapper.toRateOverrideEntities(models));
        }
    }

    private void rewriteActualOverrides(List<TaxActualOverrideModel> models) {
        actualOverrideRepo.deleteAll();
        actualOverrideRepo.flush();
        if (!models.isEmpty()) {
            actualOverrideRepo.saveAll(FiscalEntityMapper.toActualOverrideEntities(models));
        }
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new TaxMutatedEvent(mutationKind));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
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
            return fallback;
        }
    }
}
