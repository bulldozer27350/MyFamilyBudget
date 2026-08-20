package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record PatrimoineProjectionsModel(
    List<PatrimoinePerPlacementModel> perPlacement,
    List<PatrimoineYearModel> totals
) {}
