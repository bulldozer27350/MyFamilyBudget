package com.moe.myfamilybudget.web.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.AnalysePretDto;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.api.model.AnalysePretsHypothesesDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresValuesDto;
import com.moe.myfamilybudget.api.model.PretRemboursementDto;
import com.moe.myfamilybudget.api.model.PretRenegociationDto;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersValuesModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceResultModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAssumptionsModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanItemModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRenegotiationAdviceModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanRepaymentAdviceModel;

/**
 * Conversion du résultat de l'analyse des prêts vers les DTOs OpenAPI (tag AnalysePrets) (R-90, SILO-320).
 */
@Component
public class AnalysePretsMapper {

    public AnalysePretsDto toDto(LoanAdviceResultModel model) {
        AnalysePretsDto dto = new AnalysePretsDto();
        dto.setMarketRateUsed(model.marketRateUsed());
        dto.setMarketRateSource(model.marketRateSource());
        dto.setAssumptions(toDto(model.assumptions()));
        dto.setLoans(model.loans().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    public AnalysePretsDto toDto(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel model) {
        AnalysePretsDto dto = new AnalysePretsDto();
        dto.setMarketRateUsed(model.marketRateUsed());
        dto.setMarketRateSource(model.marketRateSource());
        dto.setAssumptions(toDto(model.assumptions()));
        dto.setLoans(model.loans().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    private AnalysePretsHypothesesDto toDto(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.Assumptions a) {
        AnalysePretsHypothesesDto dto = new AnalysePretsHypothesesDto();
        dto.setLoanType(a.loanType());
        dto.setRepayMarginRate(a.repayMarginRate());
        dto.setRenegotiationMinGapRate(a.renegotiationMinGapRate());
        dto.setRenegotiationMinCrd(a.renegotiationMinCrd());
        dto.setRenegotiationMinRemainingMonths(a.renegotiationMinRemainingMonths());
        dto.setRenegotiationFixedCosts(a.renegotiationFixedCosts());
        dto.setFlatTaxRate(a.flatTaxRate());
        return dto;
    }

    private AnalysePretDto toDto(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.LoanItem item) {
        AnalysePretDto dto = new AnalysePretDto();
        dto.setId(item.id());
        dto.setLabel(item.label());
        dto.setCrd(item.crd());
        dto.setRate(item.rate());
        dto.setMonthly(item.monthly());
        dto.setInsurance(item.insurance());
        dto.setRemainingMonths(item.remainingMonths());
        dto.setRemainingInterest(item.remainingInterest());
        dto.setEarlyRepaymentIndemnity(item.earlyRepaymentIndemnity());
        dto.setRepayment(toDto(item.repayment()));
        dto.setRenegotiation(toDto(item.renegotiation()));
        return dto;
    }

    private PretRemboursementDto toDto(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.RepaymentAdvice a) {
        PretRemboursementDto dto = new PretRemboursementDto();
        dto.setVerdict(a.verdict().name());
        dto.setLoanEffectiveCost(a.loanEffectiveCost());
        dto.setAlternativeNetYield(a.alternativeNetYield());
        dto.setAlternativeLabel(a.alternativeLabel());
        dto.setAnnualSaving(a.annualSaving());
        dto.setIndemnityPaybackMonths(a.indemnityPaybackMonths());
        dto.setReason(a.reason());
        return dto;
    }

    private PretRenegociationDto toDto(com.moe.myfamilybudget.domain.credit.model.LoanAdviceResultModel.RenegotiationAdvice a) {
        PretRenegociationDto dto = new PretRenegociationDto();
        dto.setVerdict(a.verdict().name());
        dto.setGapRate(a.gapRate());
        dto.setNewMonthly(a.newMonthly());
        dto.setMonthlyGain(a.monthlyGain());
        dto.setGrossSaving(a.grossSaving());
        dto.setCosts(a.costs());
        dto.setNetSaving(a.netSaving());
        dto.setPaybackMonths(a.paybackMonths());
        dto.setReason(a.reason());
        return dto;
    }

    private AnalysePretsHypothesesDto toDto(LoanAssumptionsModel a) {
        AnalysePretsHypothesesDto dto = new AnalysePretsHypothesesDto();
        dto.setLoanType(a.loanType());
        dto.setRepayMarginRate(a.repayMarginRate());
        dto.setRenegotiationMinGapRate(a.renegotiationMinGapRate());
        dto.setRenegotiationMinCrd(a.renegotiationMinCrd());
        dto.setRenegotiationMinRemainingMonths(a.renegotiationMinRemainingMonths());
        dto.setRenegotiationFixedCosts(a.renegotiationFixedCosts());
        dto.setFlatTaxRate(a.flatTaxRate());
        return dto;
    }

    private AnalysePretDto toDto(LoanItemModel item) {
        AnalysePretDto dto = new AnalysePretDto();
        dto.setId(item.id());
        dto.setLabel(item.label());
        dto.setCrd(item.crd());
        dto.setRate(item.rate());
        dto.setMonthly(item.monthly());
        dto.setInsurance(item.insurance());
        dto.setRemainingMonths(item.remainingMonths());
        dto.setRemainingInterest(item.remainingInterest());
        dto.setEarlyRepaymentIndemnity(item.earlyRepaymentIndemnity());
        dto.setRepayment(toDto(item.repayment()));
        dto.setRenegotiation(toDto(item.renegotiation()));
        return dto;
    }

    private PretRemboursementDto toDto(LoanRepaymentAdviceModel a) {
        PretRemboursementDto dto = new PretRemboursementDto();
        dto.setVerdict(a.verdict().name());
        dto.setLoanEffectiveCost(a.loanEffectiveCost());
        dto.setAlternativeNetYield(a.alternativeNetYield());
        dto.setAlternativeLabel(a.alternativeLabel());
        dto.setAnnualSaving(a.annualSaving());
        dto.setIndemnityPaybackMonths(a.indemnityPaybackMonths());
        dto.setReason(a.reason());
        return dto;
    }

    private PretRenegociationDto toDto(LoanRenegotiationAdviceModel a) {
        PretRenegociationDto dto = new PretRenegociationDto();
        dto.setVerdict(a.verdict().name());
        dto.setGapRate(a.gapRate());
        dto.setNewMonthly(a.newMonthly());
        dto.setMonthlyGain(a.monthlyGain());
        dto.setGrossSaving(a.grossSaving());
        dto.setCosts(a.costs());
        dto.setNetSaving(a.netSaving());
        dto.setPaybackMonths(a.paybackMonths());
        dto.setReason(a.reason());
        return dto;
    }

    // ------------------------------------------------------------------ hypothèses modifiables

    public AnalysePretsParametresDto toParametresDto(LoanAdviceParametersModel model) {
        AnalysePretsParametresDto dto = new AnalysePretsParametresDto();
        dto.setValues(toValuesDto(model.values()));
        dto.setDefaults(toValuesDto(model.defaults()));
        return dto;
    }

    public AnalysePretsParametresDto toParametresDto(com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters current,
            com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters defaults) {
        AnalysePretsParametresDto dto = new AnalysePretsParametresDto();
        dto.setValues(toValuesDto(current));
        dto.setDefaults(toValuesDto(defaults));
        return dto;
    }

    public AnalysePretsParametresValuesDto toValuesDto(LoanAdviceParametersValuesModel p) {
        AnalysePretsParametresValuesDto dto = new AnalysePretsParametresValuesDto();
        dto.setMarketRate(p.marketRate());
        dto.setRepayMarginRate(p.repayMarginRate());
        dto.setRenegotiationMinGapRate(p.renegotiationMinGapRate());
        dto.setRenegotiationMinCrd(p.renegotiationMinCrd());
        dto.setRenegotiationMinRemainingMonths(p.renegotiationMinRemainingMonths());
        dto.setRenegotiationFixedCosts(p.renegotiationFixedCosts());
        dto.setFlatTaxRate(p.flatTaxRate());
        return dto;
    }

    public AnalysePretsParametresValuesDto toValuesDto(com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters p) {
        AnalysePretsParametresValuesDto dto = new AnalysePretsParametresValuesDto();
        dto.setMarketRate(p.marketRate());
        dto.setRepayMarginRate(p.repayMarginRate());
        dto.setRenegotiationMinGapRate(p.renegotiationMinGapRate());
        dto.setRenegotiationMinCrd(p.renegotiationMinCrd());
        dto.setRenegotiationMinRemainingMonths(p.renegotiationMinRemainingMonths());
        dto.setRenegotiationFixedCosts(p.renegotiationFixedCosts());
        dto.setFlatTaxRate(p.flatTaxRate());
        return dto;
    }

    /**
     * @throws IllegalArgumentException si un champ obligatoire est absent (traduit en 400)
     */
    public com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters toParameters(AnalysePretsParametresValuesDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Corps de requête manquant.");
        }
        if (dto.getRenegotiationMinRemainingMonths() == null) {
            throw new IllegalArgumentException("Champ obligatoire manquant : renegotiationMinRemainingMonths");
        }
        return new com.moe.myfamilybudget.domain.credit.calculation.LoanAdviceParameters(
                dto.getMarketRate(),
                required(dto.getRepayMarginRate(), "repayMarginRate"),
                required(dto.getRenegotiationMinGapRate(), "renegotiationMinGapRate"),
                required(dto.getRenegotiationMinCrd(), "renegotiationMinCrd"),
                dto.getRenegotiationMinRemainingMonths(),
                required(dto.getRenegotiationFixedCosts(), "renegotiationFixedCosts"),
                required(dto.getFlatTaxRate(), "flatTaxRate"));
    }

    /**
     * @throws IllegalArgumentException si un champ obligatoire est absent (traduit en 400)
     */
    public LoanAdviceParametersSaveCommand toSaveCommand(AnalysePretsParametresValuesDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Corps de requête manquant.");
        }
        if (dto.getRenegotiationMinRemainingMonths() == null) {
            throw new IllegalArgumentException("Champ obligatoire manquant : renegotiationMinRemainingMonths");
        }
        return new LoanAdviceParametersSaveCommand(
                dto.getMarketRate(),
                required(dto.getRepayMarginRate(), "repayMarginRate"),
                required(dto.getRenegotiationMinGapRate(), "renegotiationMinGapRate"),
                required(dto.getRenegotiationMinCrd(), "renegotiationMinCrd"),
                dto.getRenegotiationMinRemainingMonths(),
                required(dto.getRenegotiationFixedCosts(), "renegotiationFixedCosts"),
                required(dto.getFlatTaxRate(), "flatTaxRate"));
    }

    private static java.math.BigDecimal required(java.math.BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Champ obligatoire manquant : " + field);
        }
        return value;
    }
}
