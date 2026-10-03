package com.moe.myfamilybudget.persistence.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.persistence.entity.CreditLoanEntity;

/**
 * Mapper du domaine Credit entre {@link LoanModel} et {@link CreditLoanEntity} (DB-1040).
 *
 * <p>Additif : {@code EntityModelConverter} n'est pas modifie. La conversion est sans perte.
 */
public final class CreditLoanEntityMapper {

    private CreditLoanEntityMapper() {}

    public static CreditLoanEntity toEntity(LoanModel model, int position) {
        if (model == null) {
            return null;
        }
        Objects.requireNonNull(model.id(), "L'identifiant d'un pret est obligatoire");
        CreditLoanEntity entity = new CreditLoanEntity();
        entity.setId(model.id());
        entity.setPosition(position);
        entity.setLabel(model.label());
        entity.setCrd(model.crd());
        entity.setRate(model.rate());
        entity.setMonthly(model.monthly());
        entity.setInsurance(model.insurance());
        entity.setStartDate(model.startDate());
        entity.setEndDate(model.endDate());
        entity.setInitialAmount(model.initialAmount());
        entity.setTotalInstallments(model.totalInstallments());
        entity.setStepDate(model.stepDate());
        return entity;
    }

    public static LoanModel toModel(CreditLoanEntity entity) {
        if (entity == null) {
            return null;
        }
        return new LoanModel(
                entity.getId(),
                entity.getLabel(),
                entity.getCrd(),
                entity.getRate(),
                entity.getMonthly(),
                entity.getInsurance(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getInitialAmount(),
                entity.getTotalInstallments(),
                entity.getStepDate());
    }

    public static List<CreditLoanEntity> toEntities(List<LoanModel> models) {
        List<CreditLoanEntity> entities = new ArrayList<>();
        if (models == null) {
            return entities;
        }
        int position = 0;
        for (LoanModel model : models) {
            CreditLoanEntity entity = toEntity(model, position++);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    public static List<LoanModel> toModels(List<CreditLoanEntity> entities) {
        List<LoanModel> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (CreditLoanEntity entity : entities) {
            models.add(toModel(entity));
        }
        return models;
    }
}
