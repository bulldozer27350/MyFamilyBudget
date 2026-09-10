package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PatrimoineApi;
import com.moe.myfamilybudget.api.model.AddPlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.UpdatePlacementHistoriquePointRequest;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionPointDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.server.internal.mapper.PatrimoineMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementHistoryEntryModel;
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
    public ResponseEntity<PlacementHistoryEntryDto> addPlacementHistoriquePoint(String placementId, AddPlacementHistoriquePointRequest body) {
        if (body == null) return ResponseEntity.ok().build();
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("date", body.getDate());
        map.put("value", body.getValue());
        map.put("notes", body.getNotes());
        Map<String, Object> saved = this.persistenceManager.addPlacementHistoryEntry(placementId, map);
        PlacementHistoryEntryDto dto = new PlacementHistoryEntryDto();
        dto.setId((String) saved.get("id"));
        dto.setDate((String) saved.get("date"));
        Object value = saved.get("value");
        if (value instanceof BigDecimal bd) dto.setValue(bd);
        dto.setNotes((String) saved.get("notes"));
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Void> updatePlacementHistoriquePoint(String placementId, String entryId, UpdatePlacementHistoriquePointRequest body) {
        if (body != null) {
            Map<String, Object> map = new java.util.HashMap<>();
            if (body.getDate() != null) map.put("date", body.getDate());
            if (body.getValue() != null) map.put("value", body.getValue());
            if (body.getNotes() != null) map.put("notes", body.getNotes());
            this.persistenceManager.updatePlacementHistoryEntry(placementId, entryId, map);
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> deletePlacementHistoriquePoint(String placementId, String entryId) {
        this.persistenceManager.deletePlacementHistoryEntry(placementId, entryId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PlacementEvolutionDto> getPlacementEvolution(String placementId, Boolean useConstantEuros) {
        BudgetDataModel data = this.persistenceManager.getBudgetData();
        PlacementModel placement = data.getEffectivePlacements().stream()
                .filter(p -> java.util.Objects.equals(p.id(), placementId))
                .findFirst()
                .orElse(null);
        if (placement == null) {
            return ResponseEntity.notFound().build();
        }
        SettingsModel settings = data.settings();
        BigDecimal inflationRate = settings != null ? settings.getEffectiveInflationRate() : BigDecimal.ZERO;
        PlacementEvolutionDto dto = computePlacementEvolution(
                placement, data.getEffectiveTransfers(), Boolean.TRUE.equals(useConstantEuros), inflationRate
        );
        return ResponseEntity.ok(dto);
    }

    /**
     * Construit la chronologie d'un placement pour la fenetre dediee "Historique" : un segment
     * "reel" (valeurs saisies a la main, triees par date) suivi de 3 segments de projection
     * (pessimiste / correcte / optimiste) qui repartent tous du dernier point reel connu — ou,
     * a defaut d'historique saisi, du solde/date de reference du placement. Portage direct de
     * buildPlacementTimeline (view/js/calculations.js), avec application du deflateur
     * "euros constants" (recupere du meme reglage que la vue principale) sur les 3 projections
     * uniquement — les valeurs reelles saisies restent affichees telles quelles.
     */
    private PlacementEvolutionDto computePlacementEvolution(PlacementModel placement, List<TransferModel> transfers,
                                                              boolean useConstantEuros, BigDecimal inflationRate) {
        int horizonYears = 15;
        LocalDate today = LocalDate.now();

        List<PlacementHistoryEntryModel> history = new ArrayList<>(placement.getEffectiveHistory());
        history.removeIf(h -> h.date() == null || parseDate(h.date()) == null);
        history.sort(Comparator.comparing(h -> parseDate(h.date())));

        LocalDate anchorDate;
        BigDecimal anchorValue;
        if (!history.isEmpty()) {
            PlacementHistoryEntryModel last = history.get(history.size() - 1);
            anchorDate = parseDate(last.date());
            anchorValue = last.getEffectiveValue();
        } else if (placement.balanceDate() != null && parseDate(placement.balanceDate()) != null) {
            anchorDate = parseDate(placement.balanceDate());
            anchorValue = placement.getEffectiveBalance();
        } else {
            anchorDate = today;
            anchorValue = placement.getEffectiveBalance();
        }

        List<PlacementEvolutionPointDto> points = new ArrayList<>();
        for (PlacementHistoryEntryModel h : history) {
            LocalDate d = parseDate(h.date());
            PlacementEvolutionPointDto pt = new PlacementEvolutionPointDto();
            pt.setTimestamp(toEpochMillis(d));
            pt.setDateISO(d.toString());
            pt.setLabel(formatLabel(d));
            pt.setReal(h.getEffectiveValue());
            points.add(pt);
        }

        PlacementEvolutionPointDto anchorPoint = points.stream()
                .filter(p -> p.getDateISO().equals(anchorDate.toString()))
                .findFirst()
                .orElse(null);
        if (anchorPoint == null) {
            anchorPoint = new PlacementEvolutionPointDto();
            anchorPoint.setTimestamp(toEpochMillis(anchorDate));
            anchorPoint.setDateISO(anchorDate.toString());
            anchorPoint.setLabel(formatLabel(anchorDate));
            anchorPoint.setReal(anchorValue);
            points.add(anchorPoint);
            points.sort(Comparator.comparing(PlacementEvolutionPointDto::getTimestamp));
        }
        anchorPoint.setPess(anchorValue);
        anchorPoint.setCorr(anchorValue);
        anchorPoint.setOpti(anchorValue);

        BigDecimal runningPess = anchorValue;
        BigDecimal runningCorr = anchorValue;
        BigDecimal runningOpti = anchorValue;
        BigDecimal monthlyContrib = placement.getEffectiveMonthly();
        YearMonth monthlyFrom = placement.monthlyFrom() != null && parseDate(placement.monthlyFrom()) != null
                ? YearMonth.from(parseDate(placement.monthlyFrom())) : YearMonth.from(anchorDate);
        YearMonth monthlyUntil = placement.monthlyUntil() != null && parseDate(placement.monthlyUntil()) != null
                ? YearMonth.from(parseDate(placement.monthlyUntil())) : null;

        YearMonth cursor = YearMonth.from(anchorDate);
        YearMonth end = cursor.plusYears(horizonYears);
        int elapsedMonths = 0;
        while (cursor.isBefore(end)) {
            boolean withinContribWindow = !cursor.isBefore(monthlyFrom) && (monthlyUntil == null || !cursor.isAfter(monthlyUntil));
            YearMonth cursorFinal = cursor;
            BigDecimal withdrawn = transfers.stream()
                    .filter(t -> placement.label() != null && placement.label().equalsIgnoreCase(t.placement())
                            && t.date() != null && parseDate(t.date()) != null
                            && YearMonth.from(parseDate(t.date())).equals(cursorFinal))
                    .map(TransferModel::getEffectiveAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            runningPess = applyMonth(runningPess, placement.getEffectiveRatePess(), monthlyContrib, withinContribWindow, withdrawn);
            runningCorr = applyMonth(runningCorr, placement.getEffectiveRateCorr(), monthlyContrib, withinContribWindow, withdrawn);
            runningOpti = applyMonth(runningOpti, placement.getEffectiveRateOpti(), monthlyContrib, withinContribWindow, withdrawn);

            cursor = cursor.plusMonths(1);
            elapsedMonths++;
            LocalDate d = cursor.atDay(1);
            double deflatorVal = useConstantEuros
                    ? Math.pow(1.0 / (1.0 + inflationRate.doubleValue()), elapsedMonths / 12.0)
                    : 1.0;
            BigDecimal deflator = BigDecimal.valueOf(deflatorVal);

            PlacementEvolutionPointDto pt = new PlacementEvolutionPointDto();
            pt.setTimestamp(toEpochMillis(d));
            pt.setDateISO(d.toString());
            pt.setLabel(formatLabel(d));
            pt.setPess(runningPess.multiply(deflator));
            pt.setCorr(runningCorr.multiply(deflator));
            pt.setOpti(runningOpti.multiply(deflator));
            points.add(pt);
        }

        points.sort(Comparator.comparing(PlacementEvolutionPointDto::getTimestamp));

        PlacementEvolutionDto dto = new PlacementEvolutionDto();
        dto.setPlacementId(placement.id());
        dto.setAnchorTimestamp(anchorPoint.getTimestamp());
        dto.setTodayTimestamp(toEpochMillis(today));
        dto.setPoints(points);
        return dto;
    }

    private BigDecimal applyMonth(BigDecimal running, BigDecimal annualRate, BigDecimal monthlyContrib,
                                   boolean withinContribWindow, BigDecimal withdrawn) {
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), java.math.MathContext.DECIMAL64);
        BigDecimal next = running.multiply(BigDecimal.ONE.add(monthlyRate));
        if (withinContribWindow) next = next.add(monthlyContrib);
        next = next.subtract(withdrawn);
        return next.max(BigDecimal.ZERO);
    }

    private long toEpochMillis(LocalDate date) {
        return date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private String formatLabel(LocalDate d) {
        String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"};
        return String.format("%02d/%s/%d", d.getDayOfMonth(), monthNames[d.getMonthValue() - 1], d.getYear());
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
