package com.moe.myfamilybudget.server.internal.calculation;

import com.moe.myfamilybudget.domain.wealth.calculation.PlacementTransfer;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Virement (vers ou depuis un placement) affectant la trésorerie (RF-400, voir
 * doc/architecture/06-domaine-tresorerie.md).
 *
 * <p>Contrairement à {@link PlacementTransfer} (domaine Patrimoine), la trésorerie n'a pas besoin
 * du placement concerné : elle ne fait que sommer tous les mouvements d'une même année.
 *
 * @param date   date du virement, {@code null} si non renseignée (sans effet)
 * @param amount montant ({@code null} vaut 0)
 */
public record TransferProjection(LocalDate date, BigDecimal amount) {
    public TransferProjection {
        amount = amount != null ? amount : BigDecimal.ZERO;
    }
}
