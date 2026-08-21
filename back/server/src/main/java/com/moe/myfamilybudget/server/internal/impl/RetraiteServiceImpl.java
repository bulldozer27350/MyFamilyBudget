package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.RetraiteApi;
import com.moe.myfamilybudget.server.internal.mapper.RetraiteMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.RetraitePersonWithProjectionModel;
import com.moe.myfamilybudget.server.internal.model.RetraiteResultModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.RetirementProjectionModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class RetraiteServiceImpl implements RetraiteApi {

    private static final int TRIMESTRES_REQUIS = 172;
    private static final int AGE_TAUX_PLEIN_AUTO = 67;
    private static final BigDecimal DECOTE_PAR_TRIMESTRE = new BigDecimal("0.00625");
    private static final BigDecimal SURCOTE_PAR_TRIMESTRE = new BigDecimal("0.0125");
    private static final BigDecimal TAUX_PLEIN = new BigDecimal("0.50");
    private static final BigDecimal TAUX_MINORE_PLANCHER = new BigDecimal("0.375");
    private static final BigDecimal MAJORATION_3_ENFANTS = new BigDecimal("0.10");

    private final PersistenceManager persistenceManager;
    private final RetraiteMapper retraiteMapper;

    public RetraiteServiceImpl(PersistenceManager persistenceManager, RetraiteMapper retraiteMapper) {
        this.persistenceManager = persistenceManager;
        this.retraiteMapper = retraiteMapper;
    }

    @Override
    public ResponseEntity<Object> getRetraite() {
        RetraiteResultModel resultModel = buildRetraiteResult();
        Map<String, Object> response = retraiteMapper.toResponseMap(resultModel);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveRetraite(Object body) {
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            RetirementModel model = retraiteMapper.toRetirementModelFromMap(typedMap);
            persistenceManager.updateRetirement(model);
        }
        return ResponseEntity.ok().build();
    }

    public RetraiteResultModel buildRetraiteResult() {
        BudgetDataModel data = persistenceManager.getBudgetData();
        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
            1985, 64, 85, new BigDecimal("0.02"), "", "manual", BigDecimal.ZERO, 21, new BigDecimal("0.10"), new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int retireYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();

        RetirementModel retirement = data.retirement();
        List<RetraitePersonWithProjectionModel> peopleWithProj = new ArrayList<>();

        if (retirement != null && retirement.people() != null) {
            for (RetirementModel.RetirementPersonModel person : retirement.people()) {
                RetirementProjectionModel proj = computeRetirementProjection(data, person, retireYear);
                peopleWithProj.add(new RetraitePersonWithProjectionModel(
                    person.id(),
                    person.name(),
                    person.birthYear(),
                    person.incomeLabel(),
                    person.trimestresValides(),
                    person.trimestresDate(),
                    person.salaryHistory(),
                    person.agircPoints(),
                    person.ratioPointsParEuro(),
                    proj
                ));
            }
        }

        RetraiteResultModel.RetirementWithProjectionsModel retWithProj = new RetraiteResultModel.RetirementWithProjectionsModel(
            peopleWithProj,
            retirement != null ? retirement.getEffectivePass2026() : new BigDecimal("47100"),
            retirement != null ? retirement.getEffectivePassGrowthRate() : new BigDecimal("0.015"),
            retirement != null ? retirement.getEffectiveAgircPointValue() : new BigDecimal("1.4386"),
            retirement != null ? retirement.agircPointDateGlobal() : "2025-11-01",
            retirement != null ? retirement.getEffectiveAgircPointGrowthRate() : new BigDecimal("0.01")
        );

        return new RetraiteResultModel(
            retWithProj,
            retireYear,
            data.getEffectiveIncomes(),
            settings
        );
    }

    public RetirementProjectionModel computeRetirementProjection(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int retireYear) {
        int birthYear = person.birthYear() != null ? person.birthYear() : (data.settings() != null ? data.settings().getEffectiveBirthYear() : 1985);
        int trimestresValides = person.getEffectiveTrimestresValides();
        int trimestresDateYear = yearOf(person.trimestresDate()) != null ? yearOf(person.trimestresDate()) : LocalDate.now().getYear() - 1;

        int trimestresFuturs = 0;
        for (int y = trimestresDateYear + 1; y <= retireYear; y++) {
            if (projectedAnnualSalary(data, person, y).compareTo(BigDecimal.ZERO) > 0) {
                trimestresFuturs += 4;
            }
        }

        int trimestresEstimesDepart = trimestresValides + trimestresFuturs;
        int ageDepart = retireYear - birthYear;
        int trimestresJusquTauxPleinAuto = Math.max(0, (AGE_TAUX_PLEIN_AUTO - ageDepart) * 4);

        BigDecimal tauxApplique = TAUX_PLEIN;
        BigDecimal decote = BigDecimal.ZERO;
        BigDecimal surcote = BigDecimal.ZERO;

        if (trimestresEstimesDepart < TRIMESTRES_REQUIS) {
            int manquants = TRIMESTRES_REQUIS - trimestresEstimesDepart;
            int trimestresDecote = Math.min(manquants, trimestresJusquTauxPleinAuto);
            decote = DECOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(trimestresDecote));
            tauxApplique = TAUX_PLEIN.subtract(decote).max(TAUX_MINORE_PLANCHER);
        } else if (trimestresEstimesDepart > TRIMESTRES_REQUIS) {
            int surplus = trimestresEstimesDepart - TRIMESTRES_REQUIS;
            surcote = SURCOTE_PAR_TRIMESTRE.multiply(BigDecimal.valueOf(surplus));
            tauxApplique = TAUX_PLEIN.add(surcote);
        }

        Map<Integer, BigDecimal> byYear = new HashMap<>();
        for (RetirementModel.SalaryHistoryModel h : person.getEffectiveSalaryHistory()) {
            if (h.year() != null && h.getEffectiveSalary().compareTo(BigDecimal.ZERO) > 0) {
                byYear.put(h.year(), h.getEffectiveSalary());
            }
        }

        List<YearSalary> futureYears = new ArrayList<>();
        for (int y = trimestresDateYear + 1; y <= retireYear - 1; y++) {
            BigDecimal s = projectedAnnualSalary(data, person, y);
            if (s.compareTo(BigDecimal.ZERO) > 0) {
                futureYears.add(new YearSalary(y, s));
                if (!byYear.containsKey(y)) {
                    byYear.put(y, s);
                }
            }
        }

        List<YearSalary> allEntries = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : byYear.entrySet()) {
            allEntries.add(new YearSalary(e.getKey(), e.getValue()));
        }
        allEntries.sort((a, b) -> Integer.compare(b.year(), a.year()));

        List<YearSalary> last25 = allEntries.subList(0, Math.min(25, allEntries.size()));
        BigDecimal sumCapped = BigDecimal.ZERO;
        for (YearSalary ys : last25) {
            BigDecimal pass = passForYear(data, ys.year());
            BigDecimal capped = ys.salary().min(pass);
            sumCapped = sumCapped.add(capped);
        }

        BigDecimal sam = last25.isEmpty() ? BigDecimal.ZERO : sumCapped.divide(BigDecimal.valueOf(last25.size()), 10, RoundingMode.HALF_UP);
        BigDecimal majoration = data.getEffectiveTaxChildren().size() >= 3 ? BigDecimal.ONE.add(MAJORATION_3_ENFANTS) : BigDecimal.ONE;

        double ratioTrimestresVal = Math.min(trimestresEstimesDepart, TRIMESTRES_REQUIS) / (double) TRIMESTRES_REQUIS;
        BigDecimal ratioTrimestres = BigDecimal.valueOf(ratioTrimestresVal);

        BigDecimal pensionBaseAnnuelle = sam.multiply(tauxApplique).multiply(ratioTrimestres).multiply(majoration);

        BigDecimal pointsActuels = person.getEffectiveAgircPoints();
        BigDecimal ratioPointsParEuro = person.getEffectiveRatioPointsParEuro();

        BigDecimal pointsFuturs = BigDecimal.ZERO;
        for (YearSalary fy : futureYears) {
            pointsFuturs = pointsFuturs.add(fy.salary().multiply(ratioPointsParEuro));
        }

        BigDecimal pointsEstimes = pointsActuels.add(pointsFuturs);
        BigDecimal valeurPointDepart = agircPointValueForYear(data, retireYear);
        BigDecimal pensionComplementaireAnnuelle = pointsEstimes.multiply(valeurPointDepart).multiply(majoration);

        BigDecimal pensionTotaleAnnuelle = pensionBaseAnnuelle.add(pensionComplementaireAnnuelle);
        BigDecimal pensionTotaleMensuelle = pensionTotaleAnnuelle.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        return new RetirementProjectionModel(
            ageDepart,
            trimestresValides,
            trimestresEstimesDepart,
            TRIMESTRES_REQUIS,
            trimestresEstimesDepart < TRIMESTRES_REQUIS,
            tauxApplique,
            decote,
            surcote,
            sam,
            majoration,
            pensionBaseAnnuelle,
            pointsEstimes,
            valeurPointDepart,
            pensionComplementaireAnnuelle,
            pensionTotaleAnnuelle,
            pensionTotaleMensuelle
        );
    }

    private record YearSalary(int year, BigDecimal salary) {}

    private BigDecimal projectedAnnualSalary(BudgetDataModel data, RetirementModel.RetirementPersonModel person, int year) {
        if (person.incomeLabel() == null || person.incomeLabel().isBlank()) {
            return BigDecimal.ZERO;
        }
        Optional<IncomeModel> income = data.getEffectiveIncomes().stream()
                .filter(i -> person.incomeLabel().equalsIgnoreCase(i.label()))
                .findFirst();
        return income.map(incomeModel -> incomeAnnualForYear(incomeModel, year)).orElse(BigDecimal.ZERO);
    }

    private BigDecimal passForYear(BudgetDataModel data, int year) {
        BigDecimal base = (data.retirement() != null) ? data.retirement().getEffectivePass2026() : new BigDecimal("47100");
        BigDecimal growth = (data.retirement() != null) ? data.retirement().getEffectivePassGrowthRate() : new BigDecimal("0.015");

        double factor = Math.pow(1.0 + growth.doubleValue(), year - 2026);
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal agircPointValueForYear(BudgetDataModel data, int year) {
        BigDecimal base = (data.retirement() != null) ? data.retirement().getEffectiveAgircPointValue() : new BigDecimal("1.4386");
        Integer baseYear = (data.retirement() != null) ? yearOf(data.retirement().agircPointDateGlobal()) : 2025;
        if (baseYear == null) baseYear = 2025;

        BigDecimal growth = (data.retirement() != null) ? data.retirement().getEffectiveAgircPointGrowthRate() : new BigDecimal("0.01");
        int elapsed = Math.max(0, year - baseYear);

        double factor = Math.pow(1.0 + growth.doubleValue(), elapsed);
        return base.multiply(BigDecimal.valueOf(factor));
    }

    private BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;
        BigDecimal growth = row.getEffectiveGrowthRate();
        int yearsElapsed = Math.max(0, year - startYear);
        double growthFactor = Math.pow(1.0 + growth.doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(growthFactor));
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActiveInYear(row.start(), row.end(), year)));
    }

    private int monthsActiveInYear(String startISO, String endISO, int year) {
        if (startISO == null || endISO == null || startISO.isBlank() || endISO.isBlank()) return 0;
        try {
            LocalDate start = LocalDate.parse(startISO);
            LocalDate end = LocalDate.parse(endISO);
            LocalDate yStart = LocalDate.of(year, 1, 1);
            LocalDate yEnd = LocalDate.of(year, 12, 31);
            LocalDate s = start.isAfter(yStart) ? start : yStart;
            LocalDate e = end.isBefore(yEnd) ? end : yEnd;
            if (e.isBefore(s)) return 0;
            return (e.getYear() - s.getYear()) * 12 + (e.getMonthValue() - s.getMonthValue()) + 1;
        } catch (Exception ex) {
            return 0;
        }
    }

    private Integer yearOf(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr).getYear();
        } catch (Exception e) {
            try {
                return Integer.parseInt(dateStr.substring(0, 4));
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
