package com.moe.myfamilybudget.server.internal.persistence.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.persistence.entity.GoalAllocationEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.GoalEntity;

/**
 * Mapper du domaine Objectifs entre {@link ObjectifModel} et {@link GoalEntity} (DB-1020).
 *
 * <p>Additif : {@code EntityModelConverter} n'est pas modifie. La conversion est volontairement
 * sans perte et ne rejoue pas {@code LegacyObjectifAllocationMigrator} ; le choix de conserver ou
 * non ce filet de securite releve de la bascule (DB-1021).
 */
public final class GoalEntityMapper {

    private GoalEntityMapper() {}

    public static GoalEntity toEntity(ObjectifModel model, int position) {
        if (model == null) {
            return null;
        }
        Objects.requireNonNull(model.id(), "L'identifiant d'un objectif est obligatoire");
        GoalEntity entity = new GoalEntity();
        entity.setId(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setTargetAmount(model.targetAmount());
        entity.setAllocatedAmount(model.allocatedAmount());
        entity.setTargetDate(model.targetDate());
        entity.setSourcePlacementId(model.sourcePlacementId());
        entity.setNotes(model.notes());

        List<GoalAllocationEntity> allocations = new ArrayList<>();
        int index = 0;
        for (ObjectifAllocationModel allocation : model.getEffectiveAllocations()) {
            GoalAllocationEntity child = toEntity(allocation, entity, index++);
            if (child != null) {
                allocations.add(child);
            }
        }
        entity.setAllocations(allocations);
        return entity;
    }

    public static ObjectifModel toModel(GoalEntity entity) {
        if (entity == null) {
            return null;
        }
        List<ObjectifAllocationModel> allocations = new ArrayList<>();
        for (GoalAllocationEntity child : entity.getAllocations()) {
            allocations.add(toModel(child));
        }
        return new ObjectifModel(
                entity.getId(),
                entity.getLabel(),
                entity.getTargetAmount(),
                entity.getAllocatedAmount(),
                entity.getTargetDate(),
                entity.getSourcePlacementId(),
                entity.getNotes(),
                allocations);
    }

    public static List<GoalEntity> toEntities(List<ObjectifModel> models) {
        List<GoalEntity> entities = new ArrayList<>();
        if (models == null) {
            return entities;
        }
        int position = 0;
        for (ObjectifModel model : models) {
            GoalEntity entity = toEntity(model, position++);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    public static List<ObjectifModel> toModels(List<GoalEntity> entities) {
        List<ObjectifModel> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (GoalEntity entity : entities) {
            models.add(toModel(entity));
        }
        return models;
    }

    private static GoalAllocationEntity toEntity(ObjectifAllocationModel model, GoalEntity goal, int position) {
        if (model == null) {
            return null;
        }
        GoalAllocationEntity entity = new GoalAllocationEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setPlacementId(model.placementId());
        entity.setAmount(model.amount());
        entity.setGoal(goal);
        return entity;
    }

    private static ObjectifAllocationModel toModel(GoalAllocationEntity entity) {
        return new ObjectifAllocationModel(entity.getUid(), entity.getPlacementId(), entity.getAmount());
    }
}
