package com.moe.myfamilybudget.web.service;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.AnalysePretsApi;
import com.moe.myfamilybudget.api.model.AnalysePretsDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresDto;
import com.moe.myfamilybudget.api.model.AnalysePretsParametresValuesDto;
import com.moe.myfamilybudget.web.mapper.AnalysePretsMapper;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanAdviceParametersSaveCommand;
import com.moe.myfamilybudget.application.usecase.loanmarket.LoanMarketUseCase;

/**
 * Contrôleur REST implémentant le contrat OpenAPI AnalysePretsApi (Tag: AnalysePrets) : façade
 * mince, tout le calcul est dans {@link LoanMarketUseCase} (R-86, SILO-317, même gabarit que
 * {@code AnalyseServiceImpl}/{@code TresorerieServiceImpl}).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}.
 *
 * <p>La logique de composition (lecture des prêts, des placements, résolution du taux de marché,
 * appel du moteur d'analyse, traduction des types) est portée par {@link LoanMarketUseCase}
 * ({@code application-api}), implémenté par {@code DefaultLoanMarketUseCase}. Ce contrôleur ne
 * garde que la conversion HTTP ({@link ResponseEntity}, DTO).
 */
@RestController
public class AnalysePretsServiceImpl implements AnalysePretsApi {

    private final LoanMarketUseCase loanMarketUseCase;
    private final AnalysePretsMapper mapper;

    public AnalysePretsServiceImpl(LoanMarketUseCase loanMarketUseCase, AnalysePretsMapper mapper) {
        this.loanMarketUseCase = loanMarketUseCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<AnalysePretsDto> getAnalysePrets(BigDecimal marketRate) {
        return ResponseEntity.ok(mapper.toDto(loanMarketUseCase.getAnalysePrets(marketRate)));
    }

    @Override
    public ResponseEntity<AnalysePretsParametresDto> getAnalysePretsParametres() {
        return ResponseEntity.ok(mapper.toParametresDto(loanMarketUseCase.getAnalysePretsParametres()));
    }

    @Override
    public ResponseEntity<AnalysePretsParametresDto> updateAnalysePretsParametres(
            AnalysePretsParametresValuesDto body) {
        LoanAdviceParametersSaveCommand command = mapper.toSaveCommand(body);
        return ResponseEntity.ok(mapper.toParametresDto(loanMarketUseCase.updateAnalysePretsParametres(command)));
    }

    /**
     * Informations de résolution du taux de marché, conservées pour les tests existants de
     * {@code resolveMarketRate} (via la même logique dans {@code DefaultLoanMarketUseCase}).
     */
    record ResolvedMarketRate(java.math.BigDecimal rate, String source) {}
}
