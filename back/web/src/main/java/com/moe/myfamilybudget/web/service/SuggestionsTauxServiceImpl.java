package com.moe.myfamilybudget.web.service;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.SuggestionsTauxApi;
import com.moe.myfamilybudget.api.model.SuggestionsTauxDto;
import com.moe.myfamilybudget.web.mapper.SuggestionsTauxMapper;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanMarketUseCase;

/**
 * Contrôleur REST implémentant le contrat OpenAPI SuggestionsTauxApi (Tag: SuggestionsTaux) :
 * façade mince, tout le calcul est dans {@link LoanMarketUseCase} (R-86, SILO-317, même gabarit
 * que {@code AnalysePretsServiceImpl}).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}.
 *
 * <p>La logique de composition (assemblage de l'entrée du moteur via
 * {@code PlacementRateSuggestionInputFactory}, appel de {@code PlacementRateSuggestionService},
 * traduction des types vers les modèles de {@code application-api}) est portée par
 * {@link LoanMarketUseCase}. Ce contrôleur ne garde que la conversion HTTP. Lecture seule :
 * aucun placement n'est modifié.
 */
@RestController
public class SuggestionsTauxServiceImpl implements SuggestionsTauxApi {

    private final LoanMarketUseCase loanMarketUseCase;
    private final SuggestionsTauxMapper mapper;

    public SuggestionsTauxServiceImpl(LoanMarketUseCase loanMarketUseCase, SuggestionsTauxMapper mapper) {
        this.loanMarketUseCase = loanMarketUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<SuggestionsTauxDto> getSuggestionsTaux(BigDecimal amplitude) {
        return ResponseEntity.ok(mapper.toDto(loanMarketUseCase.getSuggestionsTaux(amplitude)));
    }
}
