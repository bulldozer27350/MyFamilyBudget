package com.moe.myfamilybudget.application.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.api.model.SuggestionTauxPlacementDto;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionItemModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionKind;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionsModel;
import com.moe.myfamilybudget.web.mapper.SuggestionsTauxMapper;

class SuggestionsTauxMapperTest {

    @Test
    @DisplayName("toDto() reprend genre, taux proposés, taux actuels et explications du calcul")
    void mapsResult() {
        RateSuggestionItemModel itemModel = new RateSuggestionItemModel(
                "p1", "Livret A", "cash", RateSuggestionKind.SUGGESTION,
                "Livret A",
                new BigDecimal("0.01"), new BigDecimal("0.017"), new BigDecimal("0.024"),
                null,
                new BigDecimal("0.01"), new BigDecimal("0.02"), new BigDecimal("0.03"),
                "Taux Livret A en vigueur", null);

        RateSuggestionsModel model = new RateSuggestionsModel(
                new BigDecimal("0.01"),
                List.of(itemModel),
                List.of("Note 1", "Note 2"));

        SuggestionsTauxDto dto = new SuggestionsTauxMapper().toDto(model);

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
