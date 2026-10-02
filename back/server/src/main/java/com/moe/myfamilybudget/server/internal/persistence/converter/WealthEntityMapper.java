package com.moe.myfamilybudget.server.internal.persistence.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.persistence.entity.WealthCategoryEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.WealthPlacementEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.WealthPlacementHistoryEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.WealthRealEstateEntity;

/**
 * Mapper du domaine Patrimoine entre les modeles ({@link PlacementModel}, {@link RealEstateModel},
 * {@link AssetCategoryModel}) et les entites autonomes {@code Wealth*Entity} (DB-1050).
 *
 * <p>Additif : {@code EntityModelConverter} n'est pas modifie. Conversions sans perte (aucun defaut
 * {@code getEffective*} n'est applique) ; les methodes {@code to*Entities} numerotent {@code position} dans
 * l'ordre de la liste. Une liste {@code null} (historique d'un placement) est relue comme une liste vide.
 */
public final class WealthEntityMapper {

    private WealthEntityMapper() {}

    // --- Placements (avec historique de valorisation)

    public static WealthPlacementEntity toEntity(PlacementModel model, int position) {
        if (model == null) {
            return null;
        }
        WealthPlacementEntity entity = new WealthPlacementEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setCategory(model.category());
        entity.setBalance(model.balance());
        entity.setBalanceDate(model.balanceDate());
        entity.setMonthly(model.monthly());
        entity.setMonthlyFrom(model.monthlyFrom());
        entity.setMonthlyUntil(model.monthlyUntil());
        entity.setRatePess(model.ratePess());
        entity.setRateCorr(model.rateCorr());
        entity.setRateOpti(model.rateOpti());
        entity.setExcludedFromRetirement(model.excludedFromRetirement());
        entity.setNotes(model.notes());
        entity.setSweepPriority(model.sweepPriority());
        entity.setSweepCap(model.sweepCap());
        entity.setPauseTriggerBalance(model.pauseTriggerBalance());
        entity.setPausePriority(model.pausePriority());
        entity.setCategoryId(model.categoryId());

        List<WealthPlacementHistoryEntity> history = new ArrayList<>();
        int index = 0;
        for (PlacementHistoryEntryModel entry : model.getEffectiveHistory()) {
            WealthPlacementHistoryEntity child = toEntity(entry, entity, index++);
            if (child != null) {
                history.add(child);
            }
        }
        entity.setHistory(history);
        return entity;
    }

    public static PlacementModel toModel(WealthPlacementEntity entity) {
        if (entity == null) {
            return null;
        }
        List<PlacementHistoryEntryModel> history = new ArrayList<>();
        for (WealthPlacementHistoryEntity child : entity.getHistory()) {
            history.add(new PlacementHistoryEntryModel(child.getUid(), child.getDate(), child.getValue(),
                    child.getNotes()));
        }
        return new PlacementModel(
                entity.getUid(),
                entity.getLabel(),
                entity.getCategory(),
                entity.getBalance(),
                entity.getBalanceDate(),
                entity.getMonthly(),
                entity.getMonthlyFrom(),
                entity.getMonthlyUntil(),
                entity.getRatePess(),
                entity.getRateCorr(),
                entity.getRateOpti(),
                entity.getExcludedFromRetirement(),
                entity.getNotes(),
                entity.getSweepPriority(),
                entity.getSweepCap(),
                entity.getPauseTriggerBalance(),
                entity.getPausePriority(),
                entity.getCategoryId(),
                history);
    }

    private static WealthPlacementHistoryEntity toEntity(PlacementHistoryEntryModel model,
                                                         WealthPlacementEntity placement, int position) {
        if (model == null) {
            return null;
        }
        WealthPlacementHistoryEntity entity = new WealthPlacementHistoryEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setDate(model.date());
        entity.setValue(model.value());
        entity.setNotes(model.notes());
        entity.setPlacement(placement);
        return entity;
    }

    // --- Immobilier

    public static WealthRealEstateEntity toEntity(RealEstateModel model, int position) {
        if (model == null) {
            return null;
        }
        WealthRealEstateEntity entity = new WealthRealEstateEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setType(model.type());
        entity.setCurrentValue(model.currentValue());
        entity.setValuationYear(model.valuationYear());
        entity.setAnnualGrowthRate(model.annualGrowthRate());
        entity.setNotes(model.notes());
        return entity;
    }

    public static RealEstateModel toModel(WealthRealEstateEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RealEstateModel(entity.getUid(), entity.getLabel(), entity.getType(), entity.getCurrentValue(),
                entity.getValuationYear(), entity.getAnnualGrowthRate(), entity.getNotes());
    }

    // --- Categories d'actifs

    public static WealthCategoryEntity toEntity(AssetCategoryModel model, int position) {
        if (model == null) {
            return null;
        }
        WealthCategoryEntity entity = new WealthCategoryEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setIcon(model.icon());
        entity.setName(model.name());
        entity.setBucket(model.bucket());
        entity.setColor(model.color());
        return entity;
    }

    public static AssetCategoryModel toModel(WealthCategoryEntity entity) {
        if (entity == null) {
            return null;
        }
        return new AssetCategoryModel(entity.getUid(), entity.getIcon(), entity.getName(), entity.getBucket(),
                entity.getColor());
    }

    // --- Listes

    public static List<WealthPlacementEntity> toPlacementEntities(List<PlacementModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<PlacementModel> toPlacementModels(List<WealthPlacementEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    public static List<WealthRealEstateEntity> toRealEstateEntities(List<RealEstateModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<RealEstateModel> toRealEstateModels(List<WealthRealEstateEntity> entities) {
        return toModels(entities, e -> toModel(e));
    }

    public static List<WealthCategoryEntity> toCategoryEntities(List<AssetCategoryModel> models) {
        return toEntities(models, (m, p) -> toEntity(m, p));
    }

    public static List<AssetCategoryModel> toCategoryModels(List<WealthCategoryEntity> entities) {
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
