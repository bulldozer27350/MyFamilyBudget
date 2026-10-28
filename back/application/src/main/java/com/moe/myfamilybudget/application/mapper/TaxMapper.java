package com.moe.myfamilybudget.application.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.application.usecase.tax.TaxActualOverrideModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxBracketModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxChildModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxConfigSaveCommand;
import com.moe.myfamilybudget.application.usecase.tax.TaxRateOverrideModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxResultModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxSettingsViewModel;
import com.moe.myfamilybudget.application.usecase.tax.TaxYearlyModel;

/**
 * Mapper assurant la conversion entre le modèle applicatif ({@code application-api}, package
 * {@code application.usecase.tax}) et la couche DTO / API Map.
 *
 * <p>R-80 : ce mapper ne connaît plus aucun type d'un silo (ni {@code domain.tax.model}, ni
 * {@code domain.retirement.model}) ; {@code pass2026}/{@code passGrowthRate} sont rappelés directement
 * depuis {@link TaxResultModel} (composés par le cas d'usage {@code TaxUseCase}).
 */
@Component
public class TaxMapper {

    private static final Logger LOG = LoggerFactory.getLogger(TaxMapper.class);

    /**
     * Convertit un TaxResultModel (modèle applicatif) en une Map d'objets sérialisables JSON pour l'API.
     */
    public Map<String, Object> toResponseMap(TaxResultModel model) {
        Map<String, Object> response = new HashMap<>();

        if (model == null) {
            return response;
        }

        // 1. Tax Children
        List<Map<String, Object>> childrenList = new ArrayList<>();
        if (model.taxChildren() != null) {
            for (TaxChildModel c : model.taxChildren()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", c.id());
                item.put("name", c.name());
                item.put("birthYear", c.birthYear());
                childrenList.add(item);
            }
        }
        response.put("taxChildren", childrenList);

        // 2. Tax Brackets
        List<Map<String, Object>> bracketsList = new ArrayList<>();
        if (model.taxBrackets() != null) {
            for (TaxBracketModel b : model.taxBrackets()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", b.id());
                item.put("upTo", b.upTo());
                item.put("rate", b.rate());
                bracketsList.add(item);
            }
        }
        response.put("taxBrackets", bracketsList);

        // 3. Tax Rate Overrides
        List<Map<String, Object>> rateOverridesList = new ArrayList<>();
        if (model.taxRateOverrides() != null) {
            for (TaxRateOverrideModel r : model.taxRateOverrides()) {
                Map<String, Object> item = new HashMap<>();
                item.put("year", r.year());
                item.put("rate", r.rate());
                rateOverridesList.add(item);
            }
        }
        response.put("taxRateOverrides", rateOverridesList);

        // 4. Tax Actual Overrides
        List<Map<String, Object>> actualOverridesList = new ArrayList<>();
        if (model.taxActualOverrides() != null) {
            for (TaxActualOverrideModel a : model.taxActualOverrides()) {
                Map<String, Object> item = new HashMap<>();
                item.put("year", a.year());
                item.put("amount", a.amount());
                actualOverridesList.add(item);
            }
        }
        response.put("taxActualOverrides", actualOverridesList);

        // 5. Settings
        Map<String, Object> settingsMap = new HashMap<>();
        if (model.settings() != null) {
            TaxSettingsViewModel s = model.settings();
            settingsMap.put("birthYear", s.birthYear());
            settingsMap.put("retireAge", s.retireAge());
            settingsMap.put("simulateUntilAge", s.simulateUntilAge());
            settingsMap.put("inflationRate", s.inflationRate());
            settingsMap.put("pivotDate", s.pivotDate());
            settingsMap.put("pivotMode", s.pivotMode());
            settingsMap.put("startBalance", s.startBalance());
            settingsMap.put("childExitAge", s.childExitAge());
            settingsMap.put("taxAbattement", s.taxAbattement());
            settingsMap.put("pass2026", model.pass2026());
            settingsMap.put("passGrowthRate", model.passGrowthRate());
            settingsMap.put("sweepEnabled", s.sweepEnabled());
            settingsMap.put("cashCeiling", s.cashCeiling());
            settingsMap.put("cashFloor", s.cashFloor());
            settingsMap.put("cashAlertThreshold", s.cashAlertThreshold());
        }
        response.put("settings", settingsMap);

        // 6. Tax Preview
        List<Map<String, Object>> previewList = new ArrayList<>();
        if (model.taxPreview() != null) {
            for (TaxYearlyModel t : model.taxPreview()) {
                Map<String, Object> item = new HashMap<>();
                item.put("year", t.year());
                item.put("parts", t.parts());
                item.put("taxableIncome", t.taxableIncome());
                item.put("taxForecast", t.taxForecast());
                item.put("taxActual", t.taxActual());
                item.put("ratePAS", t.ratePAS());
                item.put("withheld", t.withheld());
                previewList.add(item);
            }
        }
        response.put("taxPreview", previewList);

        return response;
    }

    /**
     * Construit la commande de sauvegarde à partir du corps JSON. Une liste absente du corps reste
     * {@code null} dans la commande (contrat historique : conserve la valeur déjà enregistrée).
     */
    public TaxConfigSaveCommand toSaveCommandFromMap(Map<String, Object> map) {
        List<TaxChildModel> children = null;
        if (map != null && map.get("taxChildren") instanceof List<?> rawList) {
            children = rawList.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) m;
                        return toTaxChildModel(itemMap);
                    })
                    .toList();
        }

        List<TaxBracketModel> brackets = null;
        if (map != null && map.get("taxBrackets") instanceof List<?> rawList) {
            brackets = rawList.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) m;
                        return toTaxBracketModel(itemMap);
                    })
                    .toList();
        }

        List<TaxRateOverrideModel> rateOverrides = null;
        if (map != null && map.get("taxRateOverrides") instanceof List<?> rawList) {
            rateOverrides = rawList.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) m;
                        return toTaxRateOverrideModel(itemMap);
                    })
                    .toList();
        }

        List<TaxActualOverrideModel> actualOverrides = null;
        if (map != null && map.get("taxActualOverrides") instanceof List<?> rawList) {
            actualOverrides = rawList.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) m;
                        return toTaxActualOverrideModel(itemMap);
                    })
                    .toList();
        }

        return new TaxConfigSaveCommand(children, brackets, rateOverrides, actualOverrides);
    }

    public TaxChildModel toTaxChildModel(Map<String, Object> map) {
        if (map == null) return null;
        String id = getString(map, "id", null);
        String name = getString(map, "name", "");
        Integer birthYear = getInteger(map, "birthYear", null);
        return new TaxChildModel(id, name, birthYear);
    }

    public TaxBracketModel toTaxBracketModel(Map<String, Object> map) {
        if (map == null) return null;
        String id = getString(map, "id", null);
        BigDecimal upTo = getBigDecimal(map, "upTo", null);
        BigDecimal rate = getBigDecimal(map, "rate", BigDecimal.ZERO);
        return new TaxBracketModel(id, upTo, rate);
    }

    public TaxRateOverrideModel toTaxRateOverrideModel(Map<String, Object> map) {
        if (map == null) return null;
        Integer year = getInteger(map, "year", null);
        BigDecimal rate = getBigDecimal(map, "rate", BigDecimal.ZERO);
        return new TaxRateOverrideModel(year, rate);
    }

    public TaxActualOverrideModel toTaxActualOverrideModel(Map<String, Object> map) {
        if (map == null) return null;
        Integer year = getInteger(map, "year", null);
        BigDecimal amount = getBigDecimal(map, "amount", BigDecimal.ZERO);
        return new TaxActualOverrideModel(year, amount);
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(val).trim().replace(",", ".");
            if (s.isEmpty()) return defaultValue;
            return new BigDecimal(s);
        } catch (Exception e) {
            LOG.warn("Champ décimal fiscal '{}' illisible, valeur par défaut '{}' utilisée : '{}'",
                    key, defaultValue, val, e);
            return defaultValue;
        }
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        Object val = map.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(val).trim();
            if (s.isEmpty()) return defaultValue;
            return Integer.parseInt(s);
        } catch (Exception e) {
            LOG.warn("Champ entier fiscal '{}' illisible, valeur par défaut '{}' utilisée : '{}'",
                    key, defaultValue, val, e);
            return defaultValue;
        }
    }
}
