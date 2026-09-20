package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.AnalysePretsParametresDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresValuesDto;
import com.moe.myfamilybudget.server.internal.calculation.LoanAdviceParameters;

class AnalysePretsParametresMapperTest {

    private final AnalysePretsMapper mapper = new AnalysePretsMapper();

    @Test
    @DisplayName("toParametresDto() expose les valeurs en vigueur et les valeurs par défaut")
    void mapsCurrentAndDefaults() {
        LoanAdviceParameters current = new LoanAdviceParameters(new BigDecimal("0.032"), new BigDecimal("0.01"),
                new BigDecimal("0.01"), new BigDecimal("50000"), 60, new BigDecimal("2000"), new BigDecimal("0.172"));

        AnalysePretsParametresDto dto = mapper.toParametresDto(current, LoanAdviceParameters.defaults(null));

        assertEquals(0, new BigDecimal("0.032").compareTo(dto.getValues().getMarketRate()));
        assertEquals(60, dto.getValues().getRenegotiationMinRemainingMonths());
        assertNull(dto.getDefaults().getMarketRate());
        assertEquals(84, dto.getDefaults().getRenegotiationMinRemainingMonths());
        assertEquals(0, new BigDecimal("0.007").compareTo(dto.getDefaults().getRenegotiationMinGapRate()));
    }

    @Test
    @DisplayName("toParameters(toValuesDto(p)) redonne les mêmes hypothèses")
    void roundTrip() {
        LoanAdviceParameters p = LoanAdviceParameters.defaults(new BigDecimal("0.03"));

        assertEquals(p, mapper.toParameters(mapper.toValuesDto(p)));
    }

    @Test
    @DisplayName("toParameters() refuse un champ obligatoire absent, mais accepte l'absence de taux de marché")
    void missingFields() {
        AnalysePretsParametresValuesDto dto = mapper.toValuesDto(LoanAdviceParameters.defaults(null));
        assertNull(mapper.toParameters(dto).marketRate());

        dto.setFlatTaxRate(null);
        assertThrows(IllegalArgumentException.class, () -> mapper.toParameters(dto));
        assertThrows(IllegalArgumentException.class, () -> mapper.toParameters(null));

        AnalysePretsParametresValuesDto noMonths = mapper.toValuesDto(LoanAdviceParameters.defaults(null));
        noMonths.setRenegotiationMinRemainingMonths(null);
        assertThrows(IllegalArgumentException.class, () -> mapper.toParameters(noMonths));
    }
}
