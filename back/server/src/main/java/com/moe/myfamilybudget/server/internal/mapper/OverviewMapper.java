package com.moe.myfamilybudget.server.internal.mapper;

import com.moe.myfamilybudget.api.model.*;
import com.moe.myfamilybudget.server.internal.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class OverviewMapper {

    public BudgetDataModel toInternalModel(BudgetDataDto dto) {
        if (dto == null) {
            return new BudgetDataModel(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        SettingsModel settings = toSettingsModel(dto.settings());
        List<IncomeModel> incomes = dto.incomes() != null ? dto.incomes().stream().map(this::toIncomeModel).collect(Collectors.toList()) : List.of();
        List<ChargeModel> charges = dto.charges() != null ? dto.charges().stream().map(this::toChargeModel).collect(Collectors.toList()) : List.of();
        List<PlacementModel> placements = dto.placements() != null ? dto.placements().stream().map(this::toPlacementModel).collect(Collectors.toList()) : List.of();
        List<RealEstateModel> realEstate = dto.realEstate() != null ? dto.realEstate().stream().map(this::toRealEstateModel).collect(Collectors.toList()) : List.of();
        RetirementModel retirement = toRetirementModel(dto.retirement());
        List<TaxChildModel> taxChildren = dto.taxChildren() != null ? dto.taxChildren().stream().map(this::toTaxChildModel).collect(Collectors.toList()) : List.of();
        List<TaxBracketModel> taxBrackets = dto.taxBrackets() != null ? dto.taxBrackets().stream().map(this::toTaxBracketModel).collect(Collectors.toList()) : List.of();
        List<TaxRateOverrideModel> taxRateOverrides = dto.taxRateOverrides() != null ? dto.taxRateOverrides().stream().map(this::toTaxRateOverrideModel).collect(Collectors.toList()) : List.of();
        List<TaxActualOverrideModel> taxActualOverrides = dto.taxActualOverrides() != null ? dto.taxActualOverrides().stream().map(this::toTaxActualOverrideModel).collect(Collectors.toList()) : List.of();
        List<OneOffExpenseModel> oneoff = dto.oneoff() != null ? dto.oneoff().stream().map(this::toOneOffExpenseModel).collect(Collectors.toList()) : List.of();
        List<TransferModel> transfers = dto.transfers() != null ? dto.transfers().stream().map(this::toTransferModel).collect(Collectors.toList()) : List.of();
        List<VariableIncomeModel> variableIncomes = dto.variableIncomes() != null ? dto.variableIncomes().stream().map(this::toVariableIncomeModel).collect(Collectors.toList()) : List.of();
        List<VariableOverrideModel> variableOverrides = dto.variableOverrides() != null ? dto.variableOverrides().stream().map(this::toVariableOverrideModel).collect(Collectors.toList()) : List.of();
        BankImportModel bankImport = toBankImportModel(dto.bankImport());

        return new BudgetDataModel(
            settings, incomes, charges, placements, realEstate, retirement,
            taxChildren, taxBrackets, taxRateOverrides, taxActualOverrides,
            oneoff, transfers, variableIncomes, variableOverrides, bankImport
        );
    }

    public BudgetDataDto toBudgetDataDto(BudgetDataModel model) {
        if (model == null) {
            return null;
        }
        return new BudgetDataDto(
            toSettingsDto(model.settings()),
            model.incomes() != null ? model.incomes().stream().map(this::toIncomeDto).collect(Collectors.toList()) : List.of(),
            model.charges() != null ? model.charges().stream().map(this::toChargeDto).collect(Collectors.toList()) : List.of(),
            model.placements() != null ? model.placements().stream().map(this::toPlacementDto).collect(Collectors.toList()) : List.of(),
            model.realEstate() != null ? model.realEstate().stream().map(this::toRealEstateDto).collect(Collectors.toList()) : List.of(),
            toRetirementDto(model.retirement()),
            model.taxChildren() != null ? model.taxChildren().stream().map(this::toTaxChildDto).collect(Collectors.toList()) : List.of(),
            model.taxBrackets() != null ? model.taxBrackets().stream().map(this::toTaxBracketDto).collect(Collectors.toList()) : List.of(),
            model.taxRateOverrides() != null ? model.taxRateOverrides().stream().map(this::toTaxRateOverrideDto).collect(Collectors.toList()) : List.of(),
            model.taxActualOverrides() != null ? model.taxActualOverrides().stream().map(this::toTaxActualOverrideDto).collect(Collectors.toList()) : List.of(),
            model.oneoff() != null ? model.oneoff().stream().map(this::toOneOffExpenseDto).collect(Collectors.toList()) : List.of(),
            model.transfers() != null ? model.transfers().stream().map(this::toTransferDto).collect(Collectors.toList()) : List.of(),
            model.variableIncomes() != null ? model.variableIncomes().stream().map(this::toVariableIncomeDto).collect(Collectors.toList()) : List.of(),
            model.variableOverrides() != null ? model.variableOverrides().stream().map(this::toVariableOverrideDto).collect(Collectors.toList()) : List.of(),
            toBankImportDto(model.bankImport())
        );
    }

    public OverviewResponseDto toDto(OverviewResultModel model) {
        if (model == null) {
            return null;
        }

        BudgetDataDto dataDto = toBudgetDataDto(model.data());
        List<CashflowYearDto> cashflowDtos = model.cashflow() != null ? model.cashflow().stream().map(this::toCashflowYearDto).collect(Collectors.toList()) : List.of();
        PatrimoineProjectionsDto patrimoineDto = toPatrimoineProjectionsDto(model.patrimoine());
        TripleAmountDto retirePatrimoineDto = toTripleAmountDto(model.retirePatrimoine());
        TripleAmountDto fireRenteDto = toTripleAmountDto(model.fireRente());
        TripleAmountDto financialOnlyRenteDto = toTripleAmountDto(model.financialOnlyRente());

        return new OverviewResponseDto(
            dataDto,
            model.years(),
            cashflowDtos,
            patrimoineDto,
            model.useConstantEuros(),
            model.retireYear(),
            model.pivotBalance(),
            model.patrimoineActuel(),
            model.fluxNetActuel(),
            model.retireCharges(),
            model.totalPensions(),
            retirePatrimoineDto,
            fireRenteDto,
            financialOnlyRenteDto
        );
    }

    public CashflowYearDto toCashflowYearDto(CashflowYearModel m) {
        if (m == null) return null;
        return new CashflowYearDto(
            m.year(), m.income(), m.variableIncome(), m.savings(), m.charges(),
            m.oneoff(), m.transfersY(), m.impots(), m.regularisation(), m.net(), m.balance()
        );
    }

    public PatrimoineProjectionsDto toPatrimoineProjectionsDto(PatrimoineProjectionsModel m) {
        if (m == null) return null;
        List<PatrimoinePerPlacementDto> perPlacement = m.perPlacement() != null
            ? m.perPlacement().stream().map(this::toPatrimoinePerPlacementDto).collect(Collectors.toList())
            : List.of();
        List<PatrimoineYearDto> totals = m.totals() != null
            ? m.totals().stream().map(this::toPatrimoineYearDto).collect(Collectors.toList())
            : List.of();
        return new PatrimoineProjectionsDto(perPlacement, totals);
    }

    public PatrimoinePerPlacementDto toPatrimoinePerPlacementDto(PatrimoinePerPlacementModel m) {
        if (m == null) return null;
        List<PatrimoineYearDto> rows = m.rows() != null
            ? m.rows().stream().map(this::toPatrimoineYearDto).collect(Collectors.toList())
            : List.of();
        return new PatrimoinePerPlacementDto(m.label(), rows);
    }

    public PatrimoineYearDto toPatrimoineYearDto(PatrimoineYearModel m) {
        if (m == null) return null;
        return new PatrimoineYearDto(m.year(), m.pess(), m.corr(), m.opti());
    }

    public TripleAmountDto toTripleAmountDto(TripleAmountModel m) {
        if (m == null) return null;
        return new TripleAmountDto(m.pess(), m.corr(), m.opti());
    }

    private SettingsModel toSettingsModel(SettingsDto dto) {
        if (dto == null) return null;
        return new SettingsModel(
            dto.birthYear(), dto.retireAge(), dto.simulateUntilAge(), dto.inflationRate(),
            dto.pivotDate(), dto.pivotMode(), dto.startBalance(), dto.childExitAge(),
            dto.taxAbattement(), dto.pass2026(), dto.passGrowthRate()
        );
    }

    private SettingsDto toSettingsDto(SettingsModel model) {
        if (model == null) return null;
        return new SettingsDto(
            model.birthYear(), model.retireAge(), model.simulateUntilAge(), model.inflationRate(),
            model.pivotDate(), model.pivotMode(), model.startBalance(), model.childExitAge(),
            model.taxAbattement(), model.pass2026(), model.passGrowthRate()
        );
    }

    private IncomeModel toIncomeModel(IncomeDto dto) {
        if (dto == null) return null;
        return new IncomeModel(
            dto.id(), dto.label(), dto.monthly(), dto.start(), dto.end(),
            dto.growthRate(), dto.categoryId(), dto.notes()
        );
    }

    private IncomeDto toIncomeDto(IncomeModel m) {
        if (m == null) return null;
        return new IncomeDto(
            m.id(), m.label(), m.monthly(), m.start(), m.end(),
            m.growthRate(), m.categoryId(), m.notes()
        );
    }

    private ChargeModel toChargeModel(ChargeDto dto) {
        if (dto == null) return null;
        return new ChargeModel(
            dto.id(), dto.label(), dto.monthly(), dto.start(), dto.end(),
            dto.growthRate(), dto.categoryId(), dto.notes()
        );
    }

    private ChargeDto toChargeDto(ChargeModel m) {
        if (m == null) return null;
        return new ChargeDto(
            m.id(), m.label(), m.monthly(), m.start(), m.end(),
            m.growthRate(), m.categoryId(), m.notes()
        );
    }

    private PlacementModel toPlacementModel(PlacementDto dto) {
        if (dto == null) return null;
        return new PlacementModel(
            dto.id(), dto.label(), dto.category(), dto.balance(), dto.balanceDate(),
            dto.monthly(), dto.monthlyFrom(), dto.monthlyUntil(), dto.ratePess(),
            dto.rateCorr(), dto.rateOpti(), dto.excludedFromRetirement(), dto.notes()
        );
    }

    private PlacementDto toPlacementDto(PlacementModel m) {
        if (m == null) return null;
        return new PlacementDto(
            m.id(), m.label(), m.category(), m.balance(), m.balanceDate(),
            m.monthly(), m.monthlyFrom(), m.monthlyUntil(), m.ratePess(),
            m.rateCorr(), m.rateOpti(), m.excludedFromRetirement(), m.notes()
        );
    }

    private RealEstateModel toRealEstateModel(RealEstateDto dto) {
        if (dto == null) return null;
        return new RealEstateModel(
            dto.id(), dto.label(), dto.type(), dto.currentValue(), dto.valuationYear(),
            dto.annualGrowthRate(), dto.notes()
        );
    }

    private RealEstateDto toRealEstateDto(RealEstateModel m) {
        if (m == null) return null;
        return new RealEstateDto(
            m.id(), m.label(), m.type(), m.currentValue(), m.valuationYear(),
            m.annualGrowthRate(), m.notes()
        );
    }

    private RetirementModel toRetirementModel(RetirementDto dto) {
        if (dto == null) return null;
        List<RetirementModel.RetirementPersonModel> people = dto.people() != null
            ? dto.people().stream().map(this::toRetirementPersonModel).collect(Collectors.toList())
            : List.of();
        return new RetirementModel(
            people, dto.pass2026(), dto.passGrowthRate(), dto.agircPointValue(),
            dto.agircPointDateGlobal(), dto.agircPointGrowthRate()
        );
    }

    private RetirementDto toRetirementDto(RetirementModel m) {
        if (m == null) return null;
        List<RetirementDto.RetirementPersonDto> people = m.people() != null
            ? m.people().stream().map(this::toRetirementPersonDto).collect(Collectors.toList())
            : List.of();
        return new RetirementDto(
            people, m.pass2026(), m.passGrowthRate(), m.agircPointValue(),
            m.agircPointDateGlobal(), m.agircPointGrowthRate()
        );
    }

    private RetirementModel.RetirementPersonModel toRetirementPersonModel(RetirementDto.RetirementPersonDto dto) {
        if (dto == null) return null;
        List<RetirementModel.SalaryHistoryModel> sal = dto.salaryHistory() != null
            ? dto.salaryHistory().stream().map(s -> new RetirementModel.SalaryHistoryModel(s.year(), s.salary())).collect(Collectors.toList())
            : List.of();
        return new RetirementModel.RetirementPersonModel(
            dto.id(), dto.name(), dto.birthYear(), dto.incomeLabel(), dto.trimestresValides(),
            dto.trimestresDate(), sal, dto.agircPoints(), dto.ratioPointsParEuro()
        );
    }

    private RetirementDto.RetirementPersonDto toRetirementPersonDto(RetirementModel.RetirementPersonModel m) {
        if (m == null) return null;
        List<RetirementDto.SalaryHistoryDto> sal = m.salaryHistory() != null
            ? m.salaryHistory().stream().map(s -> new RetirementDto.SalaryHistoryDto(s.year(), s.salary())).collect(Collectors.toList())
            : List.of();
        return new RetirementDto.RetirementPersonDto(
            m.id(), m.name(), m.birthYear(), m.incomeLabel(), m.trimestresValides(),
            m.trimestresDate(), sal, m.agircPoints(), m.ratioPointsParEuro()
        );
    }

    private TaxChildModel toTaxChildModel(TaxChildDto dto) {
        if (dto == null) return null;
        return new TaxChildModel(dto.id(), dto.name(), dto.birthYear());
    }

    private TaxChildDto toTaxChildDto(TaxChildModel m) {
        if (m == null) return null;
        return new TaxChildDto(m.id(), m.name(), m.birthYear());
    }

    private TaxBracketModel toTaxBracketModel(TaxBracketDto dto) {
        if (dto == null) return null;
        return new TaxBracketModel(dto.id(), dto.upTo(), dto.rate());
    }

    private TaxBracketDto toTaxBracketDto(TaxBracketModel m) {
        if (m == null) return null;
        return new TaxBracketDto(m.id(), m.upTo(), m.rate());
    }

    private TaxRateOverrideModel toTaxRateOverrideModel(TaxRateOverrideDto dto) {
        if (dto == null) return null;
        return new TaxRateOverrideModel(dto.year(), dto.rate());
    }

    private TaxRateOverrideDto toTaxRateOverrideDto(TaxRateOverrideModel m) {
        if (m == null) return null;
        return new TaxRateOverrideDto(m.year(), m.rate());
    }

    private TaxActualOverrideModel toTaxActualOverrideModel(TaxActualOverrideDto dto) {
        if (dto == null) return null;
        return new TaxActualOverrideModel(dto.year(), dto.amount());
    }

    private TaxActualOverrideDto toTaxActualOverrideDto(TaxActualOverrideModel m) {
        if (m == null) return null;
        return new TaxActualOverrideDto(m.year(), m.amount());
    }

    private OneOffExpenseModel toOneOffExpenseModel(OneOffExpenseDto dto) {
        if (dto == null) return null;
        return new OneOffExpenseModel(dto.id(), dto.label(), dto.date(), dto.amount(), dto.notes());
    }

    private OneOffExpenseDto toOneOffExpenseDto(OneOffExpenseModel m) {
        if (m == null) return null;
        return new OneOffExpenseDto(m.id(), m.label(), m.date(), m.amount(), m.notes());
    }

    private TransferModel toTransferModel(TransferDto dto) {
        if (dto == null) return null;
        return new TransferModel(dto.id(), dto.placement(), dto.date(), dto.amount(), dto.notes());
    }

    private TransferDto toTransferDto(TransferModel m) {
        if (m == null) return null;
        return new TransferDto(m.id(), m.placement(), m.date(), m.amount(), m.notes());
    }

    private VariableIncomeModel toVariableIncomeModel(VariableIncomeDto dto) {
        if (dto == null) return null;
        return new VariableIncomeModel(dto.id(), dto.label(), dto.refIncomeLabel(), dto.rate(), dto.startYear(), dto.endYear(), dto.taxable());
    }

    private VariableIncomeDto toVariableIncomeDto(VariableIncomeModel m) {
        if (m == null) return null;
        return new VariableIncomeDto(m.id(), m.label(), m.refIncomeLabel(), m.rate(), m.startYear(), m.endYear(), m.taxable());
    }

    private VariableOverrideModel toVariableOverrideModel(VariableOverrideDto dto) {
        if (dto == null) return null;
        return new VariableOverrideModel(dto.id(), dto.label(), dto.year(), dto.amount(), dto.taxable());
    }

    private VariableOverrideDto toVariableOverrideDto(VariableOverrideModel m) {
        if (m == null) return null;
        return new VariableOverrideDto(m.id(), m.label(), m.year(), m.amount(), m.taxable());
    }

    private BankImportModel toBankImportModel(BankImportDto dto) {
        if (dto == null) return null;
        List<BankImportModel.BankTransactionModel> txs = dto.transactions() != null
            ? dto.transactions().stream().map(t -> new BankImportModel.BankTransactionModel(t.id(), t.date(), t.label(), t.amount())).collect(Collectors.toList())
            : List.of();
        return new BankImportModel(txs);
    }

    private BankImportDto toBankImportDto(BankImportModel m) {
        if (m == null) return null;
        List<BankImportDto.BankTransactionDto> txs = m.transactions() != null
            ? m.transactions().stream().map(t -> new BankImportDto.BankTransactionDto(t.id(), t.date(), t.label(), t.amount())).collect(Collectors.toList())
            : List.of();
        return new BankImportDto(txs);
    }
}
