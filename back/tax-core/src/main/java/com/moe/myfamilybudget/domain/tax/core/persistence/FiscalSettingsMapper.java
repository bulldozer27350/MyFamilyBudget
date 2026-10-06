package com.moe.myfamilybudget.domain.tax.core.persistence;

import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;

/**
 * Mapper entre {@link TaxSettingsModel} et {@link FiscalSettingsEntity} (SILO-220, lot A).
 *
 * <p>Conversion sans perte : aucun défaut {@code getEffective*} n'est appliqué (une valeur absente reste
 * {@code null}, comme dans la table historique {@code settings}).
 */
public final class FiscalSettingsMapper {

    private FiscalSettingsMapper() {}

    public static FiscalSettingsEntity toEntity(TaxSettingsModel model) {
        if (model == null) {
            return null;
        }
        FiscalSettingsEntity entity = new FiscalSettingsEntity();
        entity.setChildExitAge(model.childExitAge());
        entity.setTaxAbattement(model.taxAbattement());
        return entity;
    }

    public static TaxSettingsModel toModel(FiscalSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaxSettingsModel(entity.getChildExitAge(), entity.getTaxAbattement());
    }
}
