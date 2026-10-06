package com.moe.myfamilybudget.domain.retirement.core.persistence;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;

/**
 * Mapper entre {@link RetirementSettingsModel} et {@link PensionSettingsEntity} (SILO-220, lot A).
 *
 * <p>Conversion sans perte : aucun défaut {@code getEffective*} n'est appliqué (une valeur absente reste
 * {@code null}, comme dans la table historique {@code settings}).
 */
public final class PensionSettingsMapper {

    private PensionSettingsMapper() {}

    public static PensionSettingsEntity toEntity(RetirementSettingsModel model) {
        if (model == null) {
            return null;
        }
        PensionSettingsEntity entity = new PensionSettingsEntity();
        entity.setBirthYear(model.birthYear());
        entity.setRetireAge(model.retireAge());
        return entity;
    }

    public static RetirementSettingsModel toModel(PensionSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RetirementSettingsModel(entity.getBirthYear(), entity.getRetireAge());
    }
}
