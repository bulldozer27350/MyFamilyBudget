package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;
import java.util.List;

/**
 * Commande de sauvegarde de la retraite (SILO-310) : paramètres globaux et personnes. Aucune valeur n'est
 * nulle : l'appelant (couche web) applique les valeurs par défaut du contrat avant de construire la commande.
 */
public record RetraiteSaveCommand(
    List<RetraitePersonCommand> people,
    BigDecimal pass2026,
    BigDecimal passGrowthRate,
    BigDecimal agircPointValue,
    String agircPointDateGlobal,
    BigDecimal agircPointGrowthRate
) {}
