package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        PlacementEvolutionDto dto = computePlacementEvolution(data, placement, Boolean.TRUE.equals(useConstantEuros));
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
    private PlacementEvolutionDto computePlacementEvolution(BudgetDataModel data, PlacementModel placement, boolean useConstantEuros) {
        List<TransferModel> transfers = data.getEffectiveTransfers();
        List<PlacementModel> allPlacements = data.getEffectivePlacements();
        SettingsModel settings = data.getEffectiveSettings();
        BigDecimal inflationRate = settings.getEffectiveInflationRate();
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

        // --- Mecanisme de pause automatique des versements (portage de
        // view/js/calculations.js L.420-427, 509-511, 589-599) ---
        // Le placement trace n'est qu'un des placements du budget : pour savoir s'IL doit
        // etre mis en pause, il faut simuler en arriere-plan (scenario "correle" uniquement,
        // cf. decision de conception) le solde de TOUS les placements, mois par mois, car
        // n'importe lequel peut faire partie des "bufferWatch" qui declenchent la pause.
        List<PlacementModel> pausablePlacements = new ArrayList<>();
        List<PlacementModel> bufferWatch = new ArrayList<>();
        boolean hasSweepAccounts = false;
        for (PlacementModel p : allPlacements) {
            if (p.pausePriority() != null) pausablePlacements.add(p);
            if (p.pauseTriggerBalance() != null) bufferWatch.add(p);
            if (p.sweepPriority() != null) hasSweepAccounts = true;
        }
        int maxPauseLevel = 0;
        for (PlacementModel p : pausablePlacements) {
            maxPauseLevel = Math.max(maxPauseLevel, p.pausePriority());
        }
        boolean sweepEnabled = Boolean.TRUE.equals(settings.sweepEnabled());
        BigDecimal cashFloor = settings.cashFloor() != null ? settings.cashFloor() : BigDecimal.ZERO;
        BigDecimal cashCeiling = settings.cashCeiling();

        Map<String, BigDecimal> backgroundCorr = new HashMap<>();
        Map<String, YearMonth> backgroundFrom = new HashMap<>();
        Map<String, YearMonth> backgroundUntil = new HashMap<>();
        for (PlacementModel p : allPlacements) {
            if (p.label() == null) continue;
            backgroundCorr.put(p.label(), initialCorrBalance(p));
            backgroundFrom.put(p.label(), p.monthlyFrom() != null && parseDate(p.monthlyFrom()) != null
                    ? YearMonth.from(parseDate(p.monthlyFrom())) : YearMonth.from(anchorDate));
            backgroundUntil.put(p.label(), p.monthlyUntil() != null && parseDate(p.monthlyUntil()) != null
                    ? YearMonth.from(parseDate(p.monthlyUntil())) : null);
        }

        int pauseLevelFromRefill = 0;
        int pauseLevel = 0;
        // Approximation de la tresorerie : ce niveau de detail (courbe d'un seul placement)
        // ne dispose pas des revenus/charges du foyer, seulement des mouvements de
        // placements ; le declencheur de refill se base donc uniquement sur les versements
        // et retraits de placements, ce qui reste fidele a l'esprit du mecanisme JS sans
        // reproduire l'integralite du moteur de tresorerie mensuel.
        BigDecimal treasuryBalance = settings.getEffectiveStartBalance();

        YearMonth cursor = YearMonth.from(anchorDate);
        YearMonth end = cursor.plusYears(horizonYears);
        int elapsedMonths = 0;
        while (cursor.isBefore(end)) {
            boolean withinContribWindow = !cursor.isBefore(monthlyFrom) && (monthlyUntil == null || !cursor.isAfter(monthlyUntil));
            boolean isPaused = placement.pausePriority() != null && placement.pausePriority() <= pauseLevel;
            BigDecimal effectiveContrib = withinContribWindow && !isPaused ? monthlyContrib : BigDecimal.ZERO;
            YearMonth cursorFinal = cursor;
            BigDecimal withdrawn = transfers.stream()
                    .filter(t -> placement.label() != null && placement.label().equalsIgnoreCase(t.placement())
                            && t.date() != null && parseDate(t.date()) != null
                            && YearMonth.from(parseDate(t.date())).equals(cursorFinal))
                    .map(TransferModel::getEffectiveAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            runningPess = applyMonth(runningPess, placement.getEffectiveRatePess(), effectiveContrib, true, withdrawn);
            runningCorr = applyMonth(runningCorr, placement.getEffectiveRateCorr(), effectiveContrib, true, withdrawn);
            runningOpti = applyMonth(runningOpti, placement.getEffectiveRateOpti(), effectiveContrib, true, withdrawn);

            // Avance tous les placements en arriere-plan (scenario correle) et reevalue le
            // pauseLevel pour le mois suivant, exactement comme le fait le moteur JS en fin
            // de mois : la decision prise ici s'applique au(x) prochain(s) mois, pas au mois
            // courant (deja traite ci-dessus).
            BigDecimal totalContribThisMonth = BigDecimal.ZERO;
            BigDecimal totalWithdrawnThisMonth = BigDecimal.ZERO;
            for (PlacementModel p : allPlacements) {
                if (p.label() == null) continue;
                BigDecimal cur = backgroundCorr.get(p.label());
                YearMonth pFrom = backgroundFrom.get(p.label());
                YearMonth pUntil = backgroundUntil.get(p.label());
                boolean pWithin = !cursor.isBefore(pFrom) && (pUntil == null || !cursor.isAfter(pUntil));
                boolean pPaused = p.pausePriority() != null && p.pausePriority() <= pauseLevel;
                BigDecimal pContrib = pWithin && !pPaused ? p.getEffectiveMonthly() : BigDecimal.ZERO;
                final String pLabel = p.label();
                BigDecimal pWithdrawn = transfers.stream()
                        .filter(t -> pLabel.equalsIgnoreCase(t.placement())
                                && t.date() != null && parseDate(t.date()) != null
                                && YearMonth.from(parseDate(t.date())).equals(cursorFinal))
                        .map(TransferModel::getEffectiveAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                backgroundCorr.put(pLabel, applyMonth(cur, p.getEffectiveRateCorr(), pContrib, true, pWithdrawn));
                totalContribThisMonth = totalContribThisMonth.add(pContrib);
                totalWithdrawnThisMonth = totalWithdrawnThisMonth.add(pWithdrawn);
            }

            treasuryBalance = treasuryBalance.subtract(totalContribThisMonth).add(totalWithdrawnThisMonth);
            boolean refillNeeded = sweepEnabled && hasSweepAccounts && treasuryBalance.compareTo(cashFloor) < 0;
            if (refillNeeded) {
                pauseLevelFromRefill = Math.min(maxPauseLevel, pauseLevelFromRefill + 1);
            } else if (cashCeiling != null && treasuryBalance.compareTo(cashCeiling) >= 0) {
                pauseLevelFromRefill = Math.max(0, pauseLevelFromRefill - 1);
            }
            int alertCount = 0;
            for (PlacementModel bp : bufferWatch) {
                BigDecimal bal = backgroundCorr.get(bp.label());
                if (bal != null && bal.compareTo(bp.pauseTriggerBalance()) < 0) alertCount++;
            }
            int pauseLevelFromAlerts = Math.min(maxPauseLevel, alertCount);
            pauseLevel = Math.max(pauseLevelFromRefill, pauseLevelFromAlerts);

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

    /**
     * Solde de depart utilise pour simuler en arriere-plan le scenario "correle" d'un
     * placement (mecanisme de pause) : dernier point d'historique connu si saisi, sinon le
     * solde/date de reference du placement.
     */
    private BigDecimal initialCorrBalance(PlacementModel p) {
        List<PlacementHistoryEntryModel> history = new ArrayList<>(p.getEffectiveHistory());
        history.removeIf(h -> h.date() == null || parseDate(h.date()) == null);
        if (!history.isEmpty()) {
            history.sort(Comparator.comparing(h -> parseDate(h.date())));
            return history.get(history.size() - 1).getEffectiveValue();
        }
        return p.getEffectiveBalance();
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
        SettingsModel settings = data.getEffectiveSettings();

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
        List<PlacementModel> placements = data.getEffectivePlacements();
        int n = placements.size();

        // --- Mecanisme de pause automatique des versements (portage de
        // view/js/calculations.js L.420-427, 509-511, 589-599). Pour pouvoir suspendre les
        // versements d'une annee en fonction de l'etat de fin d'annee precedente, les boucles
        // sont inversees par rapport a l'implementation naive : annee en dehors, placement en
        // dedans, au lieu de placement en dehors, annee en dedans.
        List<Integer> bufferWatchIdx = new ArrayList<>();
        boolean hasSweepAccounts = false;
        int maxPauseLevel = 0;
        for (int i = 0; i < n; i++) {
            PlacementModel p = placements.get(i);
            if (p.pausePriority() != null) maxPauseLevel = Math.max(maxPauseLevel, p.pausePriority());
            if (p.pauseTriggerBalance() != null) bufferWatchIdx.add(i);
            if (p.sweepPriority() != null) hasSweepAccounts = true;
        }
        boolean sweepEnabled = Boolean.TRUE.equals(settings.sweepEnabled());
        BigDecimal cashFloor = settings.cashFloor() != null ? settings.cashFloor() : BigDecimal.ZERO;
        BigDecimal cashCeiling = settings.cashCeiling();
        int pauseLevelFromRefill = 0;
        int pauseLevel = 0;
        // Tresorerie annuelle approximee : cette classe n'a pas acces au moteur complet de
        // tresorerie (revenus/charges/impots, cf. OverviewServiceImpl) ; on reconstitue donc
        // un cashflow net simplifie (revenus - charges - depenses ponctuelles - versements
        // places + retraits de placements, hors impots) uniquement pour decider quand
        // suspendre les versements, conformement a la decision de conception d'adapter le
        // mecanisme de refill au pas annuel.
        BigDecimal treasuryBalance = settings.getEffectiveStartBalance();

        BigDecimal[] pessArr = new BigDecimal[n];
        BigDecimal[] corrArr = new BigDecimal[n];
        BigDecimal[] optiArr = new BigDecimal[n];
        Integer[] monthlyFromYearArr = new Integer[n];
        Integer[] monthlyUntilYearArr = new Integer[n];
        List<List<PatrimoineYearModel>> rowsPerPlacement = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PlacementModel p = placements.get(i);
            pessArr[i] = p.getEffectiveBalance();
            corrArr[i] = p.getEffectiveBalance();
            optiArr[i] = p.getEffectiveBalance();
            Integer monthlyFromYear = yearOf(p.monthlyFrom());
            monthlyFromYearArr[i] = monthlyFromYear != null ? monthlyFromYear : (years.isEmpty() ? 2026 : years.get(0));
            monthlyUntilYearArr[i] = yearOf(p.monthlyUntil());
            rowsPerPlacement.add(new ArrayList<>());
        }

        for (int year : years) {
            BigDecimal totalContribThisYear = BigDecimal.ZERO;
            BigDecimal totalWithdrawThisYear = BigDecimal.ZERO;

            for (int i = 0; i < n; i++) {
                PlacementModel p = placements.get(i);
                BigDecimal withdraw = BigDecimal.ZERO;
                for (TransferModel t : data.getEffectiveTransfers()) {
                    if (p.label() != null && p.label().equalsIgnoreCase(t.placement()) && yearOf(t.date()) != null && yearOf(t.date()) == year) {
                        withdraw = withdraw.add(t.getEffectiveAmount());
                    }
                }

                boolean withinWindow = year >= monthlyFromYearArr[i] && (monthlyUntilYearArr[i] == null || year <= monthlyUntilYearArr[i]);
                boolean isPaused = p.pausePriority() != null && p.pausePriority() <= pauseLevel;
                BigDecimal monthlyContrib = withinWindow && !isPaused
                        ? p.getEffectiveMonthly().multiply(BigDecimal.valueOf(12))
                        : BigDecimal.ZERO;

                pessArr[i] = pessArr[i].multiply(BigDecimal.ONE.add(p.getEffectiveRatePess())).add(monthlyContrib).subtract(withdraw);
                corrArr[i] = corrArr[i].multiply(BigDecimal.ONE.add(p.getEffectiveRateCorr())).add(monthlyContrib).subtract(withdraw);
                optiArr[i] = optiArr[i].multiply(BigDecimal.ONE.add(p.getEffectiveRateOpti())).add(monthlyContrib).subtract(withdraw);

                rowsPerPlacement.get(i).add(new PatrimoineYearModel(year, pessArr[i], corrArr[i], optiArr[i]));

                totalContribThisYear = totalContribThisYear.add(monthlyContrib);
                totalWithdrawThisYear = totalWithdrawThisYear.add(withdraw);
            }

            // Reevalue le pauseLevel a partir de l'etat de fin d'annee : la decision prise
            // ici s'appliquera a l'annee SUIVANTE (meme decalage d'une periode que dans le
            // moteur JS, qui applique en debut de mois le pauseLevel decide fin du mois
            // precedent).
            BigDecimal annualIncome = BigDecimal.ZERO;
            for (IncomeModel inc : data.getEffectiveIncomes()) {
                annualIncome = annualIncome.add(incomeAnnualForYear(inc, year));
            }
            BigDecimal annualCharges = BigDecimal.ZERO;
            for (ChargeModel c : data.getEffectiveCharges()) {
                annualCharges = annualCharges.add(chargeAnnualForYear(c, year, inflationRate));
            }
            BigDecimal annualOneoff = BigDecimal.ZERO;
            for (OneOffExpenseModel o : data.getEffectiveOneoff()) {
                if (yearOf(o.date()) != null && yearOf(o.date()) == year) {
                    annualOneoff = annualOneoff.add(o.getEffectiveAmount());
                }
            }
            BigDecimal annualNet = annualIncome.subtract(annualCharges).subtract(annualOneoff)
                    .subtract(totalContribThisYear).add(totalWithdrawThisYear);
            treasuryBalance = treasuryBalance.add(annualNet);

            boolean refillNeeded = sweepEnabled && hasSweepAccounts && treasuryBalance.compareTo(cashFloor) < 0;
            if (refillNeeded) {
                pauseLevelFromRefill = Math.min(maxPauseLevel, pauseLevelFromRefill + 1);
            } else if (cashCeiling != null && treasuryBalance.compareTo(cashCeiling) >= 0) {
                pauseLevelFromRefill = Math.max(0, pauseLevelFromRefill - 1);
            }

            int alertCount = 0;
            for (int idx : bufferWatchIdx) {
                if (corrArr[idx].compareTo(placements.get(idx).pauseTriggerBalance()) < 0) alertCount++;
            }
            int pauseLevelFromAlerts = Math.min(maxPauseLevel, alertCount);
            pauseLevel = Math.max(pauseLevelFromRefill, pauseLevelFromAlerts);
        }

        List<PatrimoinePerPlacementModel> perPlacement = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            perPlacement.add(new PatrimoinePerPlacementModel(placements.get(i).label(), rowsPerPlacement.get(i)));
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

    private BigDecimal incomeAnnualForYear(IncomeModel row, int year) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + row.getEffectiveGrowthRate().doubleValue(), yearsElapsed);
        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));

        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private BigDecimal chargeAnnualForYear(ChargeModel row, int year, BigDecimal defaultInflation) {
        Integer startYear = yearOf(row.start());
        if (startYear == null) startYear = year;

        BigDecimal growth = row.getEffectiveGrowthRate(defaultInflation);
        int yearsElapsed = Math.max(0, year - startYear);
        double factor = Math.pow(1.0 + growth.doubleValue(), yearsElapsed);

        BigDecimal effectiveMonthly = row.getEffectiveMonthly().multiply(BigDecimal.valueOf(factor));
        int monthsActive = monthsActiveInYear(row.start(), row.end(), year);
        return effectiveMonthly.multiply(BigDecimal.valueOf(monthsActive));
    }

    private int monthsActiveInYear(String startISO, String endISO, int year) {
        LocalDate start = parseDate(startISO);
        LocalDate end = parseDate(endISO);
        if (start == null || end == null) return 0;

        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);

        LocalDate s = start.isAfter(yStart) ? start : yStart;
        LocalDate e = end.isBefore(yEnd) ? end : yEnd;

        if (e.isBefore(s)) return 0;
        return (e.getYear() - s.getYear()) * 12 + (e.getMonthValue() - s.getMonthValue()) + 1;
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
