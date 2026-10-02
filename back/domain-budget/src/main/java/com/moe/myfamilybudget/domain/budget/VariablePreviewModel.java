package com.moe.myfamilybudget.domain.budget;

import java.util.List;

public record VariablePreviewModel(
    String label,
    List<VariablePreviewCellModel> cells
) {}
