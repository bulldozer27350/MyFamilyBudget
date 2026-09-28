package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.RetraiteApi;
import com.moe.myfamilybudget.server.internal.calculation.RetirementCalculationInput;
import com.moe.myfamilybudget.server.internal.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.server.internal.factory.RetirementInputFactory;
import com.moe.myfamilybudget.server.internal.mapper.RetraiteMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjection;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.server.internal.model.RetraiteResultModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Point d'entrée REST du domaine Retraite.
 *
 * RF-101 (doc/architecture/03-domaine-retraite.md) : ce service ne recalcule plus de projection
 * retraite en interne. Il compose {@link RetirementInputFactory} (construction de l'
 * {@link RetirementCalculationInput} à partir de {@link BudgetDataModel}) et
 * {@link RetirementCalculationService} (moteur de calcul pur, sans dépendance à
 * {@code BudgetDataModel}), qui devient l'unique version canonique de ce calcul.
 */
@RestController
public class RetraiteServiceImpl implements RetraiteApi {

    private final PersistenceManager persistenceManager;
    private final RetraiteMapper retraiteMapper;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;

    public RetraiteServiceImpl(
        PersistenceManager persistenceManager,
        RetraiteMapper retraiteMapper,
        RetirementInputFactory retirementInputFactory,
        RetirementCalculationService retirementCalculationService
    ) {
        this.persistenceManager = persistenceManager;
        this.retraiteMapper = retraiteMapper;
        this.retirementInputFactory = retirementInputFactory;
        this.retirementCalculationService = retirementCalculationService;
    }

    @Override
    public ResponseEntity<Object> getRetraite() {
        RetraiteResultModel resultModel = buildRetraiteResult();
        Map<String, Object> response = retraiteMapper.toResponseMap(resultModel);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveRetraite(Object body) {
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            RetirementModel model = retraiteMapper.toRetirementModelFromMap(typedMap);
            persistenceManager.updateRetirement(model);
        }
        return ResponseEntity.ok().build();
    }

    public RetraiteResultModel buildRetraiteResult() {
        BudgetDataModel data = persistenceManager.getBudgetData();
        SettingsModel settings = data.getEffectiveSettings();

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();

        RetirementModel retirement = data.retirement();
        RetirementCalculationInput input = retirementInputFactory.create(data);
        RetirementProjection projection = retirementCalculationService.compute(input);

        List<RetirementModel.RetirementPersonModel> people = retirement != null
            ? retirement.getEffectivePeople()
            : List.of();
        List<RetirementProjectionModel> personProjections = projection.people();

        List<RetraitePersonWithProjectionModel> peopleWithProj = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) {
            RetirementModel.RetirementPersonModel person = people.get(i);
            RetirementProjectionModel proj = personProjections.get(i);
            peopleWithProj.add(new RetraitePersonWithProjectionModel(
                person.id(),
                person.name(),
                person.birthYear(),
                person.incomeLabel(),
                person.trimestresValides(),
                person.trimestresDate(),
                person.salaryHistory(),
                person.agircPoints(),
                person.ratioPointsParEuro(),
                person.cadre(),
                proj
            ));
        }

        RetraiteResultModel.RetirementWithProjectionsModel retWithProj = new RetraiteResultModel.RetirementWithProjectionsModel(
            peopleWithProj,
            retirement != null ? retirement.getEffectivePass2026() : new BigDecimal("47100"),
            retirement != null ? retirement.getEffectivePassGrowthRate() : new BigDecimal("0.015"),
            retirement != null ? retirement.getEffectiveAgircPointValue() : new BigDecimal("1.4386"),
            retirement != null ? retirement.agircPointDateGlobal() : "2025-11-01",
            retirement != null ? retirement.getEffectiveAgircPointGrowthRate() : new BigDecimal("0.01")
        );

        return new RetraiteResultModel(
            retWithProj,
            retireYear,
            data.getEffectiveIncomes(),
            settings
        );
    }
}
