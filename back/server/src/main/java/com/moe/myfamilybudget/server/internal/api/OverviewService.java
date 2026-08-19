package com.moe.myfamilybudget.server.internal.api;

import com.moe.myfamilybudget.server.internal.dto.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;

public interface OverviewService {
    OverviewResponseDto buildOverview(BudgetDataModel data, boolean useConstantEuros);
}
