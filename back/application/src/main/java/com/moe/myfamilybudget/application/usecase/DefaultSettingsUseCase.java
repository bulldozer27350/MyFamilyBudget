package com.moe.myfamilybudget.application.usecase;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.model.SettingsCalculator;
import com.moe.myfamilybudget.application.model.SettingsResultModel;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.settings.SettingsComposer;
import com.moe.myfamilybudget.application.usecase.settings.SettingsUseCase;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.wealth.port.AssetCategoryField;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Implémentation du cas d'usage « Paramètres » (R-88, SILO-319).
 *
 * <p>Orchestre la lecture de la vue composite des paramètres ({@link SettingsComposer}), le calcul
 * des métadonnées ({@link SettingsCalculator}), l'assemblage de la réponse ({@link SettingsMapper}),
 * et l'application atomique des mutations sous transaction ({@link TransactionRunner}) et verrou
 * ({@link SiloMutationLock}).
 *
 * <p>Le contrôleur {@code ParametersServiceImpl} (ou le module {@code web}) ne fait que convertir vers/depuis
 * {@code ResponseEntity}.
 */
@Service
public class DefaultSettingsUseCase implements SettingsUseCase {

    private final RetirementSettingsReader retirementSettingsReader;
    private final TaxSettingsReader taxSettingsReader;
    private final TresorerieSettingsReader tresorerieSettingsReader;
    private final SimulationSettingsReader simulationSettingsReader;
    private final EconomicAssumptionsReader economicAssumptionsReader;
    private final PatrimoineReader patrimoineReader;
    private final BankReader bankReader;
    private final RetirementReader retirementReader;
    private final SettingsMapper settingsMapper;
    private final ObjectifsSettingsService objectifsSettingsService;
    private final PatrimoineCommandService patrimoineCommandService;
    private final SiloMutationLock siloMutationLock;
    private final SettingsCommandRouter settingsCommandRouter;
    private final TransactionRunner transactionRunner;

    public DefaultSettingsUseCase(
            RetirementSettingsReader retirementSettingsReader,
            TaxSettingsReader taxSettingsReader,
            TresorerieSettingsReader tresorerieSettingsReader,
            SimulationSettingsReader simulationSettingsReader,
            EconomicAssumptionsReader economicAssumptionsReader,
            PatrimoineReader patrimoineReader,
            BankReader bankReader,
            RetirementReader retirementReader,
            SettingsMapper settingsMapper,
            ObjectifsSettingsService objectifsSettingsService,
            PatrimoineCommandService patrimoineCommandService,
            SiloMutationLock siloMutationLock,
            SettingsCommandRouter settingsCommandRouter,
            TransactionRunner transactionRunner) {
        this.retirementSettingsReader = retirementSettingsReader;
        this.taxSettingsReader = taxSettingsReader;
        this.tresorerieSettingsReader = tresorerieSettingsReader;
        this.simulationSettingsReader = simulationSettingsReader;
        this.economicAssumptionsReader = economicAssumptionsReader;
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
    public Map<String, Object> getSettings() {
        var settings = SettingsComposer.compose(
                retirementSettingsReader.getRetirementSettings(), taxSettingsReader.getTaxSettings(),
                tresorerieSettingsReader.getTresorerieSettings(), simulationSettingsReader.getSimulationSettings(),
                economicAssumptionsReader.getEconomicAssumptions());
        List<AssetCategoryModel> categories = patrimoineReader.getAssetCategories();

        SettingsResultModel result = SettingsCalculator.computeSettingsResult(
                settings, categories, bankReader.getBankImport());

        return settingsMapper.toResponseMap(
                result, objectifsSettingsService.current(), retirementReader.getRetirement());
    }

    @Override
    public void saveSettings(Object body) {
        transactionRunner.inTransaction(() -> {
            applySettings(body);
            return null;
        });
    }

    private void applySettings(Object body) {
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

    private void updateSetting(String field, Object value) {
        settingsCommandRouter.updateSetting(field, value);
    }
}
