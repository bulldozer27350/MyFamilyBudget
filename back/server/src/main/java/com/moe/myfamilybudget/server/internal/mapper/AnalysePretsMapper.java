package com.moe.myfamilybudget.server.internal.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.AnalysePretDto;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.api.model.AnalysePretsHypothesesDto;
import com.moe.myfamilybudget.api.model.PretRemboursementDto;
import com.moe.myfamilybudget.api.model.PretRenegociationDto;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.Assumptions;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.LoanItem;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RenegotiationAdvice;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel.RepaymentAdvice;

/**
 * Conversion du résultat de l'analyse des prêts vers les DTOs OpenAPI (tag AnalysePrets).
 */
@Component
public class AnalysePretsMapper {

    public AnalysePretsDto toDto(LoanAdviceResultModel model) {
        AnalysePretsDto dto = new AnalysePretsDto();
        dto.setMarketRateUsed(model.marketRateUsed());
        dto.setAssumptions(toDto(model.assumptions()));
        dto.setLoans(model.loans().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    private AnalysePretsHypothesesDto toDto(Assumptions a) {
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

    private AnalysePretDto toDto(LoanItem item) {
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

    private PretRemboursementDto toDto(RepaymentAdvice a) {
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

    private PretRenegociationDto toDto(RenegotiationAdvice a) {
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
}
