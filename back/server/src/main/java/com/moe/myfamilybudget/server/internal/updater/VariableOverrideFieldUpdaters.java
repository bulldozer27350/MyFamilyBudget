package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toInteger;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de VariableOverrideModel, avec la même sémantique exacte (année par défaut
 * = année courante) que l'ancien bloc if/else de PersistenceManager.updateTresorerieRow pour
 * "variableOverrides".
 */
public final class VariableOverrideFieldUpdaters {

    private VariableOverrideFieldUpdaters() {
    }

    public static RecordFieldUpdater<VariableOverrideModel> instance() {
        Map<String, BiFunction<VariableOverrideModel, Object, VariableOverrideModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("year", (r, v) -> withYear(r, toInteger(v, LocalDate.now().getYear())));
        setters.put("amount", (r, v) -> withAmount(r, toBigDecimal(v, BigDecimal.ZERO)));
        setters.put("taxable", (r, v) -> withTaxable(r, toStringOrEmpty(v)));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static VariableOverrideModel withLabel(VariableOverrideModel r, String v) {
        return new VariableOverrideModel(r.id(), v, r.year(), r.amount(), r.taxable(), r.notes());
    }

    private static VariableOverrideModel withYear(VariableOverrideModel r, Integer v) {
        return new VariableOverrideModel(r.id(), r.label(), v, r.amount(), r.taxable(), r.notes());
    }

    private static VariableOverrideModel withAmount(VariableOverrideModel r, BigDecimal v) {
        return new VariableOverrideModel(r.id(), r.label(), r.year(), v, r.taxable(), r.notes());
    }

    private static VariableOverrideModel withTaxable(VariableOverrideModel r, String v) {
        return new VariableOverrideModel(r.id(), r.label(), r.year(), r.amount(), v, r.notes());
    }

    private static VariableOverrideModel withNotes(VariableOverrideModel r, String v) {
        return new VariableOverrideModel(r.id(), r.label(), r.year(), r.amount(), r.taxable(), v);
    }
}
