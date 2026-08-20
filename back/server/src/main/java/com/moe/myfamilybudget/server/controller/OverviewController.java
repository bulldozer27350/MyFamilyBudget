package com.moe.myfamilybudget.server.controller;

import com.moe.myfamilybudget.api.controller.OverviewApi;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.server.internal.impl.OverviewServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewController implements OverviewApi {

    private final OverviewServiceImpl overviewService;

    public OverviewController(OverviewServiceImpl overviewService) {
        this.overviewService = overviewService;
    }

    @Override
    public ResponseEntity<OverviewResponseDto> getOverview(Boolean useConstantEuros) {
        OverviewResponseDto dto = overviewService.buildOverview(new BudgetDataDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null), Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<OverviewResponseDto> buildOverview(BudgetDataDto parameters, Boolean useConstantEuros) {
        OverviewResponseDto dto = overviewService.buildOverview(parameters, Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(dto);
    }
}
