package com.moe.myfamilybudget.server.internal.mapper;

import com.moe.myfamilybudget.api.model.AnalyseCategorySummaryDto;
import com.moe.myfamilybudget.api.model.AnalyseDriftRowDto;
import com.moe.myfamilybudget.api.model.AnalyseKpiDto;
import com.moe.myfamilybudget.api.model.AnalyseLandingRowDto;
import com.moe.myfamilybudget.api.model.AnalyseMonthlyCompareDto;
import com.moe.myfamilybudget.api.model.AnalyseResponseDto;
import com.moe.myfamilybudget.server.internal.model.AnalyseCategorySummaryModel;
import com.moe.myfamilybudget.server.internal.model.AnalyseDriftRowModel;
import com.moe.myfamilybudget.server.internal.model.AnalyseKpiModel;
import com.moe.myfamilybudget.server.internal.model.AnalyseLandingRowModel;
import com.moe.myfamilybudget.server.internal.model.AnalyseMonthlyCompareModel;
import com.moe.myfamilybudget.server.internal.model.AnalyseResultModel;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper assurant la conversion entre le Modèle Interne d'Analyse et les DTOs OpenAPI.
 */
@Component
public class AnalyseMapper {

    public AnalyseResponseDto toDto(AnalyseResultModel model) {
        if (model == null) {
            return new AnalyseResponseDto();
        }

        AnalyseResponseDto dto = new AnalyseResponseDto();
        dto.setKpis(toKpiDto(model.kpis()));
        dto.setLandingData(model.landingData().stream().map(this::toLandingRowDto).collect(Collectors.toList()));
        dto.setDriftRows(model.driftRows().stream().map(this::toDriftRowDto).collect(Collectors.toList()));
        dto.setMonthlyCompareData(model.monthlyCompareData().stream().map(this::toMonthlyCompareDto).collect(Collectors.toList()));
        dto.setCategorySummaries(model.categorySummaries().stream().map(this::toCategorySummaryDto).collect(Collectors.toList()));
        dto.setCurrentMonthISO(model.currentMonthISO());
        dto.setCurrentMonthLabel(model.currentMonthLabel());
        return dto;
    }

    private AnalyseKpiDto toKpiDto(AnalyseKpiModel model) {
        if (model == null) return null;
        AnalyseKpiDto dto = new AnalyseKpiDto();
        dto.setTotalExpenses(model.totalExpenses());
        dto.setTotalIncome(model.totalIncome());
        dto.setNbMonths(model.nbMonths());
        dto.setUncategorizedCount(model.uncategorizedCount());
        dto.setCompressibleTotal(model.compressibleTotal());
        return dto;
    }

    private AnalyseLandingRowDto toLandingRowDto(AnalyseLandingRowModel model) {
        if (model == null) return null;
        AnalyseLandingRowDto dto = new AnalyseLandingRowDto();
        dto.setId(model.id());
        dto.setLabel(model.label());
        dto.setKind(AnalyseLandingRowDto.KindEnum.fromValue(model.kind()));
        dto.setBudgeted(model.budgeted());
        dto.setReel(model.reel());
        dto.setPct(model.pct());
        dto.setStatus(AnalyseLandingRowDto.StatusEnum.fromValue(model.status()));
        return dto;
    }

    private AnalyseDriftRowDto toDriftRowDto(AnalyseDriftRowModel model) {
        if (model == null) return null;
        AnalyseDriftRowDto dto = new AnalyseDriftRowDto();
        dto.setId(model.id());
        dto.setLabel(model.label());
        dto.setKind(AnalyseDriftRowDto.KindEnum.fromValue(model.kind()));
        dto.setBudgeted(model.budgeted());
        dto.setAvg3m(model.avg3m());
        dto.setAvg12m(model.avg12m());
        dto.setEcart(model.ecart());
        dto.setEcartPct(model.ecartPct());
        dto.setStatus(AnalyseDriftRowDto.StatusEnum.fromValue(model.status()));
        dto.setMonths(model.months());
        return dto;
    }

    private AnalyseMonthlyCompareDto toMonthlyCompareDto(AnalyseMonthlyCompareModel model) {
        if (model == null) return null;
        AnalyseMonthlyCompareDto dto = new AnalyseMonthlyCompareDto();
        dto.setMonthISO(model.monthISO());
        dto.setLabel(model.label());
        dto.setBudgeted(model.budgeted());
        dto.setReel(model.reel());
        dto.setHasPointing(model.hasPointing());
        return dto;
    }

    private AnalyseCategorySummaryDto toCategorySummaryDto(AnalyseCategorySummaryModel model) {
        if (model == null) return null;
        AnalyseCategorySummaryDto dto = new AnalyseCategorySummaryDto();
        dto.setLabel(model.label());
        dto.setAmount(model.amount());
        dto.setColor(model.color());
        return dto;
    }
}
