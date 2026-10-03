package com.moe.myfamilybudget.persistence.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.persistence.entity.FiscalActualOverrideEntity;
import com.moe.myfamilybudget.persistence.entity.FiscalBracketEntity;
import com.moe.myfamilybudget.persistence.entity.FiscalChildEntity;
import com.moe.myfamilybudget.persistence.entity.FiscalRateOverrideEntity;

/**
 * Mapper du domaine Fiscalite entre les modeles ({@code Tax*Model}) et les entites autonomes
 * ({@code Fiscal*Entity}, DB-1010). Additif : {@code EntityModelConverter} n'est pas modifie. Conversions sans
 * perte ; les methodes {@code toEntities} numerotent {@code position} dans l'ordre de la liste.
 */
public final class FiscalEntityMapper {

    private FiscalEntityMapper() {}

    // --- Enfants a charge

    public static FiscalChildEntity toEntity(TaxChildModel model, int position) {
        if (model == null) {
            return null;
        }
        FiscalChildEntity entity = new FiscalChildEntity();
        entity.setPosition(position);
        entity.setUid(model.id());
        entity.setName(model.name());
        entity.setBirthYear(model.birthYear());
        return entity;
    }

    public static TaxChildModel toModel(FiscalChildEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaxChildModel(entity.getUid(), entity.getName(), entity.getBirthYear());
    }

    // --- Tranches du bareme

    public static FiscalBracketEntity toEntity(TaxBracketModel model, int position) {
        if (model == null) {
            return null;
        }
        FiscalBracketEntity entity = new FiscalBracketEntity();
        entity.setPosition(position);
        entity.setUid(model.id());
        entity.setUpTo(model.upTo());
        entity.setRate(model.rate());
        return entity;
    }

    public static TaxBracketModel toModel(FiscalBracketEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaxBracketModel(entity.getUid(), entity.getUpTo(), entity.getRate());
    }

    // --- Taux forces par annee

    public static FiscalRateOverrideEntity toEntity(TaxRateOverrideModel model, int position) {
        if (model == null) {
            return null;
        }
        FiscalRateOverrideEntity entity = new FiscalRateOverrideEntity();
        entity.setPosition(position);
        entity.setYear(model.year());
        entity.setRate(model.rate());
        return entity;
    }

    public static TaxRateOverrideModel toModel(FiscalRateOverrideEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaxRateOverrideModel(entity.getYear(), entity.getRate());
    }

    // --- Impots reels par annee

    public static FiscalActualOverrideEntity toEntity(TaxActualOverrideModel model, int position) {
        if (model == null) {
            return null;
        }
        FiscalActualOverrideEntity entity = new FiscalActualOverrideEntity();
        entity.setPosition(position);
        entity.setYear(model.year());
        entity.setAmount(model.amount());
        return entity;
    }

    public static TaxActualOverrideModel toModel(FiscalActualOverrideEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TaxActualOverrideModel(entity.getYear(), entity.getAmount());
    }

    // --- Listes

    public static List<FiscalChildEntity> toChildEntities(List<TaxChildModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<TaxChildModel> toChildModels(List<FiscalChildEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    public static List<FiscalBracketEntity> toBracketEntities(List<TaxBracketModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<TaxBracketModel> toBracketModels(List<FiscalBracketEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    public static List<FiscalRateOverrideEntity> toRateOverrideEntities(List<TaxRateOverrideModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<TaxRateOverrideModel> toRateOverrideModels(List<FiscalRateOverrideEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    public static List<FiscalActualOverrideEntity> toActualOverrideEntities(List<TaxActualOverrideModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<TaxActualOverrideModel> toActualOverrideModels(List<FiscalActualOverrideEntity> entities) {
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
