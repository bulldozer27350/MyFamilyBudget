package com.moe.myfamilybudget.server.internal.impl;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SuggestionsTauxApi;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.server.internal.calculation.PlacementRateSuggestionService;
import com.moe.myfamilybudget.server.internal.mapper.SuggestionsTauxMapper;
import com.moe.myfamilybudget.server.internal.marketdata.MarketDataService;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Contrôleur REST implémentant le contrat OpenAPI SuggestionsTauxApi (Tag: SuggestionsTaux) :
 * façade mince, tout le calcul est dans PlacementRateSuggestionService. Lecture seule : aucun
 * placement n'est modifié.
 */
@RestController
public class SuggestionsTauxServiceImpl implements SuggestionsTauxApi {

    private final PersistenceManager persistenceManager;
    private final MarketDataService marketDataService;
    private final PlacementRateSuggestionService suggestionService;
    private final SuggestionsTauxMapper mapper;

    public SuggestionsTauxServiceImpl(PersistenceManager persistenceManager, MarketDataService marketDataService,
            PlacementRateSuggestionService suggestionService, SuggestionsTauxMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.marketDataService = marketDataService;
        this.suggestionService = suggestionService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SuggestionsTauxDto> getSuggestionsTaux(BigDecimal amplitude) {
        BudgetDataModel data = persistenceManager.getBudgetData();
        return ResponseEntity.ok(mapper.toDto(suggestionService.compute(
                data.getEffectivePlacements(),
                data.getEffectiveAssetCategories(),
                marketDataService.current(),
                amplitude,
                LocalDate.now())));
    }
}
