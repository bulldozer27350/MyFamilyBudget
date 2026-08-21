package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PatrimoineApi;
import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.server.internal.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@RestController
public class PatrimoineServiceImpl implements PatrimoineApi {

    private final PatrimoineMapper mapper;
    private final PersistenceManager persistenceManager;

    public PatrimoineServiceImpl(PatrimoineMapper mapper, PersistenceManager persistenceManager) {
        this.mapper = mapper;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public ResponseEntity<PatrimoineResponseDto> getPatrimoine(Boolean useConstantEuros) {
        BudgetDataModel data = this.persistenceManager.getBudgetData();
        PatrimoineProjectionsModel projections = computePatrimoineProjections(data, Boolean.TRUE.equals(useConstantEuros));
        PatrimoineResponseDto response = this.mapper.toPatrimoineResponseDto(data, projections);
        return ResponseEntity.ok(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> savePatrimoineLigne(String listKey, Object body) {
        Map<String, Object> map = (body instanceof Map) ? (Map<String, Object>) body : null;
        this.persistenceManager.savePatrimoineRow(listKey, map);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePatrimoineLigne(String listKey, String id) {
        this.persistenceManager.deletePatrimoineRow(listKey, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> addPlacementHistoriquePoint(String placementId, AddPlacementHistoriquePointRequest body) {
        if (body != null) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("date", body.getDate());
            map.put("value", body.getValue());
            this.persistenceManager.addPlacementHistoriquePoint(placementId, map);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePlacementHistoriquePoint(String placementId, Integer index) {
        this.persistenceManager.deletePlacementHistoriquePoint(placementId, index);
        return ResponseEntity.noContent().build();
    }

    public PatrimoineProjectionsModel computePatrimoineProjections(BudgetDataModel data, boolean useConstantEuros) {
        SettingsModel settings = data.settings() != null ? data.settings() : new SettingsModel(
                1985, 64, 85, BigDecimal.ZERO, null, null, BigDecimal.ZERO, 21, BigDecimal.ZERO, new BigDecimal("47100"), new BigDecimal("0.015")
        );

        int startYear = findEarliestYear(data);
        int endYear = settings.getEffectiveBirthYear() + settings.getEffectiveRetireAge();
        if (endYear < startYear) {
            endYear = startYear + 40;
        }

        List<Integer> years = new ArrayList<>();
        for (int y = startYear; y <= endYear; y++) {
            years.add(y);
        }

        BigDecimal inflationRate = settings.getEffectiveInflationRate();
        List<PatrimoinePerPlacementModel> perPlacement = new ArrayList<>();

        for (PlacementModel p : data.getEffectivePlacements()) {
            BigDecimal pess = p.getEffectiveBalance();
            BigDecimal corr = p.getEffectiveBalance();
            BigDecimal opti = p.getEffectiveBalance();

            Integer monthlyFromYear = yearOf(p.monthlyFrom());
            if (monthlyFromYear == null) monthlyFromYear = years.isEmpty() ? 2026 : years.get(0);

            Integer monthlyUntilYear = yearOf(p.monthlyUntil());

            List<PatrimoineYearModel> rows = new ArrayList<>();
            for (int year : years) {
                BigDecimal withdraw = BigDecimal.ZERO;
                for (TransferModel t : data.getEffectiveTransfers()) {
                    if (p.label() != null && p.label().equalsIgnoreCase(t.placement()) && yearOf(t.date()) != null && yearOf(t.date()) == year) {
                        withdraw = withdraw.add(t.getEffectiveAmount());
                    }
                }

                boolean withinWindow = year >= monthlyFromYear && (monthlyUntilYear == null || year <= monthlyUntilYear);
                BigDecimal monthlyContrib = withinWindow ? p.getEffectiveMonthly().multiply(BigDecimal.valueOf(12)) : BigDecimal.ZERO;

                pess = pess.multiply(BigDecimal.ONE.add(p.getEffectiveRatePess())).add(monthlyContrib).subtract(withdraw);
                corr = corr.multiply(BigDecimal.ONE.add(p.getEffectiveRateCorr())).add(monthlyContrib).subtract(withdraw);
                opti = opti.multiply(BigDecimal.ONE.add(p.getEffectiveRateOpti())).add(monthlyContrib).subtract(withdraw);

                rows.add(new PatrimoineYearModel(year, pess, corr, opti));
            }
            perPlacement.add(new PatrimoinePerPlacementModel(p.label(), rows));
        }

        List<PatrimoineYearModel> totals = new ArrayList<>();
        for (int idx = 0; idx < years.size(); idx++) {
            int year = years.get(idx);
            double deflatorVal = useConstantEuros ? Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), year - startYear) : 1.0;
            BigDecimal deflator = BigDecimal.valueOf(deflatorVal);

            BigDecimal totalPess = BigDecimal.ZERO;
            BigDecimal totalCorr = BigDecimal.ZERO;
            BigDecimal totalOpti = BigDecimal.ZERO;

            for (PatrimoinePerPlacementModel pp : perPlacement) {
                PatrimoineYearModel row = pp.rows().get(idx);
                totalPess = totalPess.add(row.pess());
                totalCorr = totalCorr.add(row.corr());
                totalOpti = totalOpti.add(row.opti());
            }

            totals.add(new PatrimoineYearModel(year, totalPess.multiply(deflator), totalCorr.multiply(deflator), totalOpti.multiply(deflator)));
        }

        return new PatrimoineProjectionsModel(perPlacement, totals);
    }

    private int findEarliestYear(BudgetDataModel data) {
        List<String> dates = new ArrayList<>();

        for (IncomeModel i : data.getEffectiveIncomes()) if (i.start() != null) dates.add(i.start());
        for (ChargeModel c : data.getEffectiveCharges()) if (c.start() != null) dates.add(c.start());
        for (PlacementModel p : data.getEffectivePlacements()) {
            if (p.monthlyFrom() != null) dates.add(p.monthlyFrom());
            if (p.balanceDate() != null) dates.add(p.balanceDate());
        }
        for (OneOffExpenseModel o : data.getEffectiveOneoff()) if (o.date() != null) dates.add(o.date());
        for (TransferModel t : data.getEffectiveTransfers()) if (t.date() != null) dates.add(t.date());
        if (data.settings() != null && data.settings().pivotDate() != null) dates.add(data.settings().pivotDate());

        int earliestYear = 2026;
        boolean found = false;

        for (String d : dates) {
            Integer y = yearOf(d);
            if (y != null) {
                if (!found || y < earliestYear) {
                    earliestYear = y;
                    found = true;
                }
            }
        }

        return found ? earliestYear : 2026;
    }

    private Integer yearOf(String dateISO) {
        LocalDate d = parseDate(dateISO);
        return d != null ? d.getYear() : null;
    }

    private LocalDate parseDate(String dateISO) {
        if (dateISO == null || dateISO.isBlank()) return null;
        try {
            if (dateISO.length() == 7) {
                YearMonth ym = YearMonth.parse(dateISO);
                return ym.atDay(1);
            }
            return LocalDate.parse(dateISO.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
