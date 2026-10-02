package com.moe.myfamilybudget.server.internal.mapper;

import com.moe.myfamilybudget.server.internal.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.SettingsResultModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SettingsMapper {

    public Map<String, Object> toResponseMap(SettingsResultModel model) {
        return toResponseMap(model, ObjectifsParameters.defaults());
    }

    /**
     * Variante sans retraite : les clés {@code pass2026} / {@code passGrowthRate} sont alors {@code null}.
     * Les services d'API utilisent {@link #toResponseMap(SettingsResultModel, ObjectifsParameters, RetirementModel)}.
     */
    public Map<String, Object> toResponseMap(SettingsResultModel model, ObjectifsParameters objectifs) {
        return toResponseMap(model, objectifs, null);
    }

    /**
     * Les seuils des objectifs ne font plus partie de {@code SettingsModel} (RF-700) : ils sont
     * fournis à part par le domaine Objectifs et réinjectés ici pour conserver le contrat
     * {@code GET /settings} inchangé. Il en va de même (SET-040) pour {@code pass2026} et
     * {@code passGrowthRate}, relus depuis la retraite, seule propriétaire de ces valeurs.
     */
    public Map<String, Object> toResponseMap(SettingsResultModel model, ObjectifsParameters objectifs,
                                             RetirementModel retirement) {
        ObjectifsParameters goals = objectifs != null ? objectifs : ObjectifsParameters.defaults();
        if (model == null) {
            return new HashMap<>();
        }

        Map<String, Object> response = new HashMap<>();

        if (model.settings() != null) {
            Map<String, Object> sMap = new HashMap<>();
            SettingsModel s = model.settings();
            sMap.put("birthYear", s.birthYear());
            sMap.put("retireAge", s.retireAge());
            sMap.put("simulateUntilAge", s.simulateUntilAge());
            sMap.put("inflationRate", s.inflationRate());
            sMap.put("pivotDate", s.pivotDate());
            sMap.put("pivotMode", s.pivotMode());
            sMap.put("startBalance", s.startBalance());
            sMap.put("childExitAge", s.childExitAge());
            sMap.put("taxAbattement", s.taxAbattement());
            sMap.put("pass2026", retirement != null ? retirement.pass2026() : null);
            sMap.put("passGrowthRate", retirement != null ? retirement.passGrowthRate() : null);
            sMap.put("sweepEnabled", s.sweepEnabled());
            sMap.put("cashCeiling", s.cashCeiling());
            sMap.put("cashFloor", s.cashFloor());
            sMap.put("cashAlertThreshold", s.cashAlertThreshold());
            sMap.put("goalSecureHorizonMonths", goals.secureHorizonMonths());
            sMap.put("goalLiquidHorizonMonths", goals.liquidHorizonMonths());
            response.put("settings", sMap);
        } else {
            response.put("settings", new HashMap<>());
        }

        if (model.assetCategories() != null) {
            List<Map<String, Object>> categoriesList = model.assetCategories().stream()
                    .map(this::toAssetCategoryMap)
                    .collect(Collectors.toList());
            response.put("assetCategories", categoriesList);
        } else {
            response.put("assetCategories", List.of());
        }

        response.put("retireYear", model.retireYear());
        response.put("years", model.years() != null ? model.years() : List.of());
        response.put("bankImport", model.bankImport() != null ? model.bankImport() : new HashMap<>());

        return response;
    }

    public Map<String, Object> toAssetCategoryMap(AssetCategoryModel category) {
        if (category == null) return new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("id", category.id());
        map.put("icon", category.icon());
        map.put("name", category.name());
        map.put("bucket", category.bucket());
        map.put("color", category.color());
        return map;
    }

    public AssetCategoryModel toAssetCategoryModel(Map<String, Object> map) {
        if (map == null) return new AssetCategoryModel(null, "📁", "", "cash", null);
        String id = map.get("id") != null ? String.valueOf(map.get("id")) : null;
        String icon = map.get("icon") != null ? String.valueOf(map.get("icon")) : "📁";
        String name = map.get("name") != null ? String.valueOf(map.get("name")) : "";
        String bucket = map.get("bucket") != null ? String.valueOf(map.get("bucket")) : "cash";
        String color = map.get("color") != null ? String.valueOf(map.get("color")) : null;
        return new AssetCategoryModel(id, icon, name, bucket, color);
    }
}
