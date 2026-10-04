package com.moe.myfamilybudget.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.api.controller.OperationsEnCoursApi;
import com.moe.myfamilybudget.api.model.ReconcilePendingOperations200Response;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.application.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.AutoMatchResultModel;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculator;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.transition.port.BudgetReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI OperationsEnCoursApi (Tag: Operations en cours).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}. SILO-114 : plus de {@code BudgetDataModel} ; lecture via {@link BankReader}
 * et {@link BudgetReader} (charges, revenus, ponctuels) ; {@link SettingsReader} n'est conservé que pour le
 * bloc {@code settings} de la réponse REST (contrat inchangé), retiré avec la composition applicative de ce
 * bloc. Écriture via {@link BankImportCommandService} (déjà en place depuis RF-A00).
 */
@Service
@RestController
public class PendingOperationsServiceImpl implements OperationsEnCoursApi {

    private final BankReader bankReader;
    private final BudgetReader budgetReader;
    private final SettingsReader settingsReader;
    private final BankImportCommandService bankImportCommandService;
    private final StatementBankImportMapper mapper;

    public PendingOperationsServiceImpl(
            BankReader bankReader,
            BudgetReader budgetReader,
            SettingsReader settingsReader,
            BankImportCommandService bankImportCommandService,
            StatementBankImportMapper mapper) {
        this.bankReader = bankReader;
        this.budgetReader = budgetReader;
        this.settingsReader = settingsReader;
        this.bankImportCommandService = bankImportCommandService;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------------------
    // TAG: OPERATIONS EN COURS (3 méthodes définies dans OpenAPI)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> getPendingOperations() {
        BankImportModel current = bankReader.getBankImport();
        Map<String, Object> responseMap = mapper.toPendingOperationsResponseMap(
                current,
                budgetReader.getCharges(),
                budgetReader.getIncomes(),
                budgetReader.getOneoffExpenses(),
                settingsReader.getSettings());
        return ResponseEntity.ok(responseMap);
    }

    @Override
    public ResponseEntity<ReconcilePendingOperations200Response> reconcilePendingOperations(Object body) {
        BankImportModel current = bankReader.getBankImport();
        AutoMatchResultModel matchResult = BankImportCalculator.autoMatchPendingOperations(
                current.pendingOperations(), current.transactions()
        );

        if (matchResult.matchCount() > 0) {
            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    matchResult.updatedTransactions(), matchResult.updatedOperations(), current.matchings()
            );
            bankImportCommandService.updateBankImport(updated);
        }

        ReconcilePendingOperations200Response response = new ReconcilePendingOperations200Response();
        response.setMatchCount(matchResult.matchCount());
        response.setNeedsReviewCount(matchResult.needsReviewCount());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> ignorePendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        String targetId = String.valueOf(map.getOrDefault("id", map.get("operationId")));

        if (targetId != null && !targetId.isBlank() && !"null".equalsIgnoreCase(targetId)) {
            BankImportModel current = bankReader.getBankImport();
            List<BankImportModel.PendingOperationModel> updatedOps = current.pendingOperations().stream()
                    .map(op -> op.id().equals(targetId)
                            ? new BankImportModel.PendingOperationModel(
                            op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                            op.label(), op.amount(), op.categoryId(), "ignored", op.linkedTxId(),
                            op.clearedDate(), op.notes(), op.splits())
                            : op)
                    .collect(Collectors.toList());

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedOps, current.matchings()
            );
            bankImportCommandService.updateBankImport(updated);
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

        BankImportModel current = bankReader.getBankImport();
        com.moe.myfamilybudget.domain.bankpointage.model.PendingImportSummaryModel summary = BankImportCalculator.importPendingCB(
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
            bankImportCommandService.updateBankImport(updated);
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
            BankImportModel current = bankReader.getBankImport();
            List<BankImportModel.PendingOperationModel> mergedList = BankImportCalculator.mergePendingOperation(
                    manualOpId,
                    bankOp,
                    current.pendingOperations()
            );

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), mergedList, current.matchings()
            );
            bankImportCommandService.updateBankImport(updated);
        }

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> forceImportPendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        BankImportModel.PendingOperationModel op = mapper.toPendingOperationModel(map);

        if (op != null) {
            BankImportModel current = bankReader.getBankImport();
            BankImportModel.PendingOperationModel resolvedOp = op;
            if ((op.categoryId() == null || op.categoryId().isBlank()) && (op.splits() == null || op.splits().isEmpty())) {
                List<BankImportModel.PendingOperationModel> rulesApplied = BankImportCalculator.applyRulesToPendingOperations(
                        List.of(op), current.rules()
                );
                if (!rulesApplied.isEmpty()) {
                    resolvedOp = rulesApplied.get(0);
                }
            }
            final BankImportModel.PendingOperationModel opToAdd = resolvedOp;

            List<BankImportModel.PendingOperationModel> updatedList = new java.util.ArrayList<>(current.pendingOperations());
            // Filter out any existing with same ID if any
            updatedList.removeIf(existing -> existing.id().equals(opToAdd.id()));
            updatedList.add(opToAdd);

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), updatedList, current.matchings()
            );
            bankImportCommandService.updateBankImport(updated);
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
