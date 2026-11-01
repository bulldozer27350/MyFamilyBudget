package com.moe.myfamilybudget.domain.treasury.core.persistence;

import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * Mapper entre {@link TresorerieSettingsModel} et {@link CashflowSettingsEntity} (SILO-220, lot A).
 *
 * <p>Conversion sans perte : aucun défaut {@code getEffective*} n'est appliqué (une valeur absente reste
 * {@code null}, comme dans la table historique {@code settings}).
 */
public final class CashflowSettingsMapper {

    private CashflowSettingsMapper() {}

    public static CashflowSettingsEntity toEntity(TresorerieSettingsModel model) {
        if (model == null) {
            return null;
        }
        CashflowSettingsEntity entity = new CashflowSettingsEntity();
        entity.setPivotDate(model.pivotDate());
        entity.setPivotMode(model.pivotMode());
        entity.setStartBalance(model.startBalance());
        entity.setSweepEnabled(model.sweepEnabled());
        entity.setCashCeiling(model.cashCeiling());
        entity.setCashFloor(model.cashFloor());
        entity.setCashAlertThreshold(model.cashAlertThreshold());
        return entity;
    }

    public static TresorerieSettingsModel toModel(CashflowSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TresorerieSettingsModel(entity.getPivotDate(), entity.getPivotMode(), entity.getStartBalance(),
                entity.getSweepEnabled(), entity.getCashCeiling(), entity.getCashFloor(),
                entity.getCashAlertThreshold());
    }
}
