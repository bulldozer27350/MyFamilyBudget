package com.moe.myfamilybudget.api.model;

import java.util.List;

public record PatrimoinePerPlacementDto(
    String label,
    List<PatrimoineYearDto> rows
) {}
