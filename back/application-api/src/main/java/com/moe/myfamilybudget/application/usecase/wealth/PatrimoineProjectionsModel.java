package com.moe.myfamilybudget.application.usecase.wealth;

import java.util.List;

/** Projections patrimoniales complètes : détail par placement et totaux. */
public record PatrimoineProjectionsModel(
    List<PatrimoinePerPlacementModel> perPlacement,
    List<PatrimoineYearModel> totals
) {}
