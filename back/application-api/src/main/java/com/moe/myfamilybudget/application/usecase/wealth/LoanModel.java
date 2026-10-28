package com.moe.myfamilybudget.application.usecase.wealth;

import java.math.BigDecimal;

/**
 * Un prêt, rappelé dans la réponse Patrimoine (lecture inter-silo, domaine Crédit).
 * Simple miroir de données : aucune écriture ne passe par ce cas d'usage (DB-041, voir
 * {@code PatrimoineServiceImpl}, qui route "loans"/"credits" vers le command service Crédit).
 */
public record LoanModel(
    String id,
    String label,
    BigDecimal crd,
    BigDecimal rate,
    BigDecimal monthly,
    BigDecimal insurance,
    String startDate,
    String endDate,
    BigDecimal initialAmount,
    Integer totalInstallments,
    String stepDate
) {}
