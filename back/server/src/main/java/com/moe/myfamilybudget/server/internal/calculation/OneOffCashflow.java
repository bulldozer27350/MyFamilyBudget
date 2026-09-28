package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dépense ponctuelle projetée dans la trésorerie (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md).
 *
 * @param date   date de la dépense, {@code null} si non renseignée (sans effet)
 * @param amount montant ({@code null} vaut 0)
 */
public record OneOffCashflow(LocalDate date, BigDecimal amount) {
    public OneOffCashflow {
        amount = amount != null ? amount : BigDecimal.ZERO;
    }
}
