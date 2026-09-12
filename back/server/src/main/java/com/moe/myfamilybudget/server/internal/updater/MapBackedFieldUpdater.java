package com.moe.myfamilybudget.server.internal.updater;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Implémentation générique de {@link RecordFieldUpdater} : une simple table de correspondance
 * entre nom de champ et fonction de mise à jour. Chaque classe {@code *FieldUpdaters} (une par
 * modèle : Income, Charge, Placement...) construit une instance de cette classe au lieu de
 * dupliquer une chaîne de {@code "champ".equals(field) ? ... : ...}.
 *
 * @param <T> le type d'enregistrement concerné
 */
public final class MapBackedFieldUpdater<T> implements RecordFieldUpdater<T> {

    private final Map<String, BiFunction<T, Object, T>> fieldSetters;

    public MapBackedFieldUpdater(Map<String, BiFunction<T, Object, T>> fieldSetters) {
        // Copie défensive : la map fournie par l'appelant (souvent une HashMap locale à la
        // méthode de fabrique) ne doit plus pouvoir être modifiée après coup.
        this.fieldSetters = Map.copyOf(fieldSetters);
    }

    @Override
    public Optional<T> update(T record, String field, Object value) {
        BiFunction<T, Object, T> setter = fieldSetters.get(field);
        if (setter == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(setter.apply(record, value));
    }
}
