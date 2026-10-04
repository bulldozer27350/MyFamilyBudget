package com.moe.myfamilybudget.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.RetraiteApi;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.mapper.RetraiteMapper;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.application.model.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.application.model.RetraiteResultModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;

/**
 * Point d'entrée REST du domaine Retraite.
 *
 * RF-101 (doc/architecture/03-domaine-retraite.md) : ce service ne recalcule plus de projection
 * retraite en interne. Il compose {@link RetirementInputFactory} (construction de l'
 * {@link RetirementCalculationInput}) et {@link RetirementCalculationService} (moteur de calcul
 * pur), qui devient l'unique version canonique de ce calcul.
 *
 * RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Les lectures passent par les ports de domaine.
 *
 * SILO-110 : plus aucun {@code BudgetDataModel}. Le calcul lit ses fragments via
 * {@link RetirementSettingsReader}, {@link RetirementReader}, {@link TaxReader} (nombre
 * d'enfants) et {@link BudgetReader} (revenus). {@link SettingsReader} ne sert plus qu'au bloc
 * {@code settings} de la réponse REST, dont le contrat reste celui des façades composites
 * (voir 12-settings.md) ; il disparaît avec la composition applicative de ce bloc.
 */
@RestController
public class RetraiteServiceImpl implements RetraiteApi {

    private final RetraiteMapper retraiteMapper;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final RetirementCommandService retirementCommandService;
    private final SettingsReader settingsReader;
    private final RetirementSettingsReader retirementSettingsReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final BudgetReader budgetReader;

    public RetraiteServiceImpl(
        RetraiteMapper retraiteMapper,
        RetirementInputFactory retirementInputFactory,
        RetirementCalculationService retirementCalculationService,
        RetirementCommandService retirementCommandService,
        SettingsReader settingsReader,
        RetirementSettingsReader retirementSettingsReader,
        RetirementReader retirementReader,
        TaxReader taxReader,
        BudgetReader budgetReader
    ) {
        this.retraiteMapper = retraiteMapper;
        this.retirementInputFactory = retirementInputFactory;
        this.retirementCalculationService = retirementCalculationService;
        this.retirementCommandService = retirementCommandService;
        this.settingsReader = settingsReader;
        this.retirementSettingsReader = retirementSettingsReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.budgetReader = budgetReader;
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
            retirementCommandService.updateRetirement(model);
        }
        return ResponseEntity.ok().build();
    }

    public RetraiteResultModel buildRetraiteResult() {
        SettingsModel settings = settingsReader.getSettings();
        RetirementSettingsModel retirementSettings = retirementSettingsReader.getRetirementSettings();
        RetirementModel retirement = retirementReader.getRetirement();
        List<IncomeModel> incomes = budgetReader.getIncomes();

        int retireYear = retirementSettings.getEffectiveBirthYear() + retirementSettings.getEffectiveRetireAge();

        RetirementCalculationInput input = retirementInputFactory.create(
            retirementSettings, retirement, incomes, taxReader.getTaxChildren().size());
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
            incomes,
            settings
        );
    }
}
