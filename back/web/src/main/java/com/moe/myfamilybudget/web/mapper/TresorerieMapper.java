package com.moe.myfamilybudget.web.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.CashflowYearDto;
import com.moe.myfamilybudget.api.model.CategoryOptionDto;
import com.moe.myfamilybudget.api.model.ChargeDto;
import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.OneOffExpenseDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.api.model.TresorerieSuggestionDto;
import com.moe.myfamilybudget.api.model.VariableIncomeDto;
import com.moe.myfamilybudget.api.model.VariableOverrideDto;
import com.moe.myfamilybudget.api.model.VariablePreviewCellDto;
import com.moe.myfamilybudget.api.model.VariablePreviewDto;
import com.moe.myfamilybudget.application.usecase.treasury.CashflowYearModel;
import com.moe.myfamilybudget.application.usecase.treasury.CategoryOptionModel;
import com.moe.myfamilybudget.application.usecase.treasury.ChargeModel;
import com.moe.myfamilybudget.application.usecase.treasury.IncomeModel;
import com.moe.myfamilybudget.application.usecase.treasury.OneOffExpenseModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieResultModel;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieSuggestionModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariableIncomeModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariableOverrideModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewCellModel;
import com.moe.myfamilybudget.application.usecase.treasury.VariablePreviewModel;

/**
 * Mapper assurant la conversion entre le modèle applicatif ({@code application-api}, package
 * {@code application.usecase.treasury}) et les DTO OpenAPI (R-82, R-90, SILO-313, SILO-320).
 */
@Component
public class TresorerieMapper {

    public TresorerieResponseDto toTresorerieResponseDto(TresorerieResultModel m) {
        if (m == null) {
            return null;
        }
        TresorerieResponseDto dto = new TresorerieResponseDto();
        dto.setIncomes(m.incomes() != null ? m.incomes().stream().map(this::toIncomeDto).collect(Collectors.toList()) : List.of());
        dto.setCharges(m.charges() != null ? m.charges().stream().map(this::toChargeDto).collect(Collectors.toList()) : List.of());
        dto.setOneoff(m.oneoff() != null ? m.oneoff().stream().map(this::toOneOffExpenseDto).collect(Collectors.toList()) : List.of());
        dto.setVariableIncomes(m.variableIncomes() != null ? m.variableIncomes().stream().map(this::toVariableIncomeDto).collect(Collectors.toList()) : List.of());
        dto.setVariableOverrides(m.variableOverrides() != null ? m.variableOverrides().stream().map(this::toVariableOverrideDto).collect(Collectors.toList()) : List.of());
        dto.setIncomeLabels(m.incomeLabels() != null ? new ArrayList<>(m.incomeLabels()) : List.of());
        dto.setVariableIncomeLabels(m.variableIncomeLabels() != null ? new ArrayList<>(m.variableIncomeLabels()) : List.of());
        dto.setCategoryOptions(m.categoryOptions() != null ? m.categoryOptions().stream().map(this::toCategoryOptionDto).collect(Collectors.toList()) : List.of());
        dto.setSuggestions(m.suggestions() != null ? m.suggestions().stream().map(this::toTresorerieSuggestionDto).collect(Collectors.toList()) : List.of());
        dto.setRetireYear(m.retireYear());
        dto.setYears(m.years() != null ? new ArrayList<>(m.years()) : List.of());
        dto.setCashflow(m.cashflow() != null ? m.cashflow().stream().map(this::toCashflowYearDto).collect(Collectors.toList()) : List.of());
        dto.setVariablePreview(m.variablePreview() != null ? m.variablePreview().stream().map(this::toVariablePreviewDto).collect(Collectors.toList()) : List.of());
        dto.setPreviewYears(m.previewYears() != null ? new ArrayList<>(m.previewYears()) : List.of());
        return dto;
    }

    public CategoryOptionDto toCategoryOptionDto(CategoryOptionModel m) {
        if (m == null) return null;
        CategoryOptionDto dto = new CategoryOptionDto();
        dto.setValue(m.value());
        dto.setLabel(m.label());
        return dto;
    }

    public TresorerieSuggestionDto toTresorerieSuggestionDto(TresorerieSuggestionModel m) {
        if (m == null) return null;
        TresorerieSuggestionDto dto = new TresorerieSuggestionDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setKind(m.kind());
        dto.setBudgeted(m.budgeted());
        dto.setAvg3m(m.avg3m());
        dto.setAvg12m(m.avg12m());
        dto.setEcart(m.ecart());
        dto.setEcartPct(m.ecartPct());
        dto.setMonths(m.months());
        dto.setSuggested(m.suggested());
        return dto;
    }

    public VariablePreviewCellDto toVariablePreviewCellDto(VariablePreviewCellModel m) {
        if (m == null) return null;
        VariablePreviewCellDto dto = new VariablePreviewCellDto();
        dto.setYear(m.year());
        dto.setAmount(m.amount());
        dto.setIsReal(m.isReal());
        return dto;
    }

    public VariablePreviewDto toVariablePreviewDto(VariablePreviewModel m) {
        if (m == null) return null;
        List<VariablePreviewCellDto> cells = m.cells() != null ? m.cells().stream()
                .map(this::toVariablePreviewCellDto)
                .collect(Collectors.toList()) : List.of();
        VariablePreviewDto dto = new VariablePreviewDto();
        dto.setLabel(m.label());
        dto.setCells(cells);
        return dto;
    }

    public CashflowYearDto toCashflowYearDto(CashflowYearModel m) {
        if (m == null) return null;
        CashflowYearDto dto = new CashflowYearDto();
        dto.setYear(m.year());
        dto.setIncome(m.income());
        dto.setVariableIncome(m.variableIncome());
        dto.setSavings(m.savings());
        dto.setCharges(m.charges());
        dto.setOneoff(m.oneoff());
        dto.setTransfersY(m.transfersY());
        dto.setImpots(m.impots());
        dto.setRegularisation(m.regularisation());
        dto.setNet(m.net());
        dto.setBalance(m.balance());
        return dto;
    }

    public IncomeDto toIncomeDto(IncomeModel m) {
        if (m == null) return null;
        IncomeDto dto = new IncomeDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setMonthly(m.monthly());
        dto.setStart(m.start());
        dto.setEnd(m.end());
        dto.setGrowthRate(m.growthRate());
        dto.setCategoryId(m.categoryId());
        dto.setNotes(m.notes());
        return dto;
    }

    public ChargeDto toChargeDto(ChargeModel m) {
        if (m == null) return null;
        ChargeDto dto = new ChargeDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setMonthly(m.monthly());
        dto.setStart(m.start());
        dto.setEnd(m.end());
        dto.setGrowthRate(m.growthRate());
        dto.setCategoryId(m.categoryId());
        dto.setNotes(m.notes());
        return dto;
    }

    public OneOffExpenseDto toOneOffExpenseDto(OneOffExpenseModel m) {
        if (m == null) return null;
        OneOffExpenseDto dto = new OneOffExpenseDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setDate(m.date());
        dto.setAmount(m.amount());
        dto.setNotes(m.notes());
        return dto;
    }

    public VariableIncomeDto toVariableIncomeDto(VariableIncomeModel m) {
        if (m == null) return null;
        VariableIncomeDto dto = new VariableIncomeDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setRefIncomeLabel(m.refIncomeLabel());
        dto.setRate(m.rate());
        dto.setStartYear(m.startYear());
        dto.setEndYear(m.endYear());
        dto.setTaxable(m.taxable());
        return dto;
    }

    public VariableOverrideDto toVariableOverrideDto(VariableOverrideModel m) {
        if (m == null) return null;
        VariableOverrideDto dto = new VariableOverrideDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setYear(m.year());
        dto.setAmount(m.amount());
        dto.setTaxable(m.taxable());
        return dto;
    }
}
