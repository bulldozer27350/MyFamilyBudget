package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Une valeur reelle constatee pour un placement/compte a une date donnee (releve mensuel,
 * annuel, ou toute autre frequence choisie par l'utilisateur). Alimente la courbe "reelle" de
 * la fenetre dediee "Historique" du Patrimoine ; le point le plus recent sert d'ancrage aux 3
 * projections (pessimiste/correcte/optimiste) calculees par PatrimoineServiceImpl.
 */
public record PlacementHistoryEntryModel(
    String id,
    String date,
    BigDecimal value,
    String notes
) {
    public BigDecimal getEffectiveValue() {
        return value != null ? value : BigDecimal.ZERO;
    }
}
