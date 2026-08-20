package com.moe.myfamilybudget.api.model;

import java.util.List;

public record PatrimoineProjectionsDto(
    List<PatrimoinePerPlacementDto> perPlacement,
    List<PatrimoineYearDto> totals
) {}
