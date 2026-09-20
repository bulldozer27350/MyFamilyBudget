package com.moe.myfamilybudget.server.internal.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.SuggestionTauxPlacementDto;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel;
import com.moe.myfamilybudget.server.internal.model.PlacementRateSuggestionsModel.Item;

/**
 * Conversion des suggestions de taux vers les DTOs OpenAPI (tag SuggestionsTaux).
 */
@Component
public class SuggestionsTauxMapper {

    public SuggestionsTauxDto toDto(PlacementRateSuggestionsModel model) {
        SuggestionsTauxDto dto = new SuggestionsTauxDto();
        dto.setAmplitude(model.amplitude());
        dto.setSuggestions(model.suggestions().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    private SuggestionTauxPlacementDto toDto(Item item) {
        SuggestionTauxPlacementDto dto = new SuggestionTauxPlacementDto();
        dto.setPlacementId(item.placementId());
        dto.setLabel(item.label());
        dto.setBucket(item.bucket());
        dto.setKind(item.kind().name());
        dto.setBenchmark(item.benchmark());
        dto.setSuggestedPess(item.suggestedPess());
        dto.setSuggestedCorr(item.suggestedCorr());
        dto.setSuggestedOpti(item.suggestedOpti());
        dto.setReferenceRate(item.referenceRate());
        dto.setCurrentPess(item.currentPess());
        dto.setCurrentCorr(item.currentCorr());
        dto.setCurrentOpti(item.currentOpti());
        dto.setBasis(item.basis());
        dto.setCaveat(item.caveat());
        return dto;
    }
}
