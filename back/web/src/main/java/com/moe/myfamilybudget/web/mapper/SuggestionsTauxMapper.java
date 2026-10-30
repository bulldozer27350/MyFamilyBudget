package com.moe.myfamilybudget.web.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.SuggestionTauxPlacementDto;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionItemModel;
import com.moe.myfamilybudget.application.usecase.loanmarket.RateSuggestionsModel;

/**
 * Conversion des suggestions de taux vers les DTOs OpenAPI (tag SuggestionsTaux) (R-90, SILO-320).
 */
@Component
public class SuggestionsTauxMapper {

    public SuggestionsTauxDto toDto(RateSuggestionsModel model) {
        SuggestionsTauxDto dto = new SuggestionsTauxDto();
        dto.setAmplitude(model.amplitude());
        dto.setSuggestions(model.suggestions().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    public SuggestionsTauxDto toDto(com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel model) {
        SuggestionsTauxDto dto = new SuggestionsTauxDto();
        dto.setAmplitude(model.amplitude());
        dto.setSuggestions(model.suggestions().stream().map(this::toDto).toList());
        dto.setNotes(List.copyOf(model.notes()));
        return dto;
    }

    private SuggestionTauxPlacementDto toDto(com.moe.myfamilybudget.application.model.PlacementRateSuggestionsModel.Item item) {
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

    private SuggestionTauxPlacementDto toDto(RateSuggestionItemModel item) {
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
