package com.moe.myfamilybudget.server.internal.updater;

import java.math.BigDecimal;

/**
 * Conversions de valeurs brutes reçues de l'API (typiquement désérialisées en Object depuis du
 * JSON) vers les types utilisés dans les modèles internes. Logique identique à celle qui existait
 * déjà en méthodes privées dans PersistenceManager (toBigDecimal/toInteger) ; extraite ici pour
 * être réutilisable par les classes {@code *FieldUpdaters} sans dépendre d'une instance de
 * PersistenceManager.
 */
public final class FieldValueConverter {

    private FieldValueConverter() {
    }

    public static BigDecimal toBigDecimal(Object val, BigDecimal fallback) {
        if (val == null) return fallback;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(val).trim().replace(",", ".");
            if (s.isEmpty()) return fallback;
            return new BigDecimal(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Integer toInteger(Object val, Integer fallback) {
        if (val == null) return fallback;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(val).trim();
            if (s.isEmpty()) return fallback;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String toStringOrEmpty(Object val) {
        return val != null ? String.valueOf(val) : "";
    }

    public static String toStringOrDefault(Object val, String fallback) {
        return val != null ? String.valueOf(val) : fallback;
    }

    public static Boolean toBoolean(Object val, boolean fallback) {
        if (val == null) return fallback;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val));
    }
}
