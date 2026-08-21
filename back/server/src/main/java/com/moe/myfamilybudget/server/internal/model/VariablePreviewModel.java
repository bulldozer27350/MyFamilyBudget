package com.moe.myfamilybudget.server.internal.model;

import java.util.List;

public record VariablePreviewModel(
    String label,
    List<VariablePreviewCellModel> cells
) {}
