package com.moe.myfamilybudget.domain.wealth.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Retrait (transfert sortant) d'un placement (voir {@link PatrimoineProjectionInput}).
 *
 * @param placementLabel libellé du placement concerné, rapproché sans tenir compte de la casse
 *                       du libellé de {@link PlacementProjectionInput}
 * @param date           date du retrait
 * @param amount         montant retiré ({@code null} vaut 0)
 */
public record PlacementTransfer(String placementLabel, LocalDate date, BigDecimal amount) {
    public PlacementTransfer {
        amount = amount != null ? amount : BigDecimal.ZERO;
    }
}
