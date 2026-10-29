package com.moe.myfamilybudget.application.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OperationsEnCoursApi;
import com.moe.myfamilybudget.api.model.ReconcilePendingOperations200Response;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankPointageUseCase;

/**
 * Contrôleur REST implémentant le contrat OpenAPI OperationsEnCoursApi (Tag: Operations en cours).
 *
 * <p>R-83 (même gabarit que {@code TresorerieServiceImpl}, R-82, SILO-314) : ce contrôleur ne garde que
 * l'extraction des corps de requête libres ({@code Object}) en {@code Map}/listes et la conversion vers le
 * monde HTTP ({@link ResponseEntity}). La composition de la réponse et l'écriture sont portées par le cas
 * d'usage {@link BankPointageUseCase} ({@code application-api}), implémenté par
 * {@code DefaultBankPointageUseCase}.
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à {@code PersistenceManager}.
 */
@Service
@RestController
public class PendingOperationsServiceImpl implements OperationsEnCoursApi {

    private final BankPointageUseCase bankPointageUseCase;

    public PendingOperationsServiceImpl(BankPointageUseCase bankPointageUseCase) {
        this.bankPointageUseCase = bankPointageUseCase;
    }

    // ---------------------------------------------------------------------------
    // TAG: OPERATIONS EN COURS (3 méthodes définies dans OpenAPI)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> getPendingOperations() {
        return ResponseEntity.ok(bankPointageUseCase.getPendingOperations());
    }

    @Override
    public ResponseEntity<ReconcilePendingOperations200Response> reconcilePendingOperations(Object body) {
        Map<String, Object> matchResult = bankPointageUseCase.reconcilePendingOperations();

        ReconcilePendingOperations200Response response = new ReconcilePendingOperations200Response();
        response.setMatchCount((Integer) matchResult.get("matchCount"));
        response.setNeedsReviewCount((Integer) matchResult.get("needsReviewCount"));
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> ignorePendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        String targetId = String.valueOf(map.getOrDefault("id", map.get("operationId")));
        bankPointageUseCase.ignorePendingOperation(targetId);
        return ResponseEntity.ok().build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> importPendingCB(Object body) {
        Map<String, Object> map = toMap(body);
        List<List<String>> rawRows = new ArrayList<>();
        if (map.get("rawRows") instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof List<?> rowList) {
                    rawRows.add(rowList.stream().map(c -> c != null ? c.toString() : "").collect(Collectors.toList()));
                }
            }
        }

        List<String> colRoles = new ArrayList<>();
        if (map.get("colRoles") instanceof List<?> rolesList) {
            for (Object role : rolesList) {
                colRoles.add(role != null ? role.toString() : "ignore");
            }
        }

        Map<String, Object> configMap = map.get("config") instanceof Map<?, ?> cMap ? (Map<String, Object>) cMap : Collections.emptyMap();
        String dateFormat = String.valueOf(configMap.getOrDefault("dateFormat", map.getOrDefault("dateFormat", "DD-MM-YYYY")));
        boolean usePurchaseDate = Boolean.parseBoolean(String.valueOf(configMap.getOrDefault("usePurchaseDate", map.getOrDefault("usePurchaseDate", false))));

        Map<String, Object> summary = bankPointageUseCase.importPendingCB(rawRows, colRoles, dateFormat, usePurchaseDate);
        return ResponseEntity.ok(summary);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> mergePendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        String manualOpId = String.valueOf(map.getOrDefault("manualOpId", map.getOrDefault("id", "")));
        Map<String, Object> bankOpMap = map.get("bankOp") instanceof Map<?, ?> m ? (Map<String, Object>) m : map;

        bankPointageUseCase.mergePendingOperation(manualOpId, bankOpMap);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> forceImportPendingOperation(Object body) {
        bankPointageUseCase.forceImportPendingOperation(toMap(body));
        return ResponseEntity.ok().build();
    }

    // --- Utility ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }
}
