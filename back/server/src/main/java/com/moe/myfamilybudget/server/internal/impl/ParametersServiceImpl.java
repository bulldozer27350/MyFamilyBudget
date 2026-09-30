package com.moe.myfamilybudget.server.internal.impl;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.ParametresApi;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.command.PatrimoineCommandService;
import com.moe.myfamilybudget.server.internal.command.TaxCommandService;
import com.moe.myfamilybudget.server.internal.mapper.SettingsMapper;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.SettingsCalculator;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.SettingsResultModel;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. {@code getSettings()} lit via {@link SettingsReader},
 * {@link PatrimoineReader} (catégories d'actifs) et {@link BankReader} ; {@code saveSettings()}
 * ne lit rien et délègue déjà entièrement aux services de commande par domaine.
 *
 * <p>VT-340 : {@code saveSettings} est {@code @Transactional} — une mise à jour touchant plusieurs
 * propriétaires (Objectifs, Fiscalité/Paramètres) est appliquée en entier ou pas du tout.
 */
@Service
@RestController
public class ParametersServiceImpl implements ParametresApi {

    private final SettingsReader settingsReader;
    private final PatrimoineReader patrimoineReader;
    private final BankReader bankReader;
    private final SettingsMapper settingsMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final TaxCommandService taxCommandService;

    public ParametersServiceImpl(
            SettingsReader settingsReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader,
            SettingsMapper settingsMapper,
            ObjectifsSettingsService objectifsSettingsService,
            PatrimoineCommandService patrimoineCommandService,
            TaxCommandService taxCommandService) {
        this.settingsReader = settingsReader;
        this.patrimoineReader = patrimoineReader;
        this.bankReader = bankReader;
        this.settingsMapper = settingsMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.patrimoineCommandService = patrimoineCommandService;
        this.taxCommandService = taxCommandService;
    }

    @Override
    public ResponseEntity<Object> getSettings() {
        SettingsModel settings = settingsReader.getSettings();
        List<AssetCategoryModel> categories = patrimoineReader.getAssetCategories();

        SettingsResultModel result = SettingsCalculator.computeSettingsResult(
                settings, categories, bankReader.getBankImport()
        );

        Map<String, Object> response = settingsMapper.toResponseMap(result, objectifsSettingsService.current());
        return ResponseEntity.ok(response);
    }

    @Override
    @Transactional
    public ResponseEntity<Void> saveSettings(Object body) {
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;

            String action = typedMap.get("action") != null ? String.valueOf(typedMap.get("action")) : null;

            if ("updateAssetCategory".equals(action) || (typedMap.containsKey("assetCategoryId") && typedMap.containsKey("field"))) {
                String id = typedMap.get("id") != null ? String.valueOf(typedMap.get("id")) : String.valueOf(typedMap.get("assetCategoryId"));
                String field = String.valueOf(typedMap.get("field"));
                Object value = typedMap.get("value");
                patrimoineCommandService.updateAssetCategory(id, field, value);
            } else if ("addAssetCategory".equals(action)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rowMap = (Map<String, Object>) typedMap.get("row");
                AssetCategoryModel category = settingsMapper.toAssetCategoryModel(rowMap);
                patrimoineCommandService.addAssetCategory(category);
            } else if ("removeAssetCategory".equals(action)) {
                String id = String.valueOf(typedMap.get("id"));
                patrimoineCommandService.removeAssetCategory(id);
            } else if (typedMap.containsKey("field") && typedMap.get("field") != null) {
                String field = String.valueOf(typedMap.get("field"));
                Object value = typedMap.get("value");
                updateSetting(field, value);
            } else if (typedMap.containsKey("settings") && typedMap.get("settings") instanceof Map<?, ?> sMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedSMap = (Map<String, Object>) sMap;
                for (Map.Entry<String, Object> entry : typedSMap.entrySet()) {
                    updateSetting(entry.getKey(), entry.getValue());
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Façade unique de {@code PATCH /settings} (voir doc/architecture/12-settings.md) : chaque
     * champ est routé vers le domaine propriétaire.
     */
    private void updateSetting(String field, Object value) {
        if (ObjectifsSettingsService.owns(field)) {
            objectifsSettingsService.updateField(field, value);
        } else {
            taxCommandService.updateTaxSettings(field, value);
        }
    }
}
