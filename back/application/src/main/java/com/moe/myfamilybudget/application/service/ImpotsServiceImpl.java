package com.moe.myfamilybudget.application.service;

import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.api.controller.ImpotsApi;
import com.moe.myfamilybudget.domain.retirement.calculation.RetirementCalculationService;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculationInput;
import com.moe.myfamilybudget.domain.tax.calculation.TaxSimulationPeriod;
import com.moe.myfamilybudget.application.factory.RetirementInputFactory;
import com.moe.myfamilybudget.application.factory.TaxInputFactory;
import com.moe.myfamilybudget.application.factory.TaxSimulationPeriodResolver;
import com.moe.myfamilybudget.application.mapper.TaxMapper;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementProjection;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.calculation.TaxCalculator;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxYearlyModel;
import com.moe.myfamilybudget.application.model.TaxResultModel;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.command.TaxCommandService;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service/Contrôleur implémentant l'API OpenAPI ImpotsApi.
 * Orchestre les échanges entre la couche REST DTO et le domaine interne.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Les lectures passent par les ports de domaine ({@link
 * SettingsReader}, {@link TaxReader}, {@link BudgetReader}, {@link PatrimoineReader},
 * {@link RetirementReader}, {@link BankReader}) ; le {@link BudgetDataModel} attendu par
 * {@link TaxSimulationPeriodResolver}, {@link RetirementInputFactory} et {@link TaxInputFactory}
 * est recomposé localement à partir de ces ports.
 */
@RestController
public class ImpotsServiceImpl implements ImpotsApi {

    private final TaxMapper taxMapper;
    private final RetirementInputFactory retirementInputFactory;
    private final RetirementCalculationService retirementCalculationService;
    private final TaxCommandService taxCommandService;
    private final SettingsCommandRouter settingsCommandRouter;
    private final SettingsReader settingsReader;
    private final TaxReader taxReader;
    private final BudgetReader budgetReader;
    private final PatrimoineReader patrimoineReader;
    private final RetirementReader retirementReader;
    private final BankReader bankReader;

    public ImpotsServiceImpl(
            TaxMapper taxMapper,
            RetirementInputFactory retirementInputFactory,
            RetirementCalculationService retirementCalculationService,
            TaxCommandService taxCommandService,
            SettingsCommandRouter settingsCommandRouter,
            SettingsReader settingsReader,
            TaxReader taxReader,
            BudgetReader budgetReader,
            PatrimoineReader patrimoineReader,
            RetirementReader retirementReader,
            BankReader bankReader) {
        this.taxMapper = taxMapper;
        this.retirementInputFactory = retirementInputFactory;
        this.retirementCalculationService = retirementCalculationService;
        this.taxCommandService = taxCommandService;
        this.settingsCommandRouter = settingsCommandRouter;
        this.settingsReader = settingsReader;
        this.taxReader = taxReader;
        this.budgetReader = budgetReader;
        this.patrimoineReader = patrimoineReader;
        this.retirementReader = retirementReader;
        this.bankReader = bankReader;
    }

    private BudgetDataModel composeBudgetData() {
        return new BudgetDataModel(
                settingsReader.getSettings(),
                budgetReader.getIncomes(),
                budgetReader.getCharges(),
                patrimoineReader.getPlacements(),
                patrimoineReader.getRealEstate(),
                retirementReader.getRetirement(),
                taxReader.getTaxChildren(),
                taxReader.getTaxBrackets(),
                taxReader.getTaxRateOverrides(),
                taxReader.getTaxActualOverrides(),
                budgetReader.getOneoffExpenses(),
                patrimoineReader.getTransfers(),
                budgetReader.getVariableIncomes(),
                budgetReader.getVariableOverrides(),
                bankReader.getBankImport(),
                null,
                null,
                null);
    }

    @Override
    public ResponseEntity<Object> getImpots() {
        BudgetDataModel data = composeBudgetData();
        TaxSimulationPeriod period = TaxSimulationPeriodResolver.resolve(data);
        RetirementProjection retirement = retirementCalculationService.compute(retirementInputFactory.create(
                new RetirementSettingsModel(data.getEffectiveSettings().birthYear(), data.getEffectiveSettings().retireAge()),
                data.retirement(), data.getEffectiveIncomes(), data.getEffectiveTaxChildren().size()));
        TaxCalculationInput input = TaxInputFactory.from(data, period, retirement);
        List<TaxYearlyModel> taxYearly = TaxCalculator.computeTaxYearly(input);
        List<TaxYearlyModel> taxPreview = TaxCalculator.buildTaxPreview(
                taxYearly, java.time.LocalDate.now().getYear());
        TaxResultModel resultModel = new TaxResultModel(
                data.getEffectiveTaxChildren(),
                data.getEffectiveTaxBrackets(),
                data.getEffectiveTaxRateOverrides(),
                data.getEffectiveTaxActualOverrides(),
                data.getEffectiveSettings(),
                taxPreview);
        Map<String, Object> response = taxMapper.toResponseMap(resultModel, data.retirement());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveImpotsConfig(Object body) {
        if (body instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;

            if (map.containsKey("action")) {
                String action = String.valueOf(map.get("action"));
                if ("resetDefaultTaxBrackets".equalsIgnoreCase(action)) {
                    taxCommandService.resetDefaultTaxBrackets();
                    return ResponseEntity.ok().build();
                } else if ("updateSettings".equalsIgnoreCase(action) || map.containsKey("field")) {
                    String field = String.valueOf(map.get("field"));
                    Object value = map.get("value");
                    settingsCommandRouter.updateSetting(field, value);
                    return ResponseEntity.ok().build();
                }
            }

            List<TaxChildModel> children = null;
            if (map.get("taxChildren") instanceof List<?> rawList) {
                children = rawList.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) m;
                            return taxMapper.toTaxChildModel(itemMap);
                        })
                        .toList();
            }

            List<TaxBracketModel> brackets = null;
            if (map.get("taxBrackets") instanceof List<?> rawList) {
                brackets = rawList.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) m;
                            return taxMapper.toTaxBracketModel(itemMap);
                        })
                        .toList();
            }

            List<TaxRateOverrideModel> rateOverrides = null;
            if (map.get("taxRateOverrides") instanceof List<?> rawList) {
                rateOverrides = rawList.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) m;
                            return taxMapper.toTaxRateOverrideModel(itemMap);
                        })
                        .toList();
            }

            List<TaxActualOverrideModel> actualOverrides = null;
            if (map.get("taxActualOverrides") instanceof List<?> rawList) {
                actualOverrides = rawList.stream()
                        .filter(Map.class::isInstance)
                        .map(m -> {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) m;
                            return taxMapper.toTaxActualOverrideModel(itemMap);
                        })
                        .toList();
            }

            taxCommandService.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
        }

        return ResponseEntity.ok().build();
    }
}
