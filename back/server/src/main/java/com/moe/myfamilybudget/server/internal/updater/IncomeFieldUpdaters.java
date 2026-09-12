package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.IncomeModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de IncomeModel, un par un, avec la même sémantique exacte que l'ancien
 * bloc if/else de PersistenceManager.updateTresorerieRow pour "incomes".
 */
public final class IncomeFieldUpdaters {

    private IncomeFieldUpdaters() {
    }

    public static RecordFieldUpdater<IncomeModel> instance() {
        Map<String, BiFunction<IncomeModel, Object, IncomeModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("monthly", (r, v) -> withMonthly(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("start", (r, v) -> withStart(r, toStringOrEmpty(v)));
        setters.put("end", (r, v) -> withEnd(r, toStringOrEmpty(v)));
        setters.put("growthRate", (r, v) -> withGrowthRate(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("categoryId", (r, v) -> withCategoryId(r, toStringOrEmpty(v)));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static IncomeModel withLabel(IncomeModel r, String v) {
        return new IncomeModel(r.id(), v, r.monthly(), r.start(), r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static IncomeModel withMonthly(IncomeModel r, BigDecimal v) {
        return new IncomeModel(r.id(), r.label(), v, r.start(), r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static IncomeModel withStart(IncomeModel r, String v) {
        return new IncomeModel(r.id(), r.label(), r.monthly(), v, r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static IncomeModel withEnd(IncomeModel r, String v) {
        return new IncomeModel(r.id(), r.label(), r.monthly(), r.start(), v, r.growthRate(), r.categoryId(), r.notes());
    }

    private static IncomeModel withGrowthRate(IncomeModel r, BigDecimal v) {
        return new IncomeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), v, r.categoryId(), r.notes());
    }

    private static IncomeModel withCategoryId(IncomeModel r, String v) {
        return new IncomeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), r.growthRate(), v, r.notes());
    }

    private static IncomeModel withNotes(IncomeModel r, String v) {
        return new IncomeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), r.growthRate(), r.categoryId(), v);
    }
}
