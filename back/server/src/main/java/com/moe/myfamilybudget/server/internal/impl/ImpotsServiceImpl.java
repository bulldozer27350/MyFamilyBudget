package com.moe.myfamilybudget.server.internal.impl;

import com.moe.myfamilybudget.api.controller.ImpotsApi;
import com.moe.myfamilybudget.server.internal.mapper.TaxMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxCalculator;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxResultModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Service/Contrôleur implémentant l'API OpenAPI ImpotsApi.
 * Orchestre les échanges entre la couche REST DTO et le domaine interne.
 */
@RestController
public class ImpotsServiceImpl implements ImpotsApi {

    private final PersistenceManager persistenceManager;
    private final TaxMapper taxMapper;

    public ImpotsServiceImpl(PersistenceManager persistenceManager, TaxMapper taxMapper) {
        this.persistenceManager = persistenceManager;
        this.taxMapper = taxMapper;
    }

    @Override
    public ResponseEntity<Object> getImpots() {
        BudgetDataModel data = persistenceManager.getBudgetData();
        TaxResultModel resultModel = TaxCalculator.computeTaxResult(data);
        Map<String, Object> response = taxMapper.toResponseMap(resultModel);
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
                    persistenceManager.resetDefaultTaxBrackets();
                    return ResponseEntity.ok().build();
                } else if ("updateSettings".equalsIgnoreCase(action) || map.containsKey("field")) {
                    String field = String.valueOf(map.get("field"));
                    Object value = map.get("value");
                    persistenceManager.updateTaxSettings(field, value);
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

            persistenceManager.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
        }

        return ResponseEntity.ok().build();
    }
}
