package com.moe.myfamilybudget.domain.treasury.model;

import java.util.List;

public record VariablePreviewModel(
    String label,
    List<VariablePreviewCellModel> cells
) {}
