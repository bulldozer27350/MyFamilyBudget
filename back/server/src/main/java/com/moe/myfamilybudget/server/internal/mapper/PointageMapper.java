package com.moe.myfamilybudget.server.internal.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PointageModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

/**
 * Mapper assurant la conversion entre les structures de transfert (Maps / DTOs)
 * et le Modèle Interne de Pointage.
 */
@Component
public class PointageMapper {

    /**
     * Convertit le Modèle Interne PointageModel en Map représentant la réponse JSON pour l'API.
     */
    public Map<String, Object> toPointageResponseMap(PointageModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();

        map.put("transactions", model.transactions() != null
                ? model.transactions().stream().map(this::toTransactionMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("categories", model.categories() != null
                ? model.categories().stream().map(this::toCategoryMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("matchings", model.matchings() != null
                ? model.matchings().stream().map(this::toMatchingMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("charges", model.charges() != null
                ? model.charges().stream().map(this::toChargeMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("incomes", model.incomes() != null
                ? model.incomes().stream().map(this::toIncomeMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("placements", model.placements() != null
                ? model.placements().stream().map(this::toPlacementMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("settings", model.settings() != null
                ? toSettingsMap(model.settings())
                : Collections.emptyMap());

        return map;
    }

    /**
     * Extrait et convertit le body de la requête HTTP en une liste de MatchingLinkModel.
     */
    @SuppressWarnings("unchecked")
    public List<BankImportModel.MatchingLinkModel> toMatchingLinks(Object body) {
        if (body == null) return Collections.emptyList();

        List<Object> rawList = null;
        if (body instanceof List<?> list) {
            rawList = (List<Object>) list;
        } else if (body instanceof Map<?, ?> map && map.containsKey("links")) {
            Object linksObj = map.get("links");
            if (linksObj instanceof List<?> list) {
                rawList = (List<Object>) list;
            }
        }

        if (rawList == null) return Collections.emptyList();

        List<BankImportModel.MatchingLinkModel> links = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> linkMap) {
                String budgetLineId = String.valueOf(linkMap.getOrDefault("budgetLineId", ""));
                List<String> txIds = new ArrayList<>();

                Object txIdsObj = linkMap.get("txIds");
                if (txIdsObj instanceof List<?> idsList) {
                    for (Object idObj : idsList) {
                        if (idObj != null) {
                            txIds.add(String.valueOf(idObj));
                        }
                    }
                }
                links.add(new BankImportModel.MatchingLinkModel(budgetLineId, txIds));
            }
        }
        return links;
    }

    // --- Conversions internes en Map ---

    private Map<String, Object> toTransactionMap(BankImportModel.BankTransactionModel t) {
        if (t == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", t.id());
        map.put("date", t.date());
        map.put("label", t.label());
        map.put("type", t.type());
        map.put("amount", t.amount());
        map.put("categoryId", t.categoryId());
        return map;
    }

    private Map<String, Object> toCategoryMap(BankImportModel.CategoryModel c) {
        if (c == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.id());
        map.put("label", c.label());
        map.put("kind", c.kind());
        map.put("compressible", c.compressible());
        return map;
    }

    private Map<String, Object> toMatchingMap(BankImportModel.MatchingModel m) {
        if (m == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("month", m.month());
        List<Map<String, Object>> linkMaps = m.links() != null
                ? m.links().stream().map(this::toMatchingLinkMap).collect(Collectors.toList())
                : Collections.emptyList();
        map.put("links", linkMaps);
        return map;
    }

    private Map<String, Object> toMatchingLinkMap(BankImportModel.MatchingLinkModel l) {
        if (l == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("budgetLineId", l.budgetLineId());
        map.put("txIds", l.txIds() != null ? l.txIds() : Collections.emptyList());
        return map;
    }

    private Map<String, Object> toChargeMap(ChargeModel c) {
        if (c == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.id());
        map.put("label", c.label());
        map.put("amount", c.amount());
        map.put("dualAmount", c.dualAmount());
        map.put("amountP1", c.amountP1());
        map.put("amountP2", c.amountP2());
        map.put("growthRate", c.growthRate());
        map.put("adjustWithInflation", c.adjustWithInflation());
        map.put("start", c.start());
        map.put("end", c.end());
        map.put("categoryId", c.categoryId());
        return map;
    }

    private Map<String, Object> toIncomeMap(IncomeModel i) {
        if (i == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", i.id());
        map.put("label", i.label());
        map.put("amount", i.amount());
        map.put("growthRate", i.growthRate());
        map.put("start", i.start());
        map.put("end", i.end());
        map.put("categoryId", i.categoryId());
        return map;
    }

    private Map<String, Object> toPlacementMap(PlacementModel p) {
        if (p == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.id());
        map.put("label", p.label());
        map.put("monthly", p.monthly());
        map.put("monthlyFrom", p.monthlyFrom());
        map.put("monthlyUntil", p.monthlyUntil());
        map.put("categoryId", p.categoryId());
        return map;
    }

    private Map<String, Object> toSettingsMap(SettingsModel s) {
        if (s == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("inflationRate", s.inflationRate());
        map.put("targetRetirementAge", s.targetRetirementAge());
        map.put("childExitAge", s.childExitAge());
        map.put("taxAbattement", s.taxAbattement());
        return map;
    }
}
