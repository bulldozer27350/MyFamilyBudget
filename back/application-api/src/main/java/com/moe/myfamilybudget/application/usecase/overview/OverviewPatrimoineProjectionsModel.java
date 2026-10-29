package com.moe.myfamilybudget.application.usecase.overview;

import java.util.List;

/**
 * Projections patrimoniales complètes pour l'Overview (R-85) : détail par placement et totaux.
 */
public record OverviewPatrimoineProjectionsModel(
    List<OverviewPatrimoinePerPlacementModel> perPlacement,
    List<OverviewPatrimoineYearModel> totals
) {}
