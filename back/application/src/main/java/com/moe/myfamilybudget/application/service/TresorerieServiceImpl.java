package com.moe.myfamilybudget.application.service;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.TresorerieApi;
import com.moe.myfamilybudget.api.model.TresorerieAjustementRequestDto;
import com.moe.myfamilybudget.api.model.TresorerieResponseDto;
import com.moe.myfamilybudget.application.mapper.TresorerieMapper;
import com.moe.myfamilybudget.application.usecase.treasury.TresorerieResultModel;
import com.moe.myfamilybudget.application.usecase.treasury.TreasuryUseCase;

/**
 * Contrôleur REST de la trésorerie prévisionnelle (Trésorerie).
 *
 * <p>R-82 (même gabarit que {@code ImpotsServiceImpl}, {@code PatrimoineServiceImpl}, SILO-313) : ce
 * service ne garde que la conversion vers le monde HTTP ({@link ResponseEntity}, DTO). La composition
 * de la réponse (lectures des fragments, construction de l'entrée du moteur, appel du moteur, moyennes
 * réelles, suggestions, traduction des types des silos) est portée par le cas d'usage
 * {@link TreasuryUseCase} ({@code application-api}), implémenté par {@code DefaultTreasuryUseCase}.
 * L'interprétation du {@code listKey}/{@code field}/{@code kind} (DB-050) est également déplacée dans
 * le cas d'usage ; ce service ne garde que la décision d'appeler ou non (corps de requête absent).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}.
 */
@RestController
public class TresorerieServiceImpl implements TresorerieApi {

    private final TresorerieMapper mapper;
    private final TreasuryUseCase treasuryUseCase;

    public TresorerieServiceImpl(TresorerieMapper mapper, TreasuryUseCase treasuryUseCase) {
        this.mapper = mapper;
        this.treasuryUseCase = treasuryUseCase;
    }

    @Override
    public ResponseEntity<TresorerieResponseDto> getTresorerie(Boolean useConstantEuros) {
        TresorerieResultModel result = treasuryUseCase.getTresorerie(Boolean.TRUE.equals(useConstantEuros));
        return ResponseEntity.ok(this.mapper.toTresorerieResponseDto(result));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> addTresorerieLigne(String listKey, Object body) {
        Map<String, Object> created = this.treasuryUseCase.addTresorerieLigne(listKey, (Map<String, Object>) body);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<Void> updateTresorerieLigne(String listKey, String id, com.moe.myfamilybudget.api.model.UpdateTresorerieLigneRequestDto body) {
        if (body != null) {
            this.treasuryUseCase.updateTresorerieLigne(listKey, id, body.getField(), body.getValue());
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeTresorerieLigne(String listKey, String id) {
        this.treasuryUseCase.removeTresorerieLigne(listKey, id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> applyTresorerieAjustement(TresorerieAjustementRequestDto request) {
        if (request != null && request.getLineId() != null && request.getKind() != null && request.getNewMonthly() != null) {
            BigDecimal newMonthly = BigDecimal.valueOf(request.getNewMonthly().doubleValue());
            this.treasuryUseCase.applyTresorerieAjustement(request.getLineId(), request.getKind(), newMonthly);
        }
        return ResponseEntity.ok().build();
    }
}
