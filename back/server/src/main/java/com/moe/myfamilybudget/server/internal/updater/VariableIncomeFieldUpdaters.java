package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;

import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toBigDecimal;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toInteger;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrDefault;
import static com.moe.myfamilybudget.server.internal.updater.FieldValueConverter.toStringOrEmpty;

/**
 * Champs modifiables de VariableIncomeModel, avec la même sémantique exacte (y compris les
 * valeurs par défaut spécifiques : rate=0.05, startYear=2026, endYear=2049, type="prime") que
 * l'ancien bloc if/else de PersistenceManager.updateTresorerieRow pour "variableIncomes".
 */
public final class VariableIncomeFieldUpdaters {

    private VariableIncomeFieldUpdaters() {
    }

    public static RecordFieldUpdater<VariableIncomeModel> instance() {
        Map<String, BiFunction<VariableIncomeModel, Object, VariableIncomeModel>> setters = new HashMap<>();
        setters.put("label", (r, v) -> withLabel(r, toStringOrEmpty(v)));
        setters.put("refIncomeLabel", (r, v) -> withRefIncomeLabel(r, toStringOrEmpty(v)));
        setters.put("rate", (r, v) -> withRate(r, toBigDecimal(v, new BigDecimal("0.05"))));
        setters.put("startYear", (r, v) -> withStartYear(r, toInteger(v, 2026)));
        setters.put("endYear", (r, v) -> withEndYear(r, toInteger(v, 2049)));
        setters.put("taxable", (r, v) -> withTaxable(r, toStringOrEmpty(v)));
        setters.put("type", (r, v) -> withType(r, toStringOrDefault(v, "prime")));
        setters.put("notes", (r, v) -> withNotes(r, toStringOrEmpty(v)));
        return new MapBackedFieldUpdater<>(setters);
    }

    private static VariableIncomeModel withLabel(VariableIncomeModel r, String v) {
        return new VariableIncomeModel(r.id(), v, r.refIncomeLabel(), r.rate(), r.startYear(), r.endYear(), r.taxable(), r.type(), r.notes());
    }

    private static VariableIncomeModel withRefIncomeLabel(VariableIncomeModel r, String v) {
        return new VariableIncomeModel(r.id(), r.label(), v, r.rate(), r.startYear(), r.endYear(), r.taxable(), r.type(), r.notes());
    }

    private static VariableIncomeModel withRate(VariableIncomeModel r, BigDecimal v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), v, r.startYear(), r.endYear(), r.taxable(), r.type(), r.notes());
    }

    private static VariableIncomeModel withStartYear(VariableIncomeModel r, Integer v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), r.rate(), v, r.endYear(), r.taxable(), r.type(), r.notes());
    }

    private static VariableIncomeModel withEndYear(VariableIncomeModel r, Integer v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), r.rate(), r.startYear(), v, r.taxable(), r.type(), r.notes());
    }

    private static VariableIncomeModel withTaxable(VariableIncomeModel r, String v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), r.rate(), r.startYear(), r.endYear(), v, r.type(), r.notes());
    }

    private static VariableIncomeModel withType(VariableIncomeModel r, String v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), r.rate(), r.startYear(), r.endYear(), r.taxable(), v, r.notes());
    }

    private static VariableIncomeModel withNotes(VariableIncomeModel r, String v) {
        return new VariableIncomeModel(r.id(), r.label(), r.refIncomeLabel(), r.rate(), r.startYear(), r.endYear(), r.taxable(), r.type(), v);
    }
}
