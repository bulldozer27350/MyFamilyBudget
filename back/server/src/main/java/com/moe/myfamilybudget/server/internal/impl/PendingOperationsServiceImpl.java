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
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
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
        BudgetDataModel budgetData = persistenceManager.getBudgetData();
        Map<String, Object> responseMap = mapper.toPendingOperationsResponseMap(current, budgetData);
        return ResponseEntity.ok(responseMap);
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

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> importPendingCB(Object body) {
        Map<String, Object> map = toMap(body);
        List<List<String>> rawRows = new java.util.ArrayList<>();
        if (map.get("rawRows") instanceof List<?> list) {
            for (Object r : list) {
                if (r instanceof List<?> rowList) {
                    rawRows.add(rowList.stream().map(c -> c != null ? c.toString() : "").collect(Collectors.toList()));
                }
            }
        }

        List<String> colRoles = new java.util.ArrayList<>();
        if (map.get("colRoles") instanceof List<?> rolesList) {
            for (Object role : rolesList) {
                colRoles.add(role != null ? role.toString() : "ignore");
            }
        }

        Map<String, Object> configMap = map.get("config") instanceof Map<?, ?> cMap ? (Map<String, Object>) cMap : Collections.emptyMap();
        String dateFormat = String.valueOf(configMap.getOrDefault("dateFormat", map.getOrDefault("dateFormat", "DD-MM-YYYY")));
        boolean usePurchaseDate = Boolean.parseBoolean(String.valueOf(configMap.getOrDefault("usePurchaseDate", map.getOrDefault("usePurchaseDate", false))));

        BankImportModel current = persistenceManager.getBankImport();
        com.moe.myfamilybudget.server.internal.model.PendingImportSummaryModel summary = BankImportCalculator.importPendingCB(
                rawRows,
                colRoles,
                dateFormat,
                usePurchaseDate,
                current.pendingOperations(),
                current.rules()
        );

        if (!summary.newOperations().isEmpty()) {
            List<BankImportModel.PendingOperationModel> allPending = new java.util.ArrayList<>(current.pendingOperations());
            allPending.addAll(summary.newOperations());
            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), allPending, current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

        return ResponseEntity.ok(mapper.toPendingImportSummaryMap(summary));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> mergePendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        String manualOpId = String.valueOf(map.getOrDefault("manualOpId", map.getOrDefault("id", "")));
        Map<String, Object> bankOpMap = map.get("bankOp") instanceof Map<?, ?> m ? (Map<String, Object>) m : map;
        BankImportModel.PendingOperationModel bankOp = mapper.toPendingOperationModel(bankOpMap);

        if (manualOpId != null && !manualOpId.isBlank()) {
            BankImportModel current = persistenceManager.getBankImport();
            List<BankImportModel.PendingOperationModel> mergedList = BankImportCalculator.mergePendingOperation(
                    manualOpId,
                    bankOp,
                    current.pendingOperations()
            );

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), mergedList, current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> forceImportPendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        BankImportModel.PendingOperationModel op = mapper.toPendingOperationModel(map);

        if (op != null) {
            BankImportModel current = persistenceManager.getBankImport();
            List<BankImportModel.PendingOperationModel> rulesApplied = BankImportCalculator.applyRulesToPendingOperations(
                    List.of(op), current.rules()
            );
            BankImportModel.PendingOperationModel opToAdd = !rulesApplied.isEmpty() ? rulesApplied.get(0) : op;

            List<BankImportModel.PendingOperationModel> updatedList = new java.util.ArrayList<>(current.pendingOperations());
            // Filter out any existing with same ID if any
            updatedList.removeIf(existing -> existing.id().equals(opToAdd.id()));
            updatedList.add(opToAdd);

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedList, current.matchings()
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
