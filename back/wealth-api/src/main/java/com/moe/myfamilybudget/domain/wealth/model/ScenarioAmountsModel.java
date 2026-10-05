package com.moe.myfamilybudget.domain.wealth.model;

import java.math.BigDecimal;

/**
 * Montant décliné selon les trois scénarios de rendement (pessimiste, corrigé, optimiste), produit par
 * les projections patrimoniales (SILO-131). Type propre au silo Patrimoine.
 */
public record ScenarioAmountsModel(
    BigDecimal pess,
    BigDecimal corr,
    BigDecimal opti
) {}
