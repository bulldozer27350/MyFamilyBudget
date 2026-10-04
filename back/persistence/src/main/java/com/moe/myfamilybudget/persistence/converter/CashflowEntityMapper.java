package com.moe.myfamilybudget.persistence.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.persistence.entity.CashflowChargeEntity;
import com.moe.myfamilybudget.persistence.entity.CashflowIncomeEntity;
import com.moe.myfamilybudget.persistence.entity.CashflowOneOffEntity;
import com.moe.myfamilybudget.persistence.entity.CashflowTransferEntity;
import com.moe.myfamilybudget.persistence.entity.CashflowVariableIncomeEntity;
import com.moe.myfamilybudget.persistence.entity.CashflowVariableOverrideEntity;

/**
 * Mapper du domaine Tresorerie entre les modeles de lignes ({@code Income}, {@code Charge}, {@code OneOffExpense},
 * {@code Transfer}, {@code VariableIncome}, {@code VariableOverride}) et les entites autonomes
 * {@code Cashflow*Entity} (DB-1060).
 *
 * <p>Additif : {@code EntityModelConverter} n'est pas modifie. Conversions sans perte (aucun defaut
 * {@code getEffective*} n'est applique) ; les methodes {@code to*Entities} numerotent {@code position} dans
 * l'ordre de la liste et ignorent les elements {@code null}.
 */
public final class CashflowEntityMapper {

    private CashflowEntityMapper() {}

    // --- Revenu mensuel

    public static CashflowIncomeEntity toEntity(IncomeModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowIncomeEntity entity = new CashflowIncomeEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setMonthly(model.monthly());
        entity.setStart(model.start());
        entity.setEnd(model.end());
        entity.setGrowthRate(model.growthRate());
        entity.setCategoryId(model.categoryId());
        entity.setNotes(model.notes());
        return entity;
    }

    public static IncomeModel toModel(CashflowIncomeEntity entity) {
        if (entity == null) {
            return null;
        }
        return new IncomeModel(entity.getUid(), entity.getLabel(), entity.getMonthly(), entity.getStart(), entity.getEnd(), entity.getGrowthRate(), entity.getCategoryId(), entity.getNotes());
    }

    public static List<CashflowIncomeEntity> toIncomeEntities(List<IncomeModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<IncomeModel> toIncomeModels(List<CashflowIncomeEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    // --- Charge mensuelle

    public static CashflowChargeEntity toEntity(ChargeModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowChargeEntity entity = new CashflowChargeEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setMonthly(model.monthly());
        entity.setStart(model.start());
        entity.setEnd(model.end());
        entity.setGrowthRate(model.growthRate());
        entity.setCategoryId(model.categoryId());
        entity.setNotes(model.notes());
        return entity;
    }

    public static ChargeModel toModel(CashflowChargeEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ChargeModel(entity.getUid(), entity.getLabel(), entity.getMonthly(), entity.getStart(), entity.getEnd(), entity.getGrowthRate(), entity.getCategoryId(), entity.getNotes());
    }

    public static List<CashflowChargeEntity> toChargeEntities(List<ChargeModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<ChargeModel> toChargeModels(List<CashflowChargeEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    // --- Depense ponctuelle

    public static CashflowOneOffEntity toEntity(OneOffExpenseModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowOneOffEntity entity = new CashflowOneOffEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setDate(model.date());
        entity.setAmount(model.amount());
        entity.setNotes(model.notes());
        return entity;
    }

    public static OneOffExpenseModel toModel(CashflowOneOffEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OneOffExpenseModel(entity.getUid(), entity.getLabel(), entity.getDate(), entity.getAmount(), entity.getNotes());
    }

    public static List<CashflowOneOffEntity> toOneOffEntities(List<OneOffExpenseModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<OneOffExpenseModel> toOneOffModels(List<CashflowOneOffEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    // --- Virement vers un placement

    public static CashflowTransferEntity toEntity(TransferModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowTransferEntity entity = new CashflowTransferEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setPlacement(model.placement());
        entity.setDate(model.date());
        entity.setAmount(model.amount());
        entity.setNotes(model.notes());
        return entity;
    }

    public static TransferModel toModel(CashflowTransferEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TransferModel(entity.getUid(), entity.getPlacement(), entity.getDate(), entity.getAmount(), entity.getNotes());
    }

    public static List<CashflowTransferEntity> toTransferEntities(List<TransferModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<TransferModel> toTransferModels(List<CashflowTransferEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    // --- Regle de revenu variable (prime, bonus...)

    public static CashflowVariableIncomeEntity toEntity(VariableIncomeModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowVariableIncomeEntity entity = new CashflowVariableIncomeEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setRefIncomeLabel(model.refIncomeLabel());
        entity.setRate(model.rate());
        entity.setStartYear(model.startYear());
        entity.setEndYear(model.endYear());
        entity.setTaxable(model.taxable());
        entity.setType(model.type());
        entity.setNotes(model.notes());
        return entity;
    }

    public static VariableIncomeModel toModel(CashflowVariableIncomeEntity entity) {
        if (entity == null) {
            return null;
        }
        return new VariableIncomeModel(entity.getUid(), entity.getLabel(), entity.getRefIncomeLabel(), entity.getRate(), entity.getStartYear(), entity.getEndYear(), entity.getTaxable(), entity.getType(), entity.getNotes());
    }

    public static List<CashflowVariableIncomeEntity> toVariableIncomeEntities(List<VariableIncomeModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<VariableIncomeModel> toVariableIncomeModels(List<CashflowVariableIncomeEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    // --- Surcharge annuelle d'un revenu variable

    public static CashflowVariableOverrideEntity toEntity(VariableOverrideModel model, int position) {
        if (model == null) {
            return null;
        }
        CashflowVariableOverrideEntity entity = new CashflowVariableOverrideEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setYear(model.year());
        entity.setAmount(model.amount());
        entity.setTaxable(model.taxable());
        entity.setNotes(model.notes());
        return entity;
    }

    public static VariableOverrideModel toModel(CashflowVariableOverrideEntity entity) {
        if (entity == null) {
            return null;
        }
        return new VariableOverrideModel(entity.getUid(), entity.getLabel(), entity.getYear(), entity.getAmount(), entity.getTaxable(), entity.getNotes());
    }

    public static List<CashflowVariableOverrideEntity> toVariableOverrideEntities(List<VariableOverrideModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<VariableOverrideModel> toVariableOverrideModels(List<CashflowVariableOverrideEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    private static <M, E> List<E> toEntities(List<M> models, BiFunction<M, Integer, E> converter) {
        List<E> entities = new ArrayList<>();
        if (models == null) {
            return entities;
        }
        int position = 0;
        for (M model : models) {
            E entity = converter.apply(model, position++);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static <E, M> List<M> toModels(List<E> entities, Function<E, M> converter) {
        List<M> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (E entity : entities) {
            models.add(converter.apply(entity));
        }
        return models;
    }
}
