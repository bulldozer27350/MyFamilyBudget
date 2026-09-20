package com.moe.myfamilybudget.server.internal.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.SuggestionTauxPlacementDto;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionService;
import com.moe.myfamilybudget.server.internal.marketdata.MarketRatesView;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRateFreshness.Status;
import com.moe.myfamilybudget.server.internal.marketdata.RegulatedRatesQuote;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

class SuggestionsTauxMapperTest {

    @Test
    @DisplayName("toDto() reprend genre, taux proposés, taux actuels et explications du calcul")
    void mapsResult() {
        PlacementModel livret = new PlacementModel("p1", "Livret A", "Livrets", new BigDecimal("1000"), "2026-09-01",
                BigDecimal.ZERO, null, null, new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"), false, "");
        MarketRatesView market = new MarketRatesView(Instant.parse("2026-09-19T08:00:00Z"),
                new RegulatedRatesQuote(YearMonth.of(2026, 8), new BigDecimal("0.017"), new BigDecimal("0.017"), new BigDecimal("0.022")),
                Status.FRESH, LocalDate.of(2026, 8, 1), null, Status.UNAVAILABLE, false, null, Status.UNAVAILABLE, null);

        SuggestionsTauxDto dto = new SuggestionsTauxMapper().toDto(new PlacementRateSuggestionService().compute(
                List.of(livret), List.of(new AssetCategoryModel("c1", "💶", "Livrets", "cash")), market, null,
                LocalDate.of(2026, 9, 19)));

        assertEquals(0, new BigDecimal("0.01").compareTo(dto.getAmplitude()));
        assertEquals(2, dto.getNotes().size());
        SuggestionTauxPlacementDto item = dto.getSuggestions().get(0);
        assertEquals("p1", item.getPlacementId());
        assertEquals("SUGGESTION", item.getKind());
        assertEquals("cash", item.getBucket());
        assertEquals("Livret A", item.getBenchmark());
        assertEquals(0, new BigDecimal("0.017").compareTo(item.getSuggestedCorr()));
        assertEquals(0, new BigDecimal("0.02").compareTo(item.getCurrentCorr()));
        assertNull(item.getReferenceRate());
        assertNull(item.getCaveat());
    }
}
