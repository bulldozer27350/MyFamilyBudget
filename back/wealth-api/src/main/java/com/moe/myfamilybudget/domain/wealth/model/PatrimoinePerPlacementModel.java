package com.moe.myfamilybudget.domain.wealth.model;

import java.util.List;

public record PatrimoinePerPlacementModel(
    String label,
    List<PatrimoineYearModel> rows
) {}
