package com.moe.myfamilybudget.server.internal.mapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.AssetCategoryDto;
import com.moe.myfamilybudget.api.model.BankImportCategoryDto;
import com.moe.myfamilybudget.api.model.LoanDto;
import com.moe.myfamilybudget.api.model.PatrimoinePerPlacementDto;
import com.moe.myfamilybudget.api.model.PatrimoineProjectionsDto;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PatrimoineYearDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.TransferDto;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;

@Component
public class PatrimoineMapper {

    public PlacementDto toPlacementDto(PlacementModel m) {
        if (m == null) return null;
        PlacementDto dto = new PlacementDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setCategory(m.category());
        dto.setBalance(m.balance());
        dto.setBalanceDate(m.balanceDate());
        dto.setMonthly(m.monthly());
        dto.setMonthlyFrom(m.monthlyFrom());
        dto.setMonthlyUntil(m.monthlyUntil());
        dto.setRatePess(m.ratePess());
        dto.setRateCorr(m.rateCorr());
        dto.setRateOpti(m.rateOpti());
        dto.setExcludedFromRetirement(m.excludedFromRetirement());
        dto.setNotes(m.notes());
        return dto;
    }

    public PlacementModel toPlacementModel(PlacementDto dto) {
        if (dto == null) return null;
        return new PlacementModel(
                dto.getId(),
                dto.getLabel(),
                dto.getCategory(),
                dto.getBalance(),
                dto.getBalanceDate(),
                dto.getMonthly(),
                dto.getMonthlyFrom(),
                dto.getMonthlyUntil(),
                dto.getRatePess(),
                dto.getRateCorr(),
                dto.getRateOpti(),
                dto.getExcludedFromRetirement(),
                dto.getNotes()
        );
    }

    public TransferDto toTransferDto(TransferModel m) {
        if (m == null) return null;
        TransferDto dto = new TransferDto();
        dto.setId(m.id());
        dto.setPlacement(m.placement());
        dto.setDate(m.date());
        dto.setAmount(m.amount());
        dto.setNotes(m.notes());
        return dto;
    }

    public TransferModel toTransferModel(TransferDto dto) {
        if (dto == null) return null;
        return new TransferModel(
                dto.getId(),
                dto.getPlacement(),
                dto.getDate(),
                dto.getAmount(),
                dto.getNotes()
        );
    }

    public LoanDto toLoanDto(LoanModel m) {
        if (m == null) return null;
        LoanDto dto = new LoanDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setCrd(m.crd());
        dto.setRate(m.rate());
        dto.setMonthly(m.monthly());
        dto.setInsurance(m.insurance());
        dto.setStartDate(m.startDate());
        dto.setEndDate(m.endDate());
        return dto;
    }

    public LoanModel toLoanModel(LoanDto dto) {
        if (dto == null) return null;
        return new LoanModel(
                dto.getId(),
                dto.getLabel(),
                dto.getCrd(),
                dto.getRate(),
                dto.getMonthly(),
                dto.getInsurance(),
                dto.getStartDate(),
                dto.getEndDate()
        );
    }

    public RealEstateDto toRealEstateDto(RealEstateModel m) {
        if (m == null) return null;
        RealEstateDto dto = new RealEstateDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setType(m.type());
        dto.setCurrentValue(m.currentValue());
        dto.setValuationYear(m.valuationYear());
        dto.setAnnualGrowthRate(m.annualGrowthRate());
        dto.setNotes(m.notes());
        return dto;
    }

    public RealEstateModel toRealEstateModel(RealEstateDto dto) {
        if (dto == null) return null;
        return new RealEstateModel(
                dto.getId(),
                dto.getLabel(),
                dto.getType(),
                dto.getCurrentValue(),
                dto.getValuationYear(),
                dto.getAnnualGrowthRate(),
                dto.getNotes()
        );
    }

    public PatrimoineYearDto toPatrimoineYearDto(PatrimoineYearModel m) {
        if (m == null) return null;
        PatrimoineYearDto dto = new PatrimoineYearDto();
        dto.setYear(m.year());
        dto.setPess(m.pess());
        dto.setCorr(m.corr());
        dto.setOpti(m.opti());
        return dto;
    }

    public PatrimoinePerPlacementDto toPatrimoinePerPlacementDto(PatrimoinePerPlacementModel m) {
        if (m == null) return null;
        List<PatrimoineYearDto> rows = m.rows() != null ? m.rows().stream()
                .map(this::toPatrimoineYearDto)
                .collect(Collectors.toList()) : Collections.emptyList();
        PatrimoinePerPlacementDto dto = new PatrimoinePerPlacementDto();
        dto.setLabel(m.label());
        dto.setRows(rows);
        return dto;
    }

    public PatrimoineProjectionsDto toPatrimoineProjectionsDto(PatrimoineProjectionsModel m) {
        if (m == null) return null;
        List<PatrimoinePerPlacementDto> perPlacement = m.perPlacement() != null ? m.perPlacement().stream()
                .map(this::toPatrimoinePerPlacementDto)
                .collect(Collectors.toList()) : Collections.emptyList();
        List<PatrimoineYearDto> totals = m.totals() != null ? m.totals().stream()
                .map(this::toPatrimoineYearDto)
                .collect(Collectors.toList()) : Collections.emptyList();

        PatrimoineProjectionsDto dto = new PatrimoineProjectionsDto();
        dto.setPerPlacement(perPlacement);
        dto.setTotals(totals);
        return dto;
    }

    public PatrimoineResponseDto toPatrimoineResponseDto(BudgetDataModel data, PatrimoineProjectionsModel projections) {
        List<PlacementDto> placements = data.getEffectivePlacements().stream()
                .map(this::toPlacementDto)
                .collect(Collectors.toList());

        List<TransferDto> transfers = data.getEffectiveTransfers().stream()
                .map(this::toTransferDto)
                .collect(Collectors.toList());

        List<RealEstateDto> realEstate = data.getEffectiveRealEstate().stream()
                .map(this::toRealEstateDto)
                .collect(Collectors.toList());

        List<LoanDto> loans = data.getEffectiveLoans().stream()
                .map(this::toLoanDto)
                .collect(Collectors.toList());

        List<AssetCategoryDto> assetCategories = data.getEffectiveAssetCategories().stream()
                .map(this::toAssetCategoryDto)
                .collect(Collectors.toList());

        List<BankImportCategoryDto> bankCategories = data.bankImport() != null && data.bankImport().categories() != null ?
                data.bankImport().categories().stream()
                        .map(this::toBankImportCategoryDto)
                        .collect(Collectors.toList()) : Collections.emptyList();
        
        PatrimoineProjectionsDto projDto = toPatrimoineProjectionsDto(projections);

        PatrimoineResponseDto dto = new PatrimoineResponseDto();
        dto.setPlacements(placements);
        dto.setTransfers(transfers);
        dto.setLoans(loans);
        dto.setRealEstate(realEstate);
        dto.setPatrimoine(projDto);
        dto.setAssetCategories(assetCategories);
        dto.setBankCategories(bankCategories);
        return dto;
    }

    public AssetCategoryDto toAssetCategoryDto(AssetCategoryModel m) {
        if (m == null) return null;
        AssetCategoryDto dto = new AssetCategoryDto();
        dto.setId(m.id());
        dto.setIcon(m.icon());
        dto.setName(m.name());
        dto.setBucket(m.bucket());
        return dto;
    }

    public BankImportCategoryDto toBankImportCategoryDto(BankImportModel.CategoryModel m) {
        if (m == null) return null;
        BankImportCategoryDto dto = new BankImportCategoryDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        if (m.kind() != null) {
            try {
                dto.setKind(BankImportCategoryDto.KindEnum.fromValue(m.kind()));
            } catch (Exception e) {
                // ignore
            }
        }
        if (m.compressible() != null) {
            try {
                dto.setCompressible(BankImportCategoryDto.CompressibleEnum.fromValue(m.compressible()));
            } catch (Exception e) {
                // ignore
            }
        }
        return dto;
    }
}
