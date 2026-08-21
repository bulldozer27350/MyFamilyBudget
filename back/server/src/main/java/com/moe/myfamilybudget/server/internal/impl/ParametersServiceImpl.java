package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.ParametresApi;
import com.moe.myfamilybudget.server.internal.mapper.SettingsMapper;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.SettingsCalculator;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.SettingsResultModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@Service
@RestController
public class ParametersServiceImpl implements ParametresApi {

    private final PersistenceManager persistenceManager;
    private final SettingsMapper settingsMapper;

    public ParametersServiceImpl(PersistenceManager persistenceManager, SettingsMapper settingsMapper) {
        this.persistenceManager = persistenceManager;
        this.settingsMapper = settingsMapper;
    }

    @Override
    public ResponseEntity<Object> getSettings() {
        BudgetDataModel data = persistenceManager.getBudgetData();
        SettingsModel settings = data.getEffectiveSettings();
        List<AssetCategoryModel> categories = data.getEffectiveAssetCategories();

        SettingsResultModel result = SettingsCalculator.computeSettingsResult(
                settings, categories, data.bankImport()
        );

        Map<String, Object> response = settingsMapper.toResponseMap(result);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveSettings(Object body) {
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;

            String action = typedMap.get("action") != null ? String.valueOf(typedMap.get("action")) : null;

            if ("updateAssetCategory".equals(action) || (typedMap.containsKey("assetCategoryId") && typedMap.containsKey("field"))) {
                String id = typedMap.get("id") != null ? String.valueOf(typedMap.get("id")) : String.valueOf(typedMap.get("assetCategoryId"));
                String field = String.valueOf(typedMap.get("field"));
                Object value = typedMap.get("value");
                persistenceManager.updateAssetCategory(id, field, value);
            } else if ("addAssetCategory".equals(action)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rowMap = (Map<String, Object>) typedMap.get("row");
                AssetCategoryModel category = settingsMapper.toAssetCategoryModel(rowMap);
                persistenceManager.addAssetCategory(category);
            } else if ("removeAssetCategory".equals(action)) {
                String id = String.valueOf(typedMap.get("id"));
                persistenceManager.removeAssetCategory(id);
            } else if (typedMap.containsKey("field") && typedMap.get("field") != null) {
                String field = String.valueOf(typedMap.get("field"));
                Object value = typedMap.get("value");
                persistenceManager.updateTaxSettings(field, value);
            } else if (typedMap.containsKey("settings") && typedMap.get("settings") instanceof Map<?, ?> sMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedSMap = (Map<String, Object>) sMap;
                for (Map.Entry<String, Object> entry : typedSMap.entrySet()) {
                    persistenceManager.updateTaxSettings(entry.getKey(), entry.getValue());
                }
            }
        }
        return ResponseEntity.ok().build();
    }
}
