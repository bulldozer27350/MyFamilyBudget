package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SuggestionsTauxApi;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionService;
import com.moe.myfamilybudget.server.internal.factory.PlacementRateSuggestionInputFactory;
import com.moe.myfamilybudget.application.mapper.SuggestionsTauxMapper;
import com.moe.myfamilybudget.domain.market.calculation.MarketDataService;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Contrôleur REST implémentant le contrat OpenAPI SuggestionsTauxApi (Tag: SuggestionsTaux) :
 * façade mince : l'entrée est assemblée par PlacementRateSuggestionInputFactory, tout le calcul est
 * dans PlacementRateSuggestionService. Lecture seule : aucun
 * placement n'est modifié.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. Seuls les placements et les catégories d'actifs sont nécessaires,
 * déjà exposés par {@link PatrimoineReader} (RF-B00).
 */
@RestController
public class SuggestionsTauxServiceImpl implements SuggestionsTauxApi {

    private final PatrimoineReader patrimoineReader;
    private final MarketDataService marketDataService;
    private final PlacementRateSuggestionService suggestionService;
    private final SuggestionsTauxMapper mapper;

    public SuggestionsTauxServiceImpl(PatrimoineReader patrimoineReader, MarketDataService marketDataService,
            PlacementRateSuggestionService suggestionService, SuggestionsTauxMapper mapper) {
        this.patrimoineReader = patrimoineReader;
        this.marketDataService = marketDataService;
        this.suggestionService = suggestionService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SuggestionsTauxDto> getSuggestionsTaux(BigDecimal amplitude) {
        return ResponseEntity.ok(mapper.toDto(suggestionService.compute(
                PlacementRateSuggestionInputFactory.from(patrimoineReader.getPlacements(),
                        patrimoineReader.getAssetCategories(), marketDataService.current(), amplitude,
                        LocalDate.now()))));
    }
}
