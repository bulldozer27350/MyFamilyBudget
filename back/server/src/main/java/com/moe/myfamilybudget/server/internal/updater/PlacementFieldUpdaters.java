package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.PlacementModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBoolean;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toInteger;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de PlacementModel.
 *
 * IMPORTANT — bug corrigé au passage : l'ancien bloc if/else de
 * PersistenceManager.updateTresorerieRow appelait le constructeur de compatibilité à 18
 * arguments de PlacementModel (celui sans le paramètre `history`), qui fixe silencieusement
 * l'historique de valorisation à List.of(). Résultat : modifier n'importe quel champ d'un
 * placement via la grille de trésorerie effaçait son historique (PlacementHistoryEntryModel)
 * au lieu de le préserver. Les with*() ci-dessous utilisent le constructeur complet à 19
 * arguments et transmettent explicitement r.history() pour ne plus jamais le perdre.
 */
public final class PlacementFieldUpdaters {

    private PlacementFieldUpdaters() {
    }

    public static RecordFieldUpdater<PlacementModel> instance() {
        Map<String, BiFunction<PlacementModel, Object, PlacementModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("category", (r, v) -> withCategory(r, toStringOrEmpty(v)));
        setters.put("balance", (r, v) -> withBalance(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("balanceDate", (r, v) -> withBalanceDate(r, toStringOrEmpty(v)));
        setters.put("monthly", (r, v) -> withMonthly(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("monthlyFrom", (r, v) -> withMonthlyFrom(r, toStringOrEmpty(v)));
        setters.put("monthlyUntil", (r, v) -> withMonthlyUntil(r, toStringOrEmpty(v)));
        setters.put("ratePess", (r, v) -> withRatePess(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("rateCorr", (r, v) -> withRateCorr(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("rateOpti", (r, v) -> withRateOpti(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("excludedFromRetirement", (r, v) -> withExcludedFromRetirement(r, toBoolean(v, false)));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        setters.put("sweepPriority", (r, v) -> withSweepPriority(r, toInteger(v, null)));
        setters.put("sweepCap", (r, v) -> withSweepCap(r, toBigDecimal(v, null)));
        setters.put("pauseTriggerBalance", (r, v) -> withPauseTriggerBalance(r, toBigDecimal(v, null)));
        setters.put("pausePriority", (r, v) -> withPausePriority(r, toInteger(v, null)));
        setters.put("categoryId", (r, v) -> withCategoryId(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static PlacementModel withLabel(PlacementModel r, String v) {
        return new PlacementModel(r.id(), v, r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withCategory(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), v, r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withBalance(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), v, r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withBalanceDate(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), v, r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withMonthly(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), v,
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withMonthlyFrom(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                v, r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withMonthlyUntil(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), v, r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withRatePess(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), v, r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withRateCorr(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), v, r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withRateOpti(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), v,
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withExcludedFromRetirement(PlacementModel r, Boolean v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                v, r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withNotes(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), v, r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withSweepPriority(PlacementModel r, Integer v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), v, r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withSweepCap(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), v,
                r.pauseTriggerBalance(), r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withPauseTriggerBalance(PlacementModel r, BigDecimal v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                v, r.pausePriority(), r.categoryId(), r.history());
    }

    private static PlacementModel withPausePriority(PlacementModel r, Integer v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), v, r.categoryId(), r.history());
    }

    private static PlacementModel withCategoryId(PlacementModel r, String v) {
        return new PlacementModel(r.id(), r.label(), r.category(), r.balance(), r.balanceDate(), r.monthly(),
                r.monthlyFrom(), r.monthlyUntil(), r.ratePess(), r.rateCorr(), r.rateOpti(),
                r.excludedFromRetirement(), r.notes(), r.sweepPriority(), r.sweepCap(),
                r.pauseTriggerBalance(), r.pausePriority(), v, r.history());
    }
}
