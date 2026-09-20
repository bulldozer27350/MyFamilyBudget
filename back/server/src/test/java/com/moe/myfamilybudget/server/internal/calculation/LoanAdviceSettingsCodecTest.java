package com.moe.myfamilybudget.server.internal.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LoanAdviceSettingsCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Aller-retour JSON sans perte de précision, avec et sans taux de marché")
    void roundTrip() {
        LoanAdviceParameters withMarket = new LoanAdviceParameters(new BigDecimal("0.032"), new BigDecimal("0.005"),
                new BigDecimal("0.007"), new BigDecimal("70000"), 84, new BigDecimal("1500.50"), new BigDecimal("0.172"));
        LoanAdviceParameters withoutMarket = LoanAdviceParameters.defaults(null);

        assertEquals(withMarket, LoanAdviceSettingsCodec.fromJson(LoanAdviceSettingsCodec.toJson(withMarket, mapper), mapper));
        assertNull(LoanAdviceSettingsCodec.fromJson(LoanAdviceSettingsCodec.toJson(withoutMarket, mapper), mapper).marketRate());
    }

    @Test
    @DisplayName("fromJson() lève IllegalStateException sur un contenu illisible ou incomplet")
    void invalid() {
        assertThrows(IllegalStateException.class, () -> LoanAdviceSettingsCodec.fromJson("{ pas du json", mapper));
        assertThrows(IllegalStateException.class, () -> LoanAdviceSettingsCodec.fromJson("{}", mapper));
        assertThrows(IllegalStateException.class, () -> LoanAdviceSettingsCodec.fromJson(
                "{\"repayMarginRate\":\"abc\",\"renegotiationMinGapRate\":\"0.007\",\"renegotiationMinCrd\":\"70000\","
                        + "\"renegotiationMinRemainingMonths\":84,\"renegotiationFixedCosts\":\"1500\",\"flatTaxRate\":\"0.3\"}",
                mapper));
    }
}
