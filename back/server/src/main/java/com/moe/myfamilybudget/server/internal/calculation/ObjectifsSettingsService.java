package com.moe.myfamilybudget.server.internal.calculation;

import org.springframework.stereotype.Service;

/**
 * Paramètres du domaine Objectifs modifiables depuis l'application (RF-700). Sans enregistrement,
 * {@link ObjectifsParameters#defaults()} s'applique. La façade REST {@code /settings} reste unique
 * (voir doc/architecture/12-settings.md) : elle route les champs {@code goalSecureHorizonMonths}
 * et {@code goalLiquidHorizonMonths} vers ce service.
 */
@Service
public class ObjectifsSettingsService {

    public static final String FIELD_SECURE_HORIZON = "goalSecureHorizonMonths";
    public static final String FIELD_LIQUID_HORIZON = "goalLiquidHorizonMonths";

    private final ObjectifsSettingsStore store;

    public ObjectifsSettingsService(ObjectifsSettingsStore store) {
        this.store = store;
    }

    /** Paramètres en vigueur : enregistrés s'ils existent, sinon ceux par défaut. */
    public ObjectifsParameters current() {
        return store.load().orElseGet(ObjectifsParameters::defaults);
    }

    public ObjectifsParameters save(ObjectifsParameters parameters) {
        ObjectifsParameters toSave = parameters != null ? parameters : ObjectifsParameters.defaults();
        store.save(toSave);
        return toSave;
    }

    /** Indique si {@code field} est un paramètre du domaine Objectifs. */
    public static boolean owns(String field) {
        return FIELD_SECURE_HORIZON.equals(field) || FIELD_LIQUID_HORIZON.equals(field);
    }

    /**
     * Met à jour un seul paramètre. {@code value} peut être {@code null}, un nombre ou une chaîne
     * numérique ; toute valeur illisible est traitée comme « non renseignée » (comportement
     * historique de la mise à jour des settings).
     */
    public ObjectifsParameters updateField(String field, Object value) {
        ObjectifsParameters current = current();
        Integer parsed = toInteger(value);
        if (FIELD_SECURE_HORIZON.equals(field)) {
            return save(current.withSecureHorizonMonths(parsed));
        }
        if (FIELD_LIQUID_HORIZON.equals(field)) {
            return save(current.withLiquidHorizonMonths(parsed));
        }
        return current;
    }

    /** Retour aux valeurs par défaut (réinitialisation des données). */
    public void reset() {
        store.clear();
    }

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
