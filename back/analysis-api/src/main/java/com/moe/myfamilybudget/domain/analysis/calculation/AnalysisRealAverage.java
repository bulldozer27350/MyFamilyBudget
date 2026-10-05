package com.moe.myfamilybudget.domain.analysis.calculation;

import java.math.BigDecimal;

/**
 * Moyennes réelles d'une ligne budgétaire calculées par Analyse (SILO-133) : remplace l'usage du type du
 * silo Budget.
 *
 * @param avg3m  moyenne sur les trois derniers mois
 * @param avg12m moyenne sur les douze derniers mois
 * @param months nombre de mois pris en compte
 */
public record AnalysisRealAverage(BigDecimal avg3m, BigDecimal avg12m, int months) {}
