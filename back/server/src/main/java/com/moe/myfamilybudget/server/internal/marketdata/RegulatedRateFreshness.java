package com.moe.myfamilybudget.server.internal.marketdata;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Évalue si une donnée de taux réglementé peut encore être la valeur en vigueur.
 *
 * Les taux du Livret A, du LDDS et du LEP ne changent que lors des révisions du 1er février et
 * du 1er août. Or les jeux de données publics sont publiés avec du retard (le dernier
 * enregistrement peut dater de plusieurs mois). Une donnée antérieure à la dernière révision
 * n'est donc pas forcément fausse (le taux a pu rester identique), mais elle doit être
 * signalée comme « possiblement obsolète » plutôt que suggérée sans réserve.
 */
public final class RegulatedRateFreshness {

    public enum Status {
        /** La donnée date de la dernière révision ou d'une période postérieure. */
        FRESH,
        /** La donnée est antérieure à la dernière révision : à vérifier. */
        STALE,
        /** Aucune donnée disponible. */
        UNAVAILABLE
    }

    private RegulatedRateFreshness() {
    }

    /** Date de la dernière révision (1er février ou 1er août) à la date donnée incluse. */
    public static LocalDate lastRevisionDate(LocalDate today) {
        LocalDate august = LocalDate.of(today.getYear(), 8, 1);
        LocalDate february = LocalDate.of(today.getYear(), 2, 1);
        if (!today.isBefore(august)) {
            return august;
        }
        if (!today.isBefore(february)) {
            return february;
        }
        return LocalDate.of(today.getYear() - 1, 8, 1);
    }

    /** Prochaine révision (1er février ou 1er août) strictement postérieure à la date donnée. */
    public static LocalDate nextRevisionDate(LocalDate today) {
        LocalDate last = lastRevisionDate(today);
        return last.getMonthValue() == 8
                ? LocalDate.of(last.getYear() + 1, 2, 1)
                : LocalDate.of(last.getYear(), 8, 1);
    }

    public static Status evaluate(YearMonth asOf, LocalDate today) {
        if (asOf == null) {
            return Status.UNAVAILABLE;
        }
        YearMonth revisionMonth = YearMonth.from(lastRevisionDate(today));
        return asOf.isBefore(revisionMonth) ? Status.STALE : Status.FRESH;
    }
}
