package com.moe.myfamilybudget.server.internal.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.TauxMarcheApi;
import com.moe.myfamilybudget.api.model.TauxMarcheDto;
import com.moe.myfamilybudget.server.internal.mapper.TauxMarcheMapper;
import com.moe.myfamilybudget.server.internal.marketdata.MarketDataService;

/**
 * Contrôleur REST implémentant le contrat OpenAPI TauxMarcheApi (Tag: TauxMarche) : expose les
 * taux publics utilisés comme aide à la saisie. Lecture seule vis-à-vis du budget de
 * l'utilisateur, aucune de ces données n'est appliquée automatiquement.
 */
@RestController
public class TauxMarcheServiceImpl implements TauxMarcheApi {

    private final MarketDataService marketDataService;
    private final TauxMarcheMapper mapper;

    public TauxMarcheServiceImpl(MarketDataService marketDataService, TauxMarcheMapper mapper) {
        this.marketDataService = marketDataService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<TauxMarcheDto> getTauxMarche() {
        return ResponseEntity.ok(mapper.toDto(marketDataService.current()));
    }

    @Override
    public ResponseEntity<TauxMarcheDto> refreshTauxMarche() {
        return ResponseEntity.ok(mapper.toDto(marketDataService.refresh()));
    }
}
