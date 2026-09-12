package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.ChargeModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de ChargeModel, avec la même sémantique exacte que l'ancien bloc if/else de
 * PersistenceManager.updateTresorerieRow pour "charges".
 */
public final class ChargeFieldUpdaters {

    private ChargeFieldUpdaters() {
    }

    public static RecordFieldUpdater<ChargeModel> instance() {
        Map<String, BiFunction<ChargeModel, Object, ChargeModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("monthly", (r, v) -> withMonthly(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("start", (r, v) -> withStart(r, toStringOrEmpty(v)));
        setters.put("end", (r, v) -> withEnd(r, toStringOrEmpty(v)));
        setters.put("growthRate", (r, v) -> withGrowthRate(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("categoryId", (r, v) -> withCategoryId(r, toStringOrEmpty(v)));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static ChargeModel withLabel(ChargeModel r, String v) {
        return new ChargeModel(r.id(), v, r.monthly(), r.start(), r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static ChargeModel withMonthly(ChargeModel r, BigDecimal v) {
        return new ChargeModel(r.id(), r.label(), v, r.start(), r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static ChargeModel withStart(ChargeModel r, String v) {
        return new ChargeModel(r.id(), r.label(), r.monthly(), v, r.end(), r.growthRate(), r.categoryId(), r.notes());
    }

    private static ChargeModel withEnd(ChargeModel r, String v) {
        return new ChargeModel(r.id(), r.label(), r.monthly(), r.start(), v, r.growthRate(), r.categoryId(), r.notes());
    }

    private static ChargeModel withGrowthRate(ChargeModel r, BigDecimal v) {
        return new ChargeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), v, r.categoryId(), r.notes());
    }

    private static ChargeModel withCategoryId(ChargeModel r, String v) {
        return new ChargeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), r.growthRate(), v, r.notes());
    }

    private static ChargeModel withNotes(ChargeModel r, String v) {
        return new ChargeModel(r.id(), r.label(), r.monthly(), r.start(), r.end(), r.growthRate(), r.categoryId(), v);
    }
}
