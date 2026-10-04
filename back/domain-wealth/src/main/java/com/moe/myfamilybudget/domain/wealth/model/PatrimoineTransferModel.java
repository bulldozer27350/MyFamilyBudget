package com.moe.myfamilybudget.domain.wealth.model;

import java.math.BigDecimal;

/**
 * Virement vers un placement, tel que le silo Patrimoine le lit et l'importe (SILO-131). Type propre au
 * silo : {@code domain-wealth} ne référence plus aucun type de {@code domain-budget} (décision D1).
 */
public record PatrimoineTransferModel(
    String id,
    String placement,
    String date,
    BigDecimal amount,
    String notes
) {
    public BigDecimal getEffectiveAmount() {
        return amount != null ? amount : BigDecimal.ZERO;
    }
}
