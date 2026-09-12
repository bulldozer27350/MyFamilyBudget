package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de OneOffExpenseModel, avec la même sémantique exacte que l'ancien bloc
 * if/else de PersistenceManager.updateTresorerieRow pour "oneoff".
 */
public final class OneOffFieldUpdaters {

    private OneOffFieldUpdaters() {
    }

    public static RecordFieldUpdater<OneOffExpenseModel> instance() {
        Map<String, BiFunction<OneOffExpenseModel, Object, OneOffExpenseModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("date", (r, v) -> withDate(r, toStringOrEmpty(v)));
        setters.put("amount", (r, v) -> withAmount(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static OneOffExpenseModel withLabel(OneOffExpenseModel r, String v) {
        return new OneOffExpenseModel(r.id(), v, r.date(), r.amount(), r.notes());
    }

    private static OneOffExpenseModel withDate(OneOffExpenseModel r, String v) {
        return new OneOffExpenseModel(r.id(), r.label(), v, r.amount(), r.notes());
    }

    private static OneOffExpenseModel withAmount(OneOffExpenseModel r, BigDecimal v) {
        return new OneOffExpenseModel(r.id(), r.label(), r.date(), v, r.notes());
    }

    private static OneOffExpenseModel withNotes(OneOffExpenseModel r, String v) {
        return new OneOffExpenseModel(r.id(), r.label(), r.date(), r.amount(), v);
    }
}
