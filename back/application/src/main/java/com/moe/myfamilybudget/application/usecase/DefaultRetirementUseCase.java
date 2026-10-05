package com.moe.myfamilybudget.application.usecase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteIncomeModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteProjectionModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteResultModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteSalaryHistoryModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteSettingsModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetirementUseCase;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationInput;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjectionModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;

/**
 * Implémentation du cas d'usage Retraite (SILO-300, lot B, pilote).
 *
 * <p>La logique de composition de la réponse (anciennement {@code RetraiteServiceImpl#buildRetraiteResult}) vit
 * ici, sans {@code ResponseEntity} ni DTO : elle lit ses fragments via les ports des silos
 * ({@link RetirementSettingsReader}, {@link RetirementReader}, {@link TaxReader} pour le nombre d'enfants,
 * {@link BudgetReader} pour les revenus), construit l'entrée du moteur par {@link RetirementInputFactory}, appelle
 * {@link RetirementCalculationService} puis traduit les types des silos vers les modèles de
 * {@code application-api}.
 *
 * <p>{@link SettingsReader} ne sert qu'au bloc {@code settings} de la réponse (contrat des façades
 * composites, voir 12-settings.md) ; il disparaît avec la composition applicative de ce bloc.
 */
@Service
public class DefaultRetirementUseCase implements RetirementUseCase {

    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final SettingsReader settingsReader;
    private final RetirementSettingsReader retirementSettingsReader;
    private final RetirementReader retirementReader;
    private final TaxReader taxReader;
    private final BudgetReader budgetReader;

    public DefaultRetirementUseCase(
        RetirementInputFactory retirementInputFactory,
        RetirementCalculationService retirementCalculationService,
        SettingsReader settingsReader,
        RetirementSettingsReader retirementSettingsReader,
        RetirementReader retirementReader,
        TaxReader taxReader,
        BudgetReader budgetReader
    ) {
        this.retirementInputFactory = retirementInputFactory;
        this.retirementCalculationService = retirementCalculationService;
        this.settingsReader = settingsReader;
        this.retirementSettingsReader = retirementSettingsReader;
        this.retirementReader = retirementReader;
        this.taxReader = taxReader;
        this.budgetReader = budgetReader;
    }

    @Override
    public RetraiteResultModel getRetraite() {
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
                toSalaryHistory(person.salaryHistory()),
                person.agircPoints(),
                person.ratioPointsParEuro(),
                person.cadre(),
                toProjection(proj)
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
            toIncomes(incomes),
            toSettings(settings)
        );
    }

    private static List<RetraiteSalaryHistoryModel> toSalaryHistory(List<RetirementModel.SalaryHistoryModel> history) {
        if (history == null) {
            return null;
        }
        List<RetraiteSalaryHistoryModel> result = new ArrayList<>(history.size());
        for (RetirementModel.SalaryHistoryModel entry : history) {
            result.add(new RetraiteSalaryHistoryModel(entry.year(), entry.salary()));
        }
        return result;
    }

    private static RetraiteProjectionModel toProjection(RetirementProjectionModel proj) {
        if (proj == null) {
            return null;
        }
        return new RetraiteProjectionModel(
            proj.ageDepart(),
            proj.trimestresValides(),
            proj.trimestresEstimesDepart(),
            proj.trimestresRequis(),
            proj.manqueTauxPlein(),
            proj.tauxApplique(),
            proj.decote(),
            proj.surcote(),
            proj.sam(),
            proj.majoration(),
            proj.pensionBaseAnnuelle(),
            proj.pointsEstimes(),
            proj.valeurPointDepart(),
            proj.pensionComplementaireAnnuelle(),
            proj.pensionTotaleAnnuelle(),
            proj.pensionTotaleMensuelle()
        );
    }

    private static List<RetraiteIncomeModel> toIncomes(List<IncomeModel> incomes) {
        if (incomes == null) {
            return null;
        }
        List<RetraiteIncomeModel> result = new ArrayList<>(incomes.size());
        for (IncomeModel income : incomes) {
            result.add(income == null ? null : new RetraiteIncomeModel(
                income.id(),
                income.label(),
                income.monthly(),
                income.start(),
                income.end(),
                income.growthRate(),
                income.categoryId(),
                income.notes()
            ));
        }
        return result;
    }

    private static RetraiteSettingsModel toSettings(SettingsModel settings) {
        if (settings == null) {
            return null;
        }
        return new RetraiteSettingsModel(
            settings.birthYear(),
            settings.retireAge(),
            settings.simulateUntilAge(),
            settings.inflationRate(),
            settings.pivotDate(),
            settings.pivotMode(),
            settings.startBalance(),
            settings.childExitAge(),
            settings.taxAbattement(),
            settings.sweepEnabled(),
            settings.cashCeiling(),
            settings.cashFloor(),
            settings.cashAlertThreshold()
        );
    }
}
