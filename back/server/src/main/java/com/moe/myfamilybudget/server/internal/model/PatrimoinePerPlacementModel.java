package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record PatrimoinePerPlacementModel(
    String label,
    List<PatrimoineYearModel> rows
) {}
