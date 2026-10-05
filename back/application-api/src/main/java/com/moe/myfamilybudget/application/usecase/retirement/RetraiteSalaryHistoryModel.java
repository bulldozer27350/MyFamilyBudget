package com.moe.myfamilybudget.application.usecase.retirement;

import java.math.BigDecimal;

/** Salaire d'une année dans l'historique d'une personne. */
public record RetraiteSalaryHistoryModel(Integer year, BigDecimal salary) {}
