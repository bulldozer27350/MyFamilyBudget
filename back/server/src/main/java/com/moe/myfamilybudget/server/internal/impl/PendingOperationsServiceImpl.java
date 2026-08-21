package com.moe.myfamilybudget.server.internal.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OperationsEnCoursApi;
import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.AutoMatchResultModel;
import com.moe.myfamilybudget.server.internal.model.BankImportCalculator;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI OperationsEnCoursApi (Tag: Operations en cours).
 */
@Service
@RestController
public class PendingOperationsServiceImpl implements OperationsEnCoursApi {

    private final PersistenceManager persistenceManager;
    private final StatementBankImportMapper mapper;

    public PendingOperationsServiceImpl(PersistenceManager persistenceManager, StatementBankImportMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------------------
    // TAG: OPERATIONS EN COURS (3 méthodes définies dans OpenAPI)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> getPendingOperations() {
        BankImportModel current = persistenceManager.getBankImport();
        List<Map<String, Object>> opsList = current.pendingOperations().stream()
                .map(mapper::toPendingOperationMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("pendingOperations", opsList));
    }

    @Override
    public ResponseEntity<Void> reconcilePendingOperations(Object body) {
        BankImportModel current = persistenceManager.getBankImport();
        AutoMatchResultModel matchResult = BankImportCalculator.autoMatchPendingOperations(
                current.pendingOperations(), current.transactions()
        );

        if (matchResult.matchCount() > 0) {
            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), matchResult.updatedOperations(), current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> ignorePendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        String targetId = String.valueOf(map.getOrDefault("id", map.get("operationId")));

        if (targetId != null && !targetId.isBlank() && !"null".equalsIgnoreCase(targetId)) {
            BankImportModel current = persistenceManager.getBankImport();
            List<BankImportModel.PendingOperationModel> updatedOps = current.pendingOperations().stream()
                    .map(op -> op.id().equals(targetId)
                            ? new BankImportModel.PendingOperationModel(
                            op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                            op.label(), op.amount(), op.categoryId(), "ignored", op.linkedTxId(),
                            op.clearedDate(), op.notes())
                            : op)
                    .collect(Collectors.toList());

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedOps, current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

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
