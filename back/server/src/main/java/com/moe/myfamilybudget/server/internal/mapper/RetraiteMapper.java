package com.moe.myfamilybudget.server.internal.mapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.RetirementDto;
import com.moe.myfamilybudget.api.model.RetirementPersonDto;
import com.moe.myfamilybudget.api.model.SalaryHistoryDto;
import com.moe.myfamilybudget.api.model.SettingsDto;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.server.internal.model.RetraiteResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

@Component
public class RetraiteMapper {

    /**
     * Convertit un RetraiteResultModel en Map<String, Object> prêt à être sérialisé en JSON.
     */
    public Map<String, Object> toResponseMap(RetraiteResultModel model) {
        if (model == null) {
            return new HashMap<>();
        }

        Map<String, Object> response = new HashMap<>();

        // Retirement section with projections
        if (model.retirement() != null) {
            Map<String, Object> retMap = new HashMap<>();
            retMap.put("pass2026", model.retirement().pass2026());
            retMap.put("passGrowthRate", model.retirement().passGrowthRate());
            retMap.put("agircPointValue", model.retirement().agircPointValue());
            retMap.put("agircPointDateGlobal", model.retirement().agircPointDateGlobal());
            retMap.put("agircPointGrowthRate", model.retirement().agircPointGrowthRate());

            List<Map<String, Object>> peopleList = new ArrayList<>();
            if (model.retirement().people() != null) {
                for (RetraitePersonWithProjectionModel person : model.retirement().people()) {
                    Map<String, Object> pMap = new HashMap<>();
                    pMap.put("id", person.id());
                    pMap.put("name", person.name());
                    pMap.put("birthYear", person.birthYear());
                    pMap.put("incomeLabel", person.incomeLabel());
                    pMap.put("trimestresValides", person.trimestresValides());
                    pMap.put("trimestresDate", person.trimestresDate());
                    pMap.put("agircPoints", person.agircPoints());
                    pMap.put("ratioPointsParEuro", person.ratioPointsParEuro());

                    List<Map<String, Object>> salList = new ArrayList<>();
                    if (person.salaryHistory() != null) {
                        for (RetirementModel.SalaryHistoryModel sh : person.salaryHistory()) {
                            Map<String, Object> shMap = new HashMap<>();
                            shMap.put("year", sh.year());
                            shMap.put("salary", sh.salary());
                            salList.add(shMap);
                        }
                    }
                    pMap.put("salaryHistory", salList);

                    if (person.projection() != null) {
                        Map<String, Object> projMap = toProjectionMap(person.projection());
                        pMap.put("projection", projMap);
                    }

                    peopleList.add(pMap);
                }
            }
            retMap.put("people", peopleList);
            response.put("retirement", retMap);
        }

        response.put("retireYear", model.retireYear());

        if (model.incomes() != null) {
            List<IncomeDto> incomeDtos = model.incomes().stream().map(this::toIncomeDto).collect(Collectors.toList());
            response.put("incomes", incomeDtos);
        } else {
            response.put("incomes", List.of());
        }

        if (model.settings() != null) {
            response.put("settings", toSettingsDto(model.settings()));
        } else {
            response.put("settings", new SettingsDto());
        }

        return response;
    }

    public Map<String, Object> toProjectionMap(RetirementProjectionModel proj) {
        if (proj == null) return new HashMap<>();
        Map<String, Object> projMap = new HashMap<>();
        projMap.put("ageDepart", proj.ageDepart());
        projMap.put("trimestresValides", proj.trimestresValides());
        projMap.put("trimestresEstimesDepart", proj.trimestresEstimesDepart());
        projMap.put("trimestresRequis", proj.trimestresRequis());
        projMap.put("manqueTauxPlein", proj.manqueTauxPlein());
        projMap.put("tauxAppliqué", proj.tauxApplique());
        projMap.put("decote", proj.decote());
        projMap.put("surcote", proj.surcote());
        projMap.put("SAM", proj.sam());
        projMap.put("majoration", proj.majoration());
        projMap.put("pensionBaseAnnuelle", proj.pensionBaseAnnuelle());
        projMap.put("pointsEstimes", proj.pointsEstimes());
        projMap.put("valeurPointDepart", proj.valeurPointDepart());
        projMap.put("pensionComplementaireAnnuelle", proj.pensionComplementaireAnnuelle());
        projMap.put("pensionTotaleAnnuelle", proj.pensionTotaleAnnuelle());
        projMap.put("pensionTotaleMensuelle", proj.pensionTotaleMensuelle());
        return projMap;
    }

    public RetirementModel toRetirementModelFromMap(Map<String, Object> map) {
        if (map == null) {
            return new RetirementModel(List.of(), new BigDecimal("47100"), new BigDecimal("0.015"), new BigDecimal("1.4386"), "2025-11-01", new BigDecimal("0.01"));
        }

        BigDecimal pass2026 = toBigDecimal(map.get("pass2026"), new BigDecimal("47100"));
        BigDecimal passGrowthRate = toBigDecimal(map.get("passGrowthRate"), new BigDecimal("0.015"));
        BigDecimal agircPointValue = toBigDecimal(map.get("agircPointValue"), new BigDecimal("1.4386"));
        String agircPointDateGlobal = map.get("agircPointDateGlobal") != null ? String.valueOf(map.get("agircPointDateGlobal")) : "2025-11-01";
        BigDecimal agircPointGrowthRate = toBigDecimal(map.get("agircPointGrowthRate"), new BigDecimal("0.01"));

        List<RetirementModel.RetirementPersonModel> people = new ArrayList<>();
        Object peopleObj = map.get("people");
        if (peopleObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> pMap) {
                    String id = getString(pMap, "id", null);
                    String name = getString(pMap, "name", "");
                    Integer birthYear = getInteger(pMap, "birthYear", null);
                    String incomeLabel = getString(pMap, "incomeLabel", "");
                    Integer trimestresValides = getInteger(pMap, "trimestresValides", 0);
                    String trimestresDate = getString(pMap, "trimestresDate", "");
                    BigDecimal agircPoints = toBigDecimal(pMap.get("agircPoints"), BigDecimal.ZERO);
                    BigDecimal ratioPointsParEuro = toBigDecimal(pMap.get("ratioPointsParEuro"), new BigDecimal("0.0051"));

                    List<RetirementModel.SalaryHistoryModel> salHistory = new ArrayList<>();
                    Object salObj = pMap.get("salaryHistory");
                    if (salObj instanceof List<?> salList) {
                        for (Object shItem : salList) {
                            if (shItem instanceof Map<?, ?> shMap) {
                                Integer year = getInteger(shMap, "year", null);
                                BigDecimal salary = toBigDecimal(shMap.get("salary"), BigDecimal.ZERO);
                                if (year != null) {
                                    salHistory.add(new RetirementModel.SalaryHistoryModel(year, salary));
                                }
                            }
                        }
                    }

                    people.add(new RetirementModel.RetirementPersonModel(
                        id, name, birthYear, incomeLabel, trimestresValides, trimestresDate, salHistory, agircPoints, ratioPointsParEuro
                    ));
                }
            }
        }

        return new RetirementModel(people, pass2026, passGrowthRate, agircPointValue, agircPointDateGlobal, agircPointGrowthRate);
    }

    private IncomeDto toIncomeDto(IncomeModel m) {
        if (m == null) return null;
        IncomeDto dto = new IncomeDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setMonthly(m.monthly());
        dto.setStart(m.start());
        dto.setEnd(m.end());
        dto.setGrowthRate(m.growthRate());
        dto.setCategoryId(m.categoryId());
        dto.setNotes(m.notes());
        return dto;
    }

    private SettingsDto toSettingsDto(SettingsModel model) {
        if (model == null) return null;
        SettingsDto dto = new SettingsDto();
        dto.setBirthYear(model.birthYear());
        dto.setRetireAge(model.retireAge());
        dto.setSimulateUntilAge(model.simulateUntilAge());
        dto.setInflationRate(model.inflationRate());
        dto.setPivotDate(model.pivotDate());
        dto.setPivotMode(model.pivotMode());
        dto.setStartBalance(model.startBalance());
        dto.setChildExitAge(model.childExitAge());
        dto.setTaxAbattement(model.taxAbattement());
        dto.setPass2026(model.pass2026());
        dto.setPassGrowthRate(model.passGrowthRate());
        return dto;
    }

    private String getString(Map<?, ?> map, String key, String fallback) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : fallback;
    }

    private Integer getInteger(Map<?, ?> map, String key, Integer fallback) {
        Object val = map.get(key);
        if (val == null) return fallback;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal toBigDecimal(Object val, BigDecimal fallback) {
        if (val == null) return fallback;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(val).trim().replace(",", ".");
            if (s.isEmpty()) return fallback;
            return new BigDecimal(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
