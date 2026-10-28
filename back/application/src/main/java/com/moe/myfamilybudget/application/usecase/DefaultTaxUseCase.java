package com.moe.myfamilybudget.application.usecase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.application.factory.PatrimoineTransferConverter;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.factory.TaxBracketDefaults;
import com.moe.myfamilybudget.application.factory.TaxInputFactory;
import com.moe.myfamilybudget.application.factory.TaxSimulationPeriodResolver;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.settings.SettingsView;
import com.moe.myfamilybudget.application.usecase.tax.TaxActualOverrideModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxBracketModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxChildModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxConfigSaveCommand;
import com.moe.myfamilybudget.application.usecase.tax.TaxRateOverrideModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxResultModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxSettingsViewModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxUseCase;
import com.moe.myfamilybudget.application.usecase.tax.TaxYearlyModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage Fiscalité (R-80, même gabarit que {@code DefaultRetirementUseCase},
 * SILO-311).
 *
 * <p>La logique de composition de la réponse (anciennement {@code ImpotsServiceImpl#getImpots}) vit
 * ici, sans {@code ResponseEntity} ni DTO : elle lit ses fragments via les ports des silos
 * ({@link RetirementSettingsReader}, {@link TaxSettingsReader}, {@link TaxReader}, {@link BudgetReader},
 * {@link PatrimoineReader}, {@link RetirementReader}, {@link BankReader}, {@link TresorerieSettingsReader},
 * {@link SimulationSettingsReader}, {@link EconomicAssumptionsReader}), construit l'entrée du moteur par
 * {@link TaxInputFactory} (après résolution de la période par {@link TaxSimulationPeriodResolver}),
 * appelle {@link TaxCalculationService} puis traduit les types des silos vers les modèles de
 * {@code application-api}. L'écriture (R-42) passe par {@link TaxCommandService}.
 *
 * <p>R-52 : le bloc {@code settings} de la réponse (contrat des façades composites, voir
 * 12-settings.md) est composé directement depuis les cinq ports propriétaires
 * ({@link SettingsComposer}), sans passer par {@code transition.port.SettingsReader}.
 */
@Service
public class DefaultTaxUseCase implements TaxUseCase {

    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final TaxCalculationService taxCalculationService;
    private final TaxCommandService taxCommandService;
    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final TaxReader taxReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final BankReader bankReader;

    public DefaultTaxUseCase(
            RetirementInputFactory retirementInputFactory,
            RetirementCalculationService retirementCalculationService,
            TaxCalculationService taxCalculationService,
            TaxCommandService taxCommandService,
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            TaxReader taxReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            BankReader bankReader) {
        this.retirementInputFactory = retirementInputFactory;
        this.retirementCalculationService = retirementCalculationService;
        this.taxCalculationService = taxCalculationService;
        this.taxCommandService = taxCommandService;
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
        this.taxReader = taxReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.bankReader = bankReader;
    }

    @Override
    public TaxResultModel getImpots() {
        RetirementSettingsModel retirementSettings = retirementSettingsReader.getRetirementSettings();
        TaxSettingsModel taxSettings = taxSettingsReader.getTaxSettings();
        var tresorerieSettings = tresorerieSettingsReader.getTresorerieSettings();
        var simulationSettings = simulationSettingsReader.getSimulationSettings();
        var economicAssumptions = economicAssumptionsReader.getEconomicAssumptions();
        RetirementModel retirementModel = retirementReader.getRetirement();
        List<IncomeModel> incomes = budgetReader.getIncomes();
        List<com.moe.myfamilybudget.domain.tax.model.TaxChildModel> children = taxReader.getTaxChildren();
        List<com.moe.myfamilybudget.domain.tax.model.TaxBracketModel> brackets =
                TaxBracketDefaults.orDefault(taxReader.getTaxBrackets());
        List<com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel> rateOverrides =
                taxReader.getTaxRateOverrides();
        List<com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel> actualOverrides =
                taxReader.getTaxActualOverrides();
        BankImportModel bankImport = bankReader.getBankImport();

        TaxSimulationPeriod period = TaxSimulationPeriodResolver.resolve(new TaxSimulationPeriodResolver.Sources(
                retirementSettings,
                simulationSettings,
                tresorerieSettings.pivotDate(),
                incomes,
                budgetReader.getCharges(),
                patrimoineReader.getPlacements(),
                budgetReader.getOneoffExpenses(),
                PatrimoineTransferConverter.toBudget(patrimoineReader.getTransfers()),
                bankImport));
        RetirementProjection retirement = retirementCalculationService.compute(retirementInputFactory.create(
                retirementSettings, retirementModel, incomes, children.size()));
        TaxCalculationInput input = TaxInputFactory.from(new TaxInputFactory.Sources(
                retirementSettings,
                taxSettings,
                economicAssumptions.inflationRate(),
                incomes,
                budgetReader.getVariableIncomes(),
                budgetReader.getVariableOverrides(),
                children,
                brackets,
                rateOverrides,
                actualOverrides), period, retirement);
        List<com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel> taxYearly =
                taxCalculationService.computeTaxYearly(input);
        List<com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel> taxPreview =
                taxCalculationService.buildTaxPreview(taxYearly, LocalDate.now().getYear());

        SettingsView settings = SettingsComposer.compose(
                retirementSettings, taxSettings, tresorerieSettings, simulationSettings, economicAssumptions);

        return new TaxResultModel(
                toChildren(children),
                toBrackets(brackets),
                toRateOverrides(rateOverrides),
                toActualOverrides(actualOverrides),
                toSettingsView(settings),
                toYearly(taxPreview),
                retirementModel != null ? retirementModel.pass2026() : null,
                retirementModel != null ? retirementModel.passGrowthRate() : null);
    }

    @Override
    public void saveTaxConfig(TaxConfigSaveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("La commande de sauvegarde de la fiscalité est obligatoire");
        }
        taxCommandService.updateTaxConfig(
                toDomainChildren(command.taxChildren()),
                toDomainBrackets(command.taxBrackets()),
                toDomainRateOverrides(command.taxRateOverrides()),
                toDomainActualOverrides(command.taxActualOverrides()));
    }

    @Override
    public void resetDefaultTaxBrackets() {
        taxCommandService.resetDefaultTaxBrackets();
    }

    private static List<TaxChildModel> toChildren(List<com.moe.myfamilybudget.domain.tax.model.TaxChildModel> children) {
        if (children == null) {
            return null;
        }
        List<TaxChildModel> result = new ArrayList<>(children.size());
        for (com.moe.myfamilybudget.domain.tax.model.TaxChildModel c : children) {
            result.add(c == null ? null : new TaxChildModel(c.id(), c.name(), c.birthYear()));
        }
        return result;
    }

    private static List<TaxBracketModel> toBrackets(List<com.moe.myfamilybudget.domain.tax.model.TaxBracketModel> brackets) {
        if (brackets == null) {
            return null;
        }
        List<TaxBracketModel> result = new ArrayList<>(brackets.size());
        for (com.moe.myfamilybudget.domain.tax.model.TaxBracketModel b : brackets) {
            result.add(b == null ? null : new TaxBracketModel(b.id(), b.upTo(), b.rate()));
        }
        return result;
    }

    private static List<TaxRateOverrideModel> toRateOverrides(
            List<com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel> rateOverrides) {
        if (rateOverrides == null) {
            return null;
        }
        List<TaxRateOverrideModel> result = new ArrayList<>(rateOverrides.size());
        for (com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel r : rateOverrides) {
            result.add(r == null ? null : new TaxRateOverrideModel(r.year(), r.rate()));
        }
        return result;
    }

    private static List<TaxActualOverrideModel> toActualOverrides(
            List<com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel> actualOverrides) {
        if (actualOverrides == null) {
            return null;
        }
        List<TaxActualOverrideModel> result = new ArrayList<>(actualOverrides.size());
        for (com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel a : actualOverrides) {
            result.add(a == null ? null : new TaxActualOverrideModel(a.year(), a.amount()));
        }
        return result;
    }

    private static List<TaxYearlyModel> toYearly(List<com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel> yearly) {
        if (yearly == null) {
            return null;
        }
        List<TaxYearlyModel> result = new ArrayList<>(yearly.size());
        for (com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel t : yearly) {
            result.add(t == null ? null : new TaxYearlyModel(
                    t.year(), t.parts(), t.taxableIncome(), t.taxForecast(), t.taxActual(), t.ratePAS(), t.withheld()));
        }
        return result;
    }

    private static TaxSettingsViewModel toSettingsView(SettingsView settings) {
        if (settings == null) {
            return null;
        }
        return new TaxSettingsViewModel(
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
                settings.cashAlertThreshold());
    }

    private static List<com.moe.myfamilybudget.domain.tax.model.TaxChildModel> toDomainChildren(List<TaxChildModel> children) {
        if (children == null) {
            return null;
        }
        List<com.moe.myfamilybudget.domain.tax.model.TaxChildModel> result = new ArrayList<>(children.size());
        for (TaxChildModel c : children) {
            result.add(c == null ? null : new com.moe.myfamilybudget.domain.tax.model.TaxChildModel(
                    c.id(), c.name(), c.birthYear()));
        }
        return result;
    }

    private static List<com.moe.myfamilybudget.domain.tax.model.TaxBracketModel> toDomainBrackets(List<TaxBracketModel> brackets) {
        if (brackets == null) {
            return null;
        }
        List<com.moe.myfamilybudget.domain.tax.model.TaxBracketModel> result = new ArrayList<>(brackets.size());
        for (TaxBracketModel b : brackets) {
            result.add(b == null ? null : new com.moe.myfamilybudget.domain.tax.model.TaxBracketModel(
                    b.id(), b.upTo(), b.rate()));
        }
        return result;
    }

    private static List<com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel> toDomainRateOverrides(
            List<TaxRateOverrideModel> rateOverrides) {
        if (rateOverrides == null) {
            return null;
        }
        List<com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel> result = new ArrayList<>(rateOverrides.size());
        for (TaxRateOverrideModel r : rateOverrides) {
            result.add(r == null ? null : new com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel(
                    r.year(), r.rate()));
        }
        return result;
    }

    private static List<com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel> toDomainActualOverrides(
            List<TaxActualOverrideModel> actualOverrides) {
        if (actualOverrides == null) {
            return null;
        }
        List<com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel> result = new ArrayList<>(actualOverrides.size());
        for (TaxActualOverrideModel a : actualOverrides) {
            result.add(a == null ? null : new com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel(
                    a.year(), a.amount()));
        }
        return result;
    }
}
