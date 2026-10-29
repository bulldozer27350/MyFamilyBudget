package com.moe.myfamilybudget.application.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.PointageApi;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankPointageUseCase;

/**
 * Contrôleur REST implémentant le contrat OpenAPI PointageApi (Tag: Pointage).
 *
 * <p>R-83 (même gabarit que {@code TresorerieServiceImpl}, R-82, SILO-314) : ce contrôleur ne garde que la
 * conversion vers le monde HTTP ({@link ResponseEntity}). La composition de la réponse (lectures des
 * fragments, assemblage du Modèle Interne de Pointage) et l'écriture des rapprochements sont portées par le
 * cas d'usage {@link BankPointageUseCase} ({@code application-api}), implémenté par
 * {@code DefaultBankPointageUseCase} (y compris la vérification de {@code monthISO}, auparavant faite ici).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à {@code PersistenceManager}.
 */
@Service
@RestController
public class PointageServiceImpl implements PointageApi {

    private final BankPointageUseCase bankPointageUseCase;

    public PointageServiceImpl(BankPointageUseCase bankPointageUseCase) {
        this.bankPointageUseCase = bankPointageUseCase;
    }

    @Override
    public ResponseEntity<Object> getPointage() {
        return ResponseEntity.ok(bankPointageUseCase.getPointage());
    }

    @Override
    public ResponseEntity<Void> savePointageMatching(String monthISO, Object body) {
        bankPointageUseCase.savePointageMatching(monthISO, body);
        return ResponseEntity.ok().build();
    }
}
