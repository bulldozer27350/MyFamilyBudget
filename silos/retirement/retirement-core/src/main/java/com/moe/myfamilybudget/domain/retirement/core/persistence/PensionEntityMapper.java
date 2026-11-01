package com.moe.myfamilybudget.domain.retirement.core.persistence;

import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.RetirementPersonModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.SalaryHistoryModel;

/**
 * Mapper du domaine Retraite entre {@link RetirementModel} et {@link PensionPlanEntity} (DB-1000).
 *
 * <p>Additif : {@code EntityModelConverter} n'est pas modifie. Conversion sans perte des valeurs saisies
 * (aucun defaut {@code getEffective*} n'est applique) ; {@code position} est numerote dans l'ordre des
 * listes. Une liste {@code null} du modele (personnes, historique de salaires) est relue comme une liste
 * vide.
 */
public final class PensionEntityMapper {

    private PensionEntityMapper() {}

    public static PensionPlanEntity toEntity(RetirementModel model) {
        if (model == null) {
            return null;
        }
        PensionPlanEntity entity = new PensionPlanEntity();
        entity.setPass2026(model.pass2026());
        entity.setPassGrowthRate(model.passGrowthRate());
        entity.setAgircPointValue(model.agircPointValue());
        entity.setAgircPointDateGlobal(model.agircPointDateGlobal());
        entity.setAgircPointGrowthRate(model.agircPointGrowthRate());

        List<PensionPersonEntity> people = new ArrayList<>();
        int position = 0;
        for (RetirementPersonModel person : model.getEffectivePeople()) {
            PensionPersonEntity child = toEntity(person, entity, position++);
            if (child != null) {
                people.add(child);
            }
        }
        entity.setPeople(people);
        return entity;
    }

    public static RetirementModel toModel(PensionPlanEntity entity) {
        if (entity == null) {
            return null;
        }
        List<RetirementPersonModel> people = new ArrayList<>();
        for (PensionPersonEntity child : entity.getPeople()) {
            people.add(toModel(child));
        }
        return new RetirementModel(
                people,
                entity.getPass2026(),
                entity.getPassGrowthRate(),
                entity.getAgircPointValue(),
                entity.getAgircPointDateGlobal(),
                entity.getAgircPointGrowthRate());
    }

    private static PensionPersonEntity toEntity(RetirementPersonModel model, PensionPlanEntity plan, int position) {
        if (model == null) {
            return null;
        }
        PensionPersonEntity entity = new PensionPersonEntity();
        entity.setUid(model.id());
        entity.setPosition(position);
        entity.setName(model.name());
        entity.setBirthYear(model.birthYear());
        entity.setIncomeLabel(model.incomeLabel());
        entity.setTrimestresValides(model.trimestresValides());
        entity.setTrimestresDate(model.trimestresDate());
        entity.setAgircPoints(model.agircPoints());
        entity.setRatioPointsParEuro(model.ratioPointsParEuro());
        entity.setCadre(model.cadre());
        entity.setPlan(plan);

        List<PensionSalaryEntity> salaries = new ArrayList<>();
        int index = 0;
        for (SalaryHistoryModel salary : model.getEffectiveSalaryHistory()) {
            PensionSalaryEntity child = toEntity(salary, entity, index++);
            if (child != null) {
                salaries.add(child);
            }
        }
        entity.setSalaryHistory(salaries);
        return entity;
    }

    private static RetirementPersonModel toModel(PensionPersonEntity entity) {
        List<SalaryHistoryModel> salaries = new ArrayList<>();
        for (PensionSalaryEntity child : entity.getSalaryHistory()) {
            salaries.add(new SalaryHistoryModel(child.getYear(), child.getSalary()));
        }
        return new RetirementPersonModel(
                entity.getUid(),
                entity.getName(),
                entity.getBirthYear(),
                entity.getIncomeLabel(),
                entity.getTrimestresValides(),
                entity.getTrimestresDate(),
                salaries,
                entity.getAgircPoints(),
                entity.getRatioPointsParEuro(),
                entity.getCadre());
    }

    private static PensionSalaryEntity toEntity(SalaryHistoryModel model, PensionPersonEntity person, int position) {
        if (model == null) {
            return null;
        }
        PensionSalaryEntity entity = new PensionSalaryEntity();
        entity.setPosition(position);
        entity.setYear(model.year());
        entity.setSalary(model.salary());
        entity.setPerson(person);
        return entity;
    }
}
