package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegulatedRateFreshnessTest {

    @Test
    @DisplayName("lastRevisionDate() renvoie le 1er août ou le 1er février le plus récent, jour de révision inclus")
    void lastRevisionDate() {
        assertEquals(LocalDate.of(2026, 8, 1), RegulatedRateFreshness.lastRevisionDate(LocalDate.of(2026, 9, 19)));
        assertEquals(LocalDate.of(2026, 8, 1), RegulatedRateFreshness.lastRevisionDate(LocalDate.of(2026, 8, 1)));
        assertEquals(LocalDate.of(2026, 2, 1), RegulatedRateFreshness.lastRevisionDate(LocalDate.of(2026, 7, 31)));
        assertEquals(LocalDate.of(2026, 2, 1), RegulatedRateFreshness.lastRevisionDate(LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2025, 8, 1), RegulatedRateFreshness.lastRevisionDate(LocalDate.of(2026, 1, 31)));
    }

    @Test
    @DisplayName("nextRevisionDate() renvoie le prochain 1er février ou 1er août, strictement après la date")
    void nextRevisionDate() {
        assertEquals(LocalDate.of(2027, 2, 1), RegulatedRateFreshness.nextRevisionDate(LocalDate.of(2026, 9, 19)));
        assertEquals(LocalDate.of(2027, 2, 1), RegulatedRateFreshness.nextRevisionDate(LocalDate.of(2026, 8, 1)));
        assertEquals(LocalDate.of(2026, 8, 1), RegulatedRateFreshness.nextRevisionDate(LocalDate.of(2026, 7, 31)));
        assertEquals(LocalDate.of(2026, 8, 1), RegulatedRateFreshness.nextRevisionDate(LocalDate.of(2026, 2, 1)));
        assertEquals(LocalDate.of(2026, 2, 1), RegulatedRateFreshness.nextRevisionDate(LocalDate.of(2026, 1, 31)));
    }

    @Test
    @DisplayName("Une donnée de mars 2026 est périmée en septembre 2026 (révision du 1er août passée)")
    void staleWhenBeforeLastRevision() {
        assertEquals(RegulatedRateFreshness.Status.STALE,
                RegulatedRateFreshness.evaluate(YearMonth.of(2026, 3), LocalDate.of(2026, 9, 19)));
    }

    @Test
    @DisplayName("Une donnée du mois de la dernière révision ou postérieure est à jour")
    void freshWhenFromLastRevisionMonth() {
        assertEquals(RegulatedRateFreshness.Status.FRESH,
                RegulatedRateFreshness.evaluate(YearMonth.of(2026, 8), LocalDate.of(2026, 9, 19)));
        assertEquals(RegulatedRateFreshness.Status.FRESH,
                RegulatedRateFreshness.evaluate(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 19)));
    }

    @Test
    @DisplayName("Une donnée de mars 2026 est encore à jour le 31 juillet 2026, avant la révision d'août")
    void freshBeforeNextRevision() {
        assertEquals(RegulatedRateFreshness.Status.FRESH,
                RegulatedRateFreshness.evaluate(YearMonth.of(2026, 3), LocalDate.of(2026, 7, 31)));
    }

    @Test
    @DisplayName("Sans donnée, le statut est UNAVAILABLE")
    void unavailableWithoutData() {
        assertEquals(RegulatedRateFreshness.Status.UNAVAILABLE,
                RegulatedRateFreshness.evaluate(null, LocalDate.of(2026, 9, 19)));
    }
}
