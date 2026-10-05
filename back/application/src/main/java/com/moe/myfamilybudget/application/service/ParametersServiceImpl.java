package com.moe.myfamilybudget.application.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.ParametresApi;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.application.model.SettingsCalculator;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.application.model.SettingsResultModel;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;

/**
 * RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. {@code getSettings()} lit via {@link SettingsReader},
 * {@link PatrimoineReader} (catégories d'actifs) et {@link BankReader} ; {@code saveSettings()}
 * ne lit rien et délègue déjà entièrement aux services de commande par domaine.
 *
 * <p>SET-020 : le routage des champs vers leur owner (Retraite, Fiscalité, Trésorerie, Objectifs, Simulation,
 * Hypothèses économiques) est délégué à {@link SettingsCommandRouter} ; l'atomicité reste portée ici.
 *
 * <p>DB-061 / SILO-206 : le verrou de mutation (VT-350b), pris en premier par {@code saveSettings}, passe par
 * le port {@link SiloMutationLock} et ne porte plus que sur les silos que la requête écrit (owners des champs,
 * Patrimoine pour les catégories d'actifs) ; une requête sans écriture ne verrouille rien.
 *
 * <p>VT-340 : {@code saveSettings} s'exécute dans une transaction — une mise à jour touchant plusieurs
 * propriétaires (Objectifs, Fiscalité/Paramètres) est appliquée en entier ou pas du tout. SILO-205 : la
 * transaction est ouverte via le port {@link TransactionRunner} (plus d'{@code @Transactional}).
 */
@Service
@RestController
public class ParametersServiceImpl implements ParametresApi {

    private final SettingsReader settingsReader;
    private final PatrimoineReader patrimoineReader;
    private final BankReader bankReader;
    private final RetirementReader retirementReader;
    private final SettingsMapper settingsMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final SiloMutationLock siloMutationLock;
    private final SettingsCommandRouter settingsCommandRouter;
    private final TransactionRunner transactionRunner;

    public ParametersServiceImpl(
            SettingsReader settingsReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader,
            RetirementReader retirementReader,
            SettingsMapper settingsMapper,
            ObjectifsSettingsService objectifsSettingsService,
            PatrimoineCommandService patrimoineCommandService,
            SiloMutationLock siloMutationLock,
            SettingsCommandRouter settingsCommandRouter,
            TransactionRunner transactionRunner) {
        this.settingsReader = settingsReader;
        this.patrimoineReader = patrimoineReader;
        this.bankReader = bankReader;
        this.retirementReader = retirementReader;
        this.settingsMapper = settingsMapper;
        this.objectifsSettingsService = objectifsSettingsService;
        this.patrimoineCommandService = patrimoineCommandService;
        this.siloMutationLock = siloMutationLock;
        this.settingsCommandRouter = settingsCommandRouter;
        this.transactionRunner = transactionRunner;
    }

    @Override
    public ResponseEntity<Object> getSettings() {
        SettingsModel settings = settingsReader.getSettings();
        List<AssetCategoryModel> categories = patrimoineReader.getAssetCategories();

        SettingsResultModel result = SettingsCalculator.computeSettingsResult(
                settings, categories, bankReader.getBankImport()
        );

        Map<String, Object> response = settingsMapper.toResponseMap(
                result, objectifsSettingsService.current(), retirementReader.getRetirement());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveSettings(Object body) {
        return transactionRunner.inTransaction(() -> applySettings(body));
    }

    private ResponseEntity<Void> applySettings(Object body) {
        Set<MutationSilo> silos = silosTouchedBy(body);
        if (!silos.isEmpty()) {
            siloMutationLock.lockForCurrentTransaction(silos);
        }
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;

            String action = typedMap.get("action") != null ? String.valueOf(typedMap.get("action")) : null;

            if ("updateAssetCategory".equals(action) || (typedMap.containsKey("assetCategoryId") && typedMap.containsKey("field"))) {
                String id = typedMap.get("id") != null ? String.valueOf(typedMap.get("id")) : String.valueOf(typedMap.get("assetCategoryId"));
                String field = String.valueOf(typedMap.get("field"));
                Object value = typedMap.get("value");
                AssetCategoryField.find(field)
                        .ifPresent(f -> patrimoineCommandService.updateAssetCategory(id, f, value));
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
     * Silos écrits par {@code body} : reflète exactement les branches de {@link #applySettings}, pour que le
     * verrou soit pris avant toute écriture et sur les seuls silos concernés (SILO-206).
     */
    private static Set<MutationSilo> silosTouchedBy(Object body) {
        Set<MutationSilo> silos = EnumSet.noneOf(MutationSilo.class);
        if (!(body instanceof Map<?, ?> map)) {
            return silos;
        }
        String action = map.get("action") != null ? String.valueOf(map.get("action")) : null;
        if ("updateAssetCategory".equals(action)
                || (map.containsKey("assetCategoryId") && map.containsKey("field"))
                || "addAssetCategory".equals(action)
                || "removeAssetCategory".equals(action)) {
            silos.add(MutationSilo.WEALTH);
        } else if (map.containsKey("field") && map.get("field") != null) {
            addSiloOf(silos, String.valueOf(map.get("field")));
        } else if (map.containsKey("settings") && map.get("settings") instanceof Map<?, ?> settings) {
            for (Object key : settings.keySet()) {
                addSiloOf(silos, String.valueOf(key));
            }
        }
        return silos;
    }

    private static void addSiloOf(Set<MutationSilo> silos, String field) {
        SettingsCommandRouter.ownerOf(field).ifPresent(owner -> silos.add(owner.silo()));
    }

    /**
     * Façade unique de {@code PATCH /settings} (voir doc/architecture/12-settings.md) : chaque
     * champ est routé par propriété vers son owner (SET-020), via la table de routage partagée
     * {@link SettingsCommandRouter}.
     */
    private void updateSetting(String field, Object value) {
        settingsCommandRouter.updateSetting(field, value);
    }
}
