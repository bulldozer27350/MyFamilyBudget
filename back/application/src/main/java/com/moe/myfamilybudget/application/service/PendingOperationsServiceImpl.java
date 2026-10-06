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
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportChange;
import com.moe.myfamilybudget.application.command.BankImportCommandService;
import com.moe.myfamilybudget.application.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.domain.bankpointage.model.AutoMatchResultModel;
import com.moe.myfamilybudget.domain.bankpointage.calculation.BankImportCalculationService;
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
    private final BankImportCalculationService bankImportCalculationService;

    public PendingOperationsServiceImpl(
            BankReader bankReader,
            BudgetReader budgetReader,
            SettingsReader settingsReader,
            BankImportCommandService bankImportCommandService,
            StatementBankImportMapper mapper,
            BankImportCalculationService bankImportCalculationService) {
        this.bankReader = bankReader;
        this.budgetReader = budgetReader;
        this.settingsReader = settingsReader;
        this.bankImportCommandService = bankImportCommandService;
        this.mapper = mapper;
        this.bankImportCalculationService = bankImportCalculationService;
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
        // SILO-213 (lot B, etape b) : lecture, calcul et ecriture sous le verrou du silo Banque.
        AutoMatchResultModel matchResult = bankImportCommandService.modifyBankImport(current -> {
            AutoMatchResultModel result = bankImportCalculationService.autoMatchPendingOperations(
                    current.pendingOperations(), current.transactions()
            );
            if (result.matchCount() <= 0) {
                return BankImportChange.unchanged(result);
            }
            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    result.updatedTransactions(), result.updatedOperations(), current.matchings()
            ), result);
        });

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
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.PendingOperationModel> updatedOps = current.pendingOperations().stream()
                        .map(op -> op.id().equals(targetId)
                                ? new BankImportModel.PendingOperationModel(
                                op.id(), op.date(), op.expectedDate(), op.type(), op.refNumber(),
                                op.label(), op.amount(), op.categoryId(), "ignored", op.linkedTxId(),
                                op.clearedDate(), op.notes(), op.splits())
                                : op)
                        .collect(Collectors.toList());

                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), current.rules(),
                        current.transactions(), updatedOps, current.matchings()
                ), null);
            });
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

        com.moe.myfamilybudget.domain.bankpointage.model.PendingImportSummaryModel summary = bankImportCommandService.modifyBankImport(current -> {
            com.moe.myfamilybudget.domain.bankpointage.model.PendingImportSummaryModel result = bankImportCalculationService.importPendingCB(
                    rawRows,
                    colRoles,
                    dateFormat,
                    usePurchaseDate,
                    current.pendingOperations(),
                    current.rules()
            );
            if (result.newOperations().isEmpty()) {
                return BankImportChange.unchanged(result);
            }
            List<BankImportModel.PendingOperationModel> allPending = new java.util.ArrayList<>(current.pendingOperations());
            allPending.addAll(result.newOperations());
            return BankImportChange.write(new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(),
                    current.transactions(), allPending, current.matchings()
            ), result);
        });

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
            bankImportCommandService.modifyBankImport(current -> {
                List<BankImportModel.PendingOperationModel> mergedList = bankImportCalculationService.mergePendingOperation(
                        manualOpId,
                        bankOp,
                        current.pendingOperations()
                );

                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), current.rules(),
                        current.transactions(), mergedList, current.matchings()
                ), null);
            });
        }

        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> forceImportPendingOperation(Object body) {
        Map<String, Object> map = toMap(body);
        BankImportModel.PendingOperationModel op = mapper.toPendingOperationModel(map);

        if (op != null) {
            bankImportCommandService.modifyBankImport(current -> {
                BankImportModel.PendingOperationModel resolvedOp = op;
                if ((op.categoryId() == null || op.categoryId().isBlank()) && (op.splits() == null || op.splits().isEmpty())) {
                    List<BankImportModel.PendingOperationModel> rulesApplied = bankImportCalculationService.applyRulesToPendingOperations(
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

                return BankImportChange.write(new BankImportModel(
                        current.columnMapping(), current.categories(), current.rules(),
                        current.transactions(), updatedList, current.matchings()
                ), null);
            });
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
