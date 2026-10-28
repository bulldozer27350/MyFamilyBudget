package com.moe.myfamilybudget.application.usecase.tax;

import java.math.BigDecimal;

/** Résultat de simulation fiscale pour une année donnée (bloc {@code taxPreview}). */
public record TaxYearlyModel(
    int year,
    double parts,
    BigDecimal taxableIncome,
    BigDecimal taxForecast,
    BigDecimal taxActual,
    BigDecimal ratePAS,
    BigDecimal withheld
) {}
