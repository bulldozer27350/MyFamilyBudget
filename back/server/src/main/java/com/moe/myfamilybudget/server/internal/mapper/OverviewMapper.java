package com.moe.myfamilybudget.server.internal.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.BankImportDto;
import com.moe.myfamilybudget.api.model.BankTransactionDto;
import com.moe.myfamilybudget.api.model.BudgetDataDto;
import com.moe.myfamilybudget.api.model.CashflowYearDto;
import com.moe.myfamilybudget.api.model.ChargeDto;
import com.moe.myfamilybudget.api.model.IncomeDto;
import com.moe.myfamilybudget.api.model.OneOffExpenseDto;
import com.moe.myfamilybudget.api.model.OverviewResponseDto;
import com.moe.myfamilybudget.api.model.PatrimoinePerPlacementDto;
import com.moe.myfamilybudget.api.model.PatrimoineProjectionsDto;
import com.moe.myfamilybudget.api.model.PatrimoineYearDto;
import com.moe.myfamilybudget.api.model.PlacementDto;
import com.moe.myfamilybudget.api.model.RealEstateDto;
import com.moe.myfamilybudget.api.model.RetirementDto;
import com.moe.myfamilybudget.api.model.RetirementPersonDto;
import com.moe.myfamilybudget.api.model.SalaryHistoryDto;
import com.moe.myfamilybudget.api.model.SettingsDto;
import com.moe.myfamilybudget.api.model.TaxActualOverrideDto;
import com.moe.myfamilybudget.api.model.TaxBracketDto;
import com.moe.myfamilybudget.api.model.TaxChildDto;
import com.moe.myfamilybudget.api.model.TaxRateOverrideDto;
import com.moe.myfamilybudget.api.model.TransferDto;
import com.moe.myfamilybudget.api.model.TripleAmountDto;
import com.moe.myfamilybudget.api.model.VariableIncomeDto;
import com.moe.myfamilybudget.api.model.VariableOverrideDto;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.CashflowYearModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.OverviewResultModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoinePerPlacementModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineProjectionsModel;
import com.moe.myfamilybudget.server.internal.model.PatrimoineYearModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.TripleAmountModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;

@Component
public class OverviewMapper {

    public BudgetDataModel toInternalModel(BudgetDataDto dto) {
        if (dto == null) {
            return new BudgetDataModel(null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null);
        }

        SettingsModel settings = toSettingsModel(dto.getSettings());
        List<IncomeModel> incomes = dto.getIncomes() != null
                ? dto.getIncomes().stream().map(this::toIncomeModel).collect(Collectors.toList())
                : List.of();
        List<ChargeModel> charges = dto.getCharges() != null
                ? dto.getCharges().stream().map(this::toChargeModel).collect(Collectors.toList())
                : List.of();
        List<PlacementModel> placements = dto.getPlacements() != null
                ? dto.getPlacements().stream().map(this::toPlacementModel).collect(Collectors.toList())
                : List.of();
        List<RealEstateModel> realEstate = dto.getRealEstate() != null
                ? dto.getRealEstate().stream().map(this::toRealEstateModel).collect(Collectors.toList())
                : List.of();
        RetirementModel retirement = toRetirementModel(dto.getRetirement());
        List<TaxChildModel> taxChildren = dto.getTaxChildren() != null
                ? dto.getTaxChildren().stream().map(this::toTaxChildModel).collect(Collectors.toList())
                : List.of();
        List<TaxBracketModel> taxBrackets = dto.getTaxBrackets() != null
                ? dto.getTaxBrackets().stream().map(this::toTaxBracketModel).collect(Collectors.toList())
                : List.of();
        List<TaxRateOverrideModel> taxRateOverrides = dto.getTaxRateOverrides() != null
                ? dto.getTaxRateOverrides().stream().map(this::toTaxRateOverrideModel).collect(Collectors.toList())
                : List.of();
        List<TaxActualOverrideModel> taxActualOverrides = dto.getTaxActualOverrides() != null
                ? dto.getTaxActualOverrides().stream().map(this::toTaxActualOverrideModel).collect(Collectors.toList())
                : List.of();
        List<OneOffExpenseModel> oneoff = dto.getOneoff() != null
                ? dto.getOneoff().stream().map(this::toOneOffExpenseModel).collect(Collectors.toList())
                : List.of();
        List<TransferModel> transfers = dto.getTransfers() != null
                ? dto.getTransfers().stream().map(this::toTransferModel).collect(Collectors.toList())
                : List.of();
        List<VariableIncomeModel> variableIncomes = dto.getVariableIncomes() != null
                ? dto.getVariableIncomes().stream().map(this::toVariableIncomeModel).collect(Collectors.toList())
                : List.of();
        List<VariableOverrideModel> variableOverrides = dto.getVariableOverrides() != null
                ? dto.getVariableOverrides().stream().map(this::toVariableOverrideModel).collect(Collectors.toList())
                : List.of();
        BankImportModel bankImport = toBankImportModel(dto.getBankImport());

        return new BudgetDataModel(settings, incomes, charges, placements, realEstate, retirement, taxChildren,
                taxBrackets, taxRateOverrides, taxActualOverrides, oneoff, transfers, variableIncomes,
                variableOverrides, bankImport);
    }

    public BudgetDataDto toBudgetDataDto(BudgetDataModel model) {
        if (model == null) {
            return null;
        }
        BudgetDataDto dto = new BudgetDataDto();
        dto.setBankImport(toBankImportDto(model.bankImport()));
        dto.setCharges(
                model.charges() != null ? model.charges().stream().map(this::toChargeDto).collect(Collectors.toList())
                        : List.of());
        dto.setIncomes(
                model.incomes() != null ? model.incomes().stream().map(this::toIncomeDto).collect(Collectors.toList())
                        : List.of());
        dto.setPlacements(model.placements() != null
                ? model.placements().stream().map(this::toPlacementDto).collect(Collectors.toList())
                : List.of());
        dto.setRealEstate(model.realEstate() != null
                ? model.realEstate().stream().map(this::toRealEstateDto).collect(Collectors.toList())
                : List.of());
        dto.setRetirement(toRetirementDto(model.retirement()));
        dto.setSettings(toSettingsDto(model.settings()));
        dto.setTaxChildren(model.taxChildren() != null
                ? model.taxChildren().stream().map(this::toTaxChildDto).collect(Collectors.toList())
                : List.of());
        dto.setTaxBrackets(model.taxBrackets() != null
                ? model.taxBrackets().stream().map(this::toTaxBracketDto).collect(Collectors.toList())
                : List.of());
        dto.setTaxRateOverrides(model.taxRateOverrides() != null
                ? model.taxRateOverrides().stream().map(this::toTaxRateOverrideDto).collect(Collectors.toList())
                : List.of());
        dto.setTaxActualOverrides(model.taxActualOverrides() != null
                ? model.taxActualOverrides().stream().map(this::toTaxActualOverrideDto).collect(Collectors.toList())
                : List.of());
        dto.setOneoff(model.oneoff() != null
                ? model.oneoff().stream().map(this::toOneOffExpenseDto).collect(Collectors.toList())
                : List.of());
        dto.setTransfers(model.transfers() != null
                ? model.transfers().stream().map(this::toTransferDto).collect(Collectors.toList())
                : List.of());
        dto.setVariableIncomes(model.variableIncomes() != null
                ? model.variableIncomes().stream().map(this::toVariableIncomeDto).collect(Collectors.toList())
                : List.of());
        dto.setVariableOverrides(model.variableOverrides() != null
                ? model.variableOverrides().stream().map(this::toVariableOverrideDto).collect(Collectors.toList())
                : List.of());
        return dto;
    }

    public OverviewResponseDto toDto(OverviewResultModel model) {
        if (model == null) {
            return null;
        }

        BudgetDataDto dataDto = toBudgetDataDto(model.data());
        List<CashflowYearDto> cashflowDtos = model.cashflow() != null
                ? model.cashflow().stream().map(this::toCashflowYearDto).collect(Collectors.toList())
                : List.of();
        PatrimoineProjectionsDto patrimoineDto = toPatrimoineProjectionsDto(model.patrimoine());
        TripleAmountDto retirePatrimoineDto = toTripleAmountDto(model.retirePatrimoine());
        TripleAmountDto fireRenteDto = toTripleAmountDto(model.fireRente());
        TripleAmountDto financialOnlyRenteDto = toTripleAmountDto(model.financialOnlyRente());

        OverviewResponseDto dto = new OverviewResponseDto();
        dto.setData(dataDto);
        dto.setYears(model.years());
        dto.setCashflow(cashflowDtos);
        dto.setPatrimoine(patrimoineDto);
        dto.setUseConstantEuros(model.useConstantEuros());
        dto.setRetireYear(model.retireYear());
        dto.setPivotBalance(model.pivotBalance());
        dto.setPatrimoineActuel(model.patrimoineActuel());
        dto.setFluxNetActuel(model.fluxNetActuel());
        dto.setRetireCharges(model.retireCharges());
        dto.setTotalPensions(model.totalPensions());
        dto.setRetirePatrimoine(retirePatrimoineDto);
        dto.setFireRente(fireRenteDto);
        dto.setFinancialOnlyRente(financialOnlyRenteDto);

        return dto;
    }

    public CashflowYearDto toCashflowYearDto(CashflowYearModel m) {
        if (m == null)
            return null;
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

    public PatrimoineProjectionsDto toPatrimoineProjectionsDto(PatrimoineProjectionsModel m) {
        if (m == null)
            return null;
        List<PatrimoinePerPlacementDto> perPlacement = m.perPlacement() != null
                ? m.perPlacement().stream().map(this::toPatrimoinePerPlacementDto).collect(Collectors.toList())
                : List.of();
        List<PatrimoineYearDto> totals = m.totals() != null
                ? m.totals().stream().map(this::toPatrimoineYearDto).collect(Collectors.toList())
                : List.of();
        PatrimoineProjectionsDto dto = new PatrimoineProjectionsDto();
        dto.setPerPlacement(perPlacement);
        dto.setTotals(totals);
        return dto;
    }

    public PatrimoinePerPlacementDto toPatrimoinePerPlacementDto(PatrimoinePerPlacementModel m) {
        if (m == null)
            return null;
        List<PatrimoineYearDto> rows = m.rows() != null
                ? m.rows().stream().map(this::toPatrimoineYearDto).collect(Collectors.toList())
                : List.of();
        PatrimoinePerPlacementDto dto = new PatrimoinePerPlacementDto();
        dto.setLabel(m.label());
        dto.setRows(rows);
        return dto;
    }

    public PatrimoineYearDto toPatrimoineYearDto(PatrimoineYearModel m) {
        if (m == null)
            return null;

        PatrimoineYearDto dto = new PatrimoineYearDto();
        dto.setYear(m.year());
        dto.setPess(m.pess());
        dto.setCorr(m.corr());
        dto.setOpti(m.opti());
        return dto;
    }

    public TripleAmountDto toTripleAmountDto(TripleAmountModel m) {
        if (m == null)
            return null;
        TripleAmountDto dto = new TripleAmountDto();
        dto.setPess(m.pess());
        dto.setCorr(m.corr());
        dto.setOpti(m.opti());
        return dto;
    }

    private SettingsModel toSettingsModel(SettingsDto dto) {
        if (dto == null)
            return null;
        return new SettingsModel(dto.getBirthYear(), dto.getRetireAge(), dto.getSimulateUntilAge(),
                dto.getInflationRate(), dto.getPivotDate(), dto.getPivotMode(), dto.getStartBalance(),
                dto.getChildExitAge(), dto.getTaxAbattement(), dto.getPass2026(), dto.getPassGrowthRate());
    }

    private SettingsDto toSettingsDto(SettingsModel model) {
        if (model == null)
            return null;
        SettingsDto dto = new SettingsDto();
        dto.setBirthYear(model.birthYear());
        dto.setRetireAge(model.retireAge());
        dto.setSimulateUntilAge(model.simulateUntilAge());
        dto.setInflationRate(model.inflationRate());
        dto.setPivotDate(model.pivotDate());
        dto.setPivotMode(model.pivotMode());
        dto.setStartBalance(model.startBalance());
        dto.setChildExitAge(model.childExitAge());
        dto.setTaxAbattement(model.taxAbattement());
        dto.setPass2026(model.pass2026());
        dto.setPassGrowthRate(model.passGrowthRate());
        return dto;
    }

    private IncomeModel toIncomeModel(IncomeDto dto) {
        if (dto == null)
            return null;
        return new IncomeModel(dto.getId(), dto.getLabel(), dto.getMonthly(), dto.getStart(), dto.getEnd(),
                dto.getGrowthRate(), dto.getCategoryId(), dto.getNotes());
    }

    private IncomeDto toIncomeDto(IncomeModel m) {
        if (m == null)
            return null;
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

    private ChargeModel toChargeModel(ChargeDto dto) {
        if (dto == null)
            return null;
        return new ChargeModel(dto.getId(), dto.getLabel(), dto.getMonthly(), dto.getStart(), dto.getEnd(),
                dto.getGrowthRate(), dto.getCategoryId(), dto.getNotes());
    }

    private ChargeDto toChargeDto(ChargeModel m) {
        if (m == null)
            return null;
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

    private PlacementModel toPlacementModel(PlacementDto dto) {
        if (dto == null)
            return null;
        return new PlacementModel(dto.getId(), dto.getLabel(), dto.getCategory(), dto.getBalance(),
                dto.getBalanceDate(), dto.getMonthly(), dto.getMonthlyFrom(), dto.getMonthlyUntil(), dto.getRatePess(),
                dto.getRateCorr(), dto.getRateOpti(), dto.getExcludedFromRetirement(), dto.getNotes());
    }

    private PlacementDto toPlacementDto(PlacementModel m) {
        if (m == null)
            return null;
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

    private RealEstateModel toRealEstateModel(RealEstateDto dto) {
        if (dto == null)
            return null;
        return new RealEstateModel(dto.getId(), dto.getLabel(), dto.getType(), dto.getCurrentValue(),
                dto.getValuationYear(), dto.getAnnualGrowthRate(), dto.getNotes());
    }

    private RealEstateDto toRealEstateDto(RealEstateModel m) {
        if (m == null)
            return null;
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

    private RetirementModel toRetirementModel(RetirementDto dto) {
        if (dto == null)
            return null;
        List<RetirementModel.RetirementPersonModel> people = dto.getPeople() != null
                ? dto.getPeople().stream().map(this::toRetirementPersonModel).collect(Collectors.toList())
                : List.of();
        return new RetirementModel(people, dto.getPass2026(), dto.getPassGrowthRate(), dto.getAgircPointValue(),
                dto.getAgircPointDateGlobal(), dto.getAgircPointGrowthRate());
    }

    private RetirementDto toRetirementDto(RetirementModel m) {
        if (m == null)
            return null;
        List<RetirementPersonDto> people = m.people() != null
                ? m.people().stream().map(this::toRetirementPersonDto).collect(Collectors.toList())
                : List.of();
        RetirementDto dto = new RetirementDto();
        dto.setPeople(people);
        dto.setPass2026(m.pass2026());
        dto.setPassGrowthRate(m.passGrowthRate());
        dto.setAgircPointValue(m.agircPointValue());
        dto.setAgircPointDateGlobal(m.agircPointDateGlobal());
        dto.setAgircPointGrowthRate(m.agircPointGrowthRate());
        return dto;
    }

    private RetirementModel.RetirementPersonModel toRetirementPersonModel(RetirementPersonDto dto) {
        if (dto == null)
            return null;
        List<RetirementModel.SalaryHistoryModel> sal = dto.getSalaryHistory() != null ? dto.getSalaryHistory().stream()
                .map(s -> new RetirementModel.SalaryHistoryModel(s.getYear(), s.getSalary()))
                .collect(Collectors.toList()) : List.of();
        return new RetirementModel.RetirementPersonModel(dto.getId(), dto.getName(), dto.getBirthYear(),
                dto.getIncomeLabel(), dto.getTrimestresValides(), dto.getTrimestresDate(), sal, dto.getAgircPoints(),
                dto.getRatioPointsParEuro());
    }

    private RetirementPersonDto toRetirementPersonDto(RetirementModel.RetirementPersonModel m) {
        if (m == null)
            return null;
        List<SalaryHistoryDto> sal = List.of();
        if (m.salaryHistory() != null) {
            sal = new ArrayList<>();
            for (RetirementModel.SalaryHistoryModel s : m.salaryHistory()) {
                SalaryHistoryDto dto = new SalaryHistoryDto();
                dto.setYear(s.year());
                dto.setSalary(s.salary());
                sal.add(dto);
            }
        }
        RetirementPersonDto dto = new RetirementPersonDto();
        dto.setId(m.id());
        dto.setName(m.name());
        dto.setBirthYear(m.birthYear());
        dto.setIncomeLabel(m.incomeLabel());
        dto.setTrimestresValides(m.trimestresValides());
        dto.setTrimestresDate(m.trimestresDate());
        dto.setSalaryHistory(sal);
        dto.setAgircPoints(m.agircPoints());
        dto.setRatioPointsParEuro(m.ratioPointsParEuro());
        return dto;
    }

    private TaxChildModel toTaxChildModel(TaxChildDto dto) {
        if (dto == null)
            return null;
        return new TaxChildModel(dto.getId(), dto.getName(), dto.getBirthYear());
    }

    private TaxChildDto toTaxChildDto(TaxChildModel m) {
        if (m == null)
            return null;
        TaxChildDto dto = new TaxChildDto();
        dto.setId(m.id());
        dto.setName(m.name());
        dto.setBirthYear(m.birthYear());
        return dto;
    }

    private TaxBracketModel toTaxBracketModel(TaxBracketDto dto) {
        if (dto == null)
            return null;
        return new TaxBracketModel(dto.getId(), dto.getUpTo(), dto.getRate());
    }

    private TaxBracketDto toTaxBracketDto(TaxBracketModel m) {
        if (m == null)
            return null;
        TaxBracketDto dto = new TaxBracketDto();
        dto.setId(m.id());
        dto.setUpTo(m.upTo());
        dto.setRate(m.rate());
        return dto;
    }

    private TaxRateOverrideModel toTaxRateOverrideModel(TaxRateOverrideDto dto) {
        if (dto == null)
            return null;
        return new TaxRateOverrideModel(dto.getYear(), dto.getRate());
    }

    private TaxRateOverrideDto toTaxRateOverrideDto(TaxRateOverrideModel m) {
        if (m == null)
            return null;
        TaxRateOverrideDto dto = new TaxRateOverrideDto();
        dto.setYear(m.year());
        dto.setRate(m.rate());
        return dto;
    }

    private TaxActualOverrideModel toTaxActualOverrideModel(TaxActualOverrideDto dto) {
        if (dto == null)
            return null;
        return new TaxActualOverrideModel(dto.getYear(), dto.getAmount());
    }

    private TaxActualOverrideDto toTaxActualOverrideDto(TaxActualOverrideModel m) {
        if (m == null)
            return null;
        TaxActualOverrideDto dto = new TaxActualOverrideDto();
        dto.setYear(m.year());
        dto.setAmount(m.amount());
        return dto;
    }

    private OneOffExpenseModel toOneOffExpenseModel(OneOffExpenseDto dto) {
        if (dto == null)
            return null;
        return new OneOffExpenseModel(dto.getId(), dto.getLabel(), dto.getDate(), dto.getAmount(), dto.getNotes());
    }

    private OneOffExpenseDto toOneOffExpenseDto(OneOffExpenseModel m) {
        if (m == null)
            return null;
        OneOffExpenseDto dto = new OneOffExpenseDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setDate(m.date());
        dto.setAmount(m.amount());
        dto.setNotes(m.notes());
        return dto;
    }

    private TransferModel toTransferModel(TransferDto dto) {
        if (dto == null)
            return null;
        return new TransferModel(dto.getId(), dto.getPlacement(), dto.getDate(), dto.getAmount(), dto.getNotes());
    }

    private TransferDto toTransferDto(TransferModel m) {
        if (m == null)
            return null;
        TransferDto dto = new TransferDto();
        dto.setId(m.id());
        dto.setPlacement(m.placement());
        dto.setDate(m.date());
        dto.setAmount(m.amount());
        dto.setNotes(m.notes());
        return dto;
    }

    private VariableIncomeModel toVariableIncomeModel(VariableIncomeDto dto) {
        if (dto == null)
            return null;
        return new VariableIncomeModel(dto.getId(), dto.getLabel(), dto.getRefIncomeLabel(), dto.getRate(),
                dto.getStartYear(), dto.getEndYear(), dto.getTaxable());
    }

    private VariableIncomeDto toVariableIncomeDto(VariableIncomeModel m) {
        if (m == null)
            return null;
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

    private VariableOverrideModel toVariableOverrideModel(VariableOverrideDto dto) {
        if (dto == null)
            return null;
        return new VariableOverrideModel(dto.getId(), dto.getLabel(), dto.getYear(), dto.getAmount(), dto.getTaxable());
    }

    private VariableOverrideDto toVariableOverrideDto(VariableOverrideModel m) {
        if (m == null)
            return null;
        VariableOverrideDto dto = new VariableOverrideDto();
        dto.setId(m.id());
        dto.setLabel(m.label());
        dto.setYear(m.year());
        dto.setAmount(m.amount());
        dto.setTaxable(m.taxable());
        return dto;
    }

    private BankImportModel toBankImportModel(BankImportDto dto) {
        if (dto == null)
            return null;
        List<BankImportModel.BankTransactionModel> txs = dto.getTransactions() != null ? dto.getTransactions().stream()
                .map(t -> new BankImportModel.BankTransactionModel(t.getId(), t.getDate(), t.getLabel(), t.getAmount()))
                .collect(Collectors.toList()) : List.of();
        return new BankImportModel(txs, List.of(), List.of());
    }

    private BankImportDto toBankImportDto(BankImportModel m) {
        if (m == null)
            return null;
        List<BankTransactionDto> txs = m.transactions() != null ? m.transactions().stream().map(t -> {
            BankTransactionDto dto = new BankTransactionDto();
            dto.setId(t.id());
            dto.setDate(t.date());
            dto.setLabel(t.label());
            dto.setAmount(t.amount());
            return dto;
        }).collect(Collectors.toList()) : List.of();
        BankImportDto dto = new BankImportDto();
        dto.setTransactions(txs);
        return dto;
    }
}
