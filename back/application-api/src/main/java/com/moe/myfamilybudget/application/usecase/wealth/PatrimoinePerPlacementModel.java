package com.moe.myfamilybudget.application.usecase.wealth;

import java.util.List;

/** Projection patrimoniale d'un placement donné, année par année. */
public record PatrimoinePerPlacementModel(
    String label,
    List<PatrimoineYearModel> rows
) {}
