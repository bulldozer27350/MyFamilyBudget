package com.moe.myfamilybudget.application.service;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.RetraiteApi;
import com.moe.myfamilybudget.application.command.RetirementCommandService;
import com.moe.myfamilybudget.application.mapper.RetraiteMapper;
import com.moe.myfamilybudget.application.usecase.retirement.RetraiteResultModel;
import com.moe.myfamilybudget.application.usecase.retirement.RetirementUseCase;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;

/**
 * Point d'entrée REST du domaine Retraite.
 *
 * <p>SILO-300 (lot B, pilote Retraite) : ce service ne garde que la conversion vers le monde HTTP
 * ({@link ResponseEntity}, carte JSON du contrat). La composition de la réponse (lectures des fragments,
 * construction de l'entrée du moteur, appel du moteur, traduction des types des silos) est portée par le cas
 * d'usage {@link RetirementUseCase} ({@code application-api}), implémenté par
 * {@code DefaultRetirementUseCase}. L'écriture passe toujours par {@link RetirementCommandService}.
 *
 * <p>RF-101 (doc/architecture/03-domaine-retraite.md) : le moteur de calcul pur reste l'unique version
 * canonique de la projection retraite. RF-B01 : plus d'appel direct à {@code PersistenceManager}.
 */
@RestController
public class RetraiteServiceImpl implements RetraiteApi {

    private final RetraiteMapper retraiteMapper;
    private final RetirementUseCase retirementUseCase;
    private final RetirementCommandService retirementCommandService;

    public RetraiteServiceImpl(
        RetraiteMapper retraiteMapper,
        RetirementUseCase retirementUseCase,
        RetirementCommandService retirementCommandService
    ) {
        this.retraiteMapper = retraiteMapper;
        this.retirementUseCase = retirementUseCase;
        this.retirementCommandService = retirementCommandService;
    }

    @Override
    public ResponseEntity<Object> getRetraite() {
        RetraiteResultModel resultModel = retirementUseCase.getRetraite();
        Map<String, Object> response = retraiteMapper.toResponseMap(resultModel);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> saveRetraite(Object body) {
        if (body instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            RetirementModel model = retraiteMapper.toRetirementModelFromMap(typedMap);
            retirementCommandService.updateRetirement(model);
        }
        return ResponseEntity.ok().build();
    }
}
