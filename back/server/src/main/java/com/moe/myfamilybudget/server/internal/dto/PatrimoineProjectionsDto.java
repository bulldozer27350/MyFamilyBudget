package com.moe.myfamilybudget.server.internal.dto;

import java.util.List;

public record PatrimoineProjectionsDto(
    List<PatrimoinePerPlacementDto> perPlacement,
    List<PatrimoineYearDto> totals
) {}
