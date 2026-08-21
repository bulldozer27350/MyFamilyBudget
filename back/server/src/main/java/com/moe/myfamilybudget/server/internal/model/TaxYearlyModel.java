package com.moe.myfamilybudget.server.internal.model;

import java.math.BigDecimal;

/**
 * Modèle interne représentant le résultat de calcul d'imposition pour une année donnée.
 */
public record TaxYearlyModel(
    int year,
    double parts,
    BigDecimal taxableIncome,
    BigDecimal taxForecast,
    BigDecimal taxActual,
    BigDecimal ratePAS,
    BigDecimal withheld
) {}
