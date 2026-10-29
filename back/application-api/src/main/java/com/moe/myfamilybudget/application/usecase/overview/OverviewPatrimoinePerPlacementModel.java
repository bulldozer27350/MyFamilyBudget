package com.moe.myfamilybudget.application.usecase.overview;

import java.util.List;

/**
 * Projection patrimoniale par placement pour l'Overview (R-85).
 */
public record OverviewPatrimoinePerPlacementModel(
    String label,
    List<OverviewPatrimoineYearModel> rows
) {}
