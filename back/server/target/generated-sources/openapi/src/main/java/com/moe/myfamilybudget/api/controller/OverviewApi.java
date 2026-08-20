package com.moe.myfamilybudget.api.controller;

import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

public interface OverviewApi {

    @GetMapping("/overview")
    ResponseEntity<OverviewResponseDto> getOverview(
        @RequestParam(value = "useConstantEuros", required = false, defaultValue = "false") Boolean useConstantEuros
    );

    @PostMapping("/overview")
    ResponseEntity<OverviewResponseDto> buildOverview(
        @RequestBody BudgetDataDto parameters,
        @RequestParam(value = "useConstantEuros", required = false, defaultValue = "false") Boolean useConstantEuros
    );
}
