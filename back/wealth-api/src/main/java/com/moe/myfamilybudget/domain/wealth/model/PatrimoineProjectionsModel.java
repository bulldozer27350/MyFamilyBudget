package com.moe.myfamilybudget.domain.wealth.model;

import java.util.List;

public record PatrimoineProjectionsModel(
    List<PatrimoinePerPlacementModel> perPlacement,
    List<PatrimoineYearModel> totals
) {}
