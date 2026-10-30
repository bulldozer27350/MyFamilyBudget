package com.moe.myfamilybudget.web.mapper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.AssetCategoryDto;
import com.moe.myfamilybudget.api.model.BankImportCategoryDto;
import com.moe.myfamilybudget.api.model.LoanDto;
import com.moe.myfamilybudget.api.model.PatrimoinePerPlacementDto;
import com.moe.myfamilybudget.api.model.PatrimoineProjectionsDto;
import com.moe.myfamilybudget.api.model.PatrimoineResponseDto;
import com.moe.myfamilybudget.api.model.PatrimoineYearDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionDto;
import com.moe.myfamilybudget.api.model.PlacementEvolutionPointDto;
import com.moe.myfamilybudget.api.model.PlacementHistoryEntryDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.TransferDto;
import com.moe.myfamilybudget.application.usecase.wealth.AssetCategoryModel;
import com.moe.myfamilybudget.application.usecase.wealth.BankImportCategoryModel;
import com.moe.myfamilybudget.application.usecase.wealth.LoanModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineTransferModel;
import com.moe.myfamilybudget.application.usecase.wealth.PatrimoineYearModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementEvolutionModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementHistoryEntryModel;
import com.moe.myfamilybudget.application.usecase.wealth.PlacementModel;
import com.moe.myfamilybudget.application.usecase.wealth.RealEstateModel;
import com.moe.myfamilybudget.application.usecase.wealth.WealthResultModel;

/**
 * Mapper assurant la conversion entre le modèle applicatif ({@code application-api}, package
 * {@code application.usecase.wealth}) et les DTO OpenAPI (R-81, R-90, SILO-312, SILO-320).
 */
@Component
public class PatrimoineMapper {

    private static final Logger LOG = LoggerFactory.getLogger(PatrimoineMapper.class);

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
        dto.setSweepPriority(m.sweepPriority());
        dto.setSweepCap(m.sweepCap());
        dto.setPauseTriggerBalance(m.pauseTriggerBalance());
        dto.setPausePriority(m.pausePriority());
        dto.setCategoryId(m.categoryId());
        List<PlacementHistoryEntryModel> history = m.history() != null ? m.history() : Collections.emptyList();
        dto.setHistory(history.stream()
                .map(this::toPlacementHistoryEntryDto)
                .collect(Collectors.toList()));
        return dto;
    }

    public PlacementHistoryEntryDto toPlacementHistoryEntryDto(PlacementHistoryEntryModel m) {
        if (m == null) return null;
        PlacementHistoryEntryDto dto = new PlacementHistoryEntryDto();
        dto.setId(m.id());
        dto.setDate(m.date());
        dto.setValue(m.value());
        dto.setNotes(m.notes());
        return dto;
    }

    public TransferDto toTransferDto(PatrimoineTransferModel m) {
        if (m == null) return null;
        TransferDto dto = new TransferDto();
        dto.setId(m.id());
        dto.setPlacement(m.placement());
        dto.setDate(m.date());
        dto.setAmount(m.amount());
        dto.setNotes(m.notes());
        return dto;
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
        dto.setInitialAmount(m.initialAmount());
        dto.setTotalInstallments(m.totalInstallments());
        dto.setStepDate(m.stepDate());
        return dto;
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

    public PlacementEvolutionDto toPlacementEvolutionDto(PlacementEvolutionModel m) {
        if (m == null) return null;
        List<PlacementEvolutionPointDto> points = m.points().stream().map(pt -> {
            PlacementEvolutionPointDto dto = new PlacementEvolutionPointDto();
            dto.setTimestamp(toEpochMillis(pt.date()));
            dto.setDateISO(pt.date().toString());
            dto.setLabel(formatLabel(pt.date()));
            dto.setReal(pt.real());
            dto.setPess(pt.pess());
            dto.setCorr(pt.corr());
            dto.setOpti(pt.opti());
            return dto;
        }).collect(Collectors.toList());

        PlacementEvolutionDto dto = new PlacementEvolutionDto();
        dto.setPlacementId(m.placementId());
        dto.setAnchorTimestamp(toEpochMillis(m.anchorDate()));
        dto.setTodayTimestamp(toEpochMillis(m.today()));
        dto.setPoints(points);
        return dto;
    }

    private static long toEpochMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static String formatLabel(LocalDate d) {
        String[] monthNames = {"Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sep", "Oct", "Nov", "Déc"};
        return String.format("%02d/%s/%d", d.getDayOfMonth(), monthNames[d.getMonthValue() - 1], d.getYear());
    }

    public PatrimoineResponseDto toPatrimoineResponseDto(WealthResultModel model) {
        List<PlacementDto> placements = orEmpty(model.placements()).stream()
                .map(this::toPlacementDto)
                .collect(Collectors.toList());

        List<TransferDto> transfers = orEmpty(model.transfers()).stream()
                .map(this::toTransferDto)
                .collect(Collectors.toList());

        List<RealEstateDto> realEstate = orEmpty(model.realEstate()).stream()
                .map(this::toRealEstateDto)
                .collect(Collectors.toList());

        List<LoanDto> loans = orEmpty(model.loans()).stream()
                .map(this::toLoanDto)
                .collect(Collectors.toList());

        List<AssetCategoryDto> assetCategories = orEmpty(model.assetCategories()).stream()
                .map(this::toAssetCategoryDto)
                .collect(Collectors.toList());

        List<BankImportCategoryDto> bankCategories = orEmpty(model.bankCategories()).stream()
                .map(this::toBankImportCategoryDto)
                .collect(Collectors.toList());

        PatrimoineProjectionsDto projDto = toPatrimoineProjectionsDto(model.projections());

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

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
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

    public BankImportCategoryDto toBankImportCategoryDto(BankImportCategoryModel m) {
        if (m == null) return null;
        BankImportCategoryDto dto = new BankImportCategoryDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        if (m.kind() != null) {
            try {
                dto.setKind(BankImportCategoryDto.KindEnum.fromValue(m.kind()));
            } catch (Exception e) {
                LOG.warn("Valeur de 'kind' inconnue pour la catégorie d'import bancaire '{}', champ laissé vide : '{}'",
                        m.id(), m.kind(), e);
            }
        }
        if (m.compressible() != null) {
            try {
                dto.setCompressible(BankImportCategoryDto.CompressibleEnum.fromValue(m.compressible()));
            } catch (Exception e) {
                LOG.warn("Valeur de 'compressible' inconnue pour la catégorie d'import bancaire '{}', champ laissé vide : '{}'",
                        m.id(), m.compressible(), e);
            }
        }
        return dto;
    }
}
