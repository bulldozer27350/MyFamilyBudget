package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.AnalysePretDto;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceCalculationService;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;
import com.moe.myfamilybudget.server.internal.model.LoanAdviceResultModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;

class AnalysePretsMapperTest {

    @Test
    @DisplayName("toDto() reprend verdicts, montants et hypothèses du résultat de calcul")
    void mapsResult() {
        LoanModel loan = new LoanModel("r", "Résidence", new BigDecimal("250000"), new BigDecimal("0.045"),
                new BigDecimal("1450"), new BigDecimal("50"), "2026-09-01", null);
        LoanAdviceResultModel result = new LoanAdviceCalculationService().compute(
                List.of(loan), List.of(), List.of(), LoanAdviceParameters.defaults(new BigDecimal("0.03")),
                LocalDate.of(2026, 9, 19));

        AnalysePretsDto dto = new AnalysePretsMapper().toDto(result);

        assertEquals(0, new BigDecimal("0.030000").compareTo(dto.getMarketRateUsed()));
        assertEquals("immobilier", dto.getAssumptions().getLoanType());
        assertEquals(1, dto.getLoans().size());
        AnalysePretDto item = dto.getLoans().get(0);
        assertEquals("Résidence", item.getLabel());
        assertEquals(295, item.getRemainingMonths());
        assertEquals("INCONNU", item.getRepayment().getVerdict());
        assertNull(item.getRepayment().getAlternativeNetYield());
        assertEquals("RENEGOCIER", item.getRenegotiation().getVerdict());
        assertEquals(36, item.getRenegotiation().getPaybackMonths());
        assertEquals(result.notes(), dto.getNotes());
    }
}
