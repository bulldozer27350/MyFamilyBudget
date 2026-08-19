package com.moe.myfamilybudget.server.internal.dto;

import java.util.List;

public record PatrimoinePerPlacementDto(
    String label,
    List<PatrimoineYearDto> rows
) {}
