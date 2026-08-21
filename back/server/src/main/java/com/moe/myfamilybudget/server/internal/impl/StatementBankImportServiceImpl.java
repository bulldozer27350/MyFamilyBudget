package com.moe.myfamilybudget.server.internal.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.AutoMatchResultModel;
import com.moe.myfamilybudget.server.internal.model.BankImportCalculator;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportSummaryModel;
import com.moe.myfamilybudget.server.internal.model.CategorizeResultModel;
import com.moe.myfamilybudget.server.internal.model.PendingImportSummaryModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service and REST Controller implementing Clean Architecture for Bank Statement Imports
 * and Pending Operations management.
 */
@Service
@RestController
@RequestMapping
public class StatementBankImportServiceImpl {

    private final PersistenceManager persistenceManager;
    private final StatementBankImportMapper mapper;

    public StatementBankImportServiceImpl() {
        this.persistenceManager = PersistenceManager.getInstance();
        this.mapper = new StatementBankImportMapper();
    }

    public StatementBankImportServiceImpl(PersistenceManager persistenceManager, StatementBankImportMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.mapper = mapper;
    }

    @GetMapping("/bank-import")
    public ResponseEntity<Map<String, Object>> getBankImport() {
        BankImportModel internalModel = persistenceManager.getBankImport();
        Map<String, Object> responseMap = mapper.toBankImportResponseMap(internalModel);
        return ResponseEntity.ok(responseMap);
    }

    @PutMapping("/bank-import/mapping")
    public ResponseEntity<Void> updateBankImportMapping(@RequestBody Map<String, Object> newMappingDto) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankColumnMappingModel newMapping = mapper.toColumnMappingModel(newMappingDto);

        BankImportModel updatedModel = new BankImportModel(
                newMapping,
                current.categories(),
                current.rules(),
                current.transactions(),
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-import/csv")
    public ResponseEntity<Map<String, Object>> importBankCSV(
            @RequestBody Map<String, Object> payload
    ) {
        BankImportModel current = persistenceManager.getBankImport();

        String rawCSV = (String) payload.getOrDefault("csvText", "");
        @SuppressWarnings("unchecked")
        List<String> colRoles = (List<String>) payload.getOrDefault("colRoles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        Map<String, Object> mappingMap = (Map<String, Object>) payload.get("mapping");

        BankImportModel.BankColumnMappingModel mappingModel = mapper.toColumnMappingModel(mappingMap);
        List<List<String>> rawRows = BankImportCalculator.parseCSVText(rawCSV, mappingModel.delimiter());

        if (mappingModel.hasHeader() && !rawRows.isEmpty()) {
            rawRows = rawRows.subList(1, rawRows.size());
        }

        BankImportSummaryModel summary = BankImportCalculator.importTransactions(
                rawRows, colRoles, mappingModel, current.transactions(), current.rules()
        );

        List<BankImportModel.BankTransactionModel> allTransactions = new ArrayList<>(current.transactions());
        allTransactions.addAll(summary.newTransactions());

        BankImportModel updatedModel = new BankImportModel(
                summary.updatedMapping(),
                current.categories(),
                current.rules(),
                allTransactions,
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok(mapper.toImportSummaryMap(summary));
    }

    @PostMapping("/bank-import/categories")
    public ResponseEntity<Map<String, Object>> addCategory(@RequestBody Map<String, Object> categoryDto) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.CategoryModel newCategory = mapper.toCategoryModel(categoryDto);

        List<BankImportModel.CategoryModel> categories = new ArrayList<>(current.categories());
        categories.add(newCategory);

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), categories, current.rules(), current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toCategoryMap(newCategory));
    }

    @PutMapping("/bank-import/categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> categoryDto
    ) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.CategoryModel> categories = new ArrayList<>();

        BankImportModel.CategoryModel updatedCategory = null;
        for (BankImportModel.CategoryModel c : current.categories()) {
            if (c.id().equals(id)) {
                String label = categoryDto.containsKey("label") ? (String) categoryDto.get("label") : c.label();
                String kind = categoryDto.containsKey("kind") ? (String) categoryDto.get("kind") : c.kind();
                String compressible = categoryDto.containsKey("compressible") ? (String) categoryDto.get("compressible") : c.compressible();
                updatedCategory = new BankImportModel.CategoryModel(id, label, kind, compressible);
                categories.add(updatedCategory);
            } else {
                categories.add(c);
            }
        }

        if (updatedCategory == null) {
            throw new IllegalArgumentException("Catégorie non trouvée : " + id);
        }

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), categories, current.rules(), current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toCategoryMap(updatedCategory));
    }

    @DeleteMapping("/bank-import/categories/{id}")
    public ResponseEntity<Void> removeCategory(@PathVariable("id") String id) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.CategoryModel> categories = current.categories().stream()
                .filter(c -> !c.id().equals(id))
                .collect(Collectors.toList());

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), categories, current.rules(), current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-import/rules")
    public ResponseEntity<Map<String, Object>> addRule(@RequestBody Map<String, Object> ruleDto) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankImportRuleModel newRule = mapper.toRuleModel(ruleDto);

        List<BankImportModel.BankImportRuleModel> rules = new ArrayList<>(current.rules());
        rules.add(newRule);

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), rules, current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toRuleMap(newRule));
    }

    @PutMapping("/bank-import/rules/{id}")
    public ResponseEntity<Map<String, Object>> updateRule(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> ruleDto
    ) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankImportRuleModel> rules = new ArrayList<>();

        BankImportModel.BankImportRuleModel updatedRule = null;
        for (BankImportModel.BankImportRuleModel r : current.rules()) {
            if (r.id().equals(id)) {
                String matchText = ruleDto.containsKey("matchText") ? (String) ruleDto.get("matchText") : r.matchText();
                String categoryId = ruleDto.containsKey("categoryId") ? (String) ruleDto.get("categoryId") : r.categoryId();
                updatedRule = new BankImportModel.BankImportRuleModel(id, matchText, categoryId);
                rules.add(updatedRule);
            } else {
                rules.add(r);
            }
        }

        if (updatedRule == null) {
            throw new IllegalArgumentException("Règle non trouvée : " + id);
        }

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), rules, current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toRuleMap(updatedRule));
    }

    @DeleteMapping("/bank-import/rules/{id}")
    public ResponseEntity<Void> removeRule(@PathVariable("id") String id) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankImportRuleModel> rules = current.rules().stream()
                .filter(r -> !r.id().equals(id))
                .collect(Collectors.toList());

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), rules, current.transactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-import/rules/recalculate")
    public ResponseEntity<Void> recalculateBankImportRules() {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankTransactionModel> recalculated = BankImportCalculator.applyRulesToTransactions(
                current.transactions(), current.rules()
        );

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), recalculated, current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-import/transactions/{txId}/category")
    public ResponseEntity<Void> setTransactionCategory(
            @PathVariable("txId") String txId,
            @RequestBody Map<String, Object> body
    ) {
        BankImportModel current = persistenceManager.getBankImport();
        String categoryId = (String) body.getOrDefault("categoryId", "");
        String ruleKeyword = (String) body.get("ruleKeyword");

        CategorizeResultModel catResult = BankImportCalculator.categorizeTransaction(
                txId, categoryId, ruleKeyword, current.transactions(), current.rules()
        );

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), catResult.updatedRules(), catResult.updatedTransactions(), current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-import/transactions/force")
    public ResponseEntity<Map<String, Object>> forceImportBankTransaction(@RequestBody Map<String, Object> txDto) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankTransactionModel tx = mapper.toTransactionModel(txDto);

        List<BankImportModel.BankTransactionModel> transactions = new ArrayList<>(current.transactions());
        List<BankImportModel.BankTransactionModel> categorized = BankImportCalculator.applyRulesToTransactions(List.of(tx), current.rules());
        BankImportModel.BankTransactionModel finalTx = categorized.isEmpty() ? tx : categorized.get(0);

        transactions.add(finalTx);

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), transactions, current.pendingOperations(), current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toTransactionMap(finalTx));
    }

    @PostMapping("/pending-operations/reconcile")
    public ResponseEntity<Map<String, Object>> autoMatchPendingOperations() {
        BankImportModel current = persistenceManager.getBankImport();

        AutoMatchResultModel matchResult = BankImportCalculator.autoMatchPendingOperations(
                current.pendingOperations(), current.transactions()
        );

        if (matchResult.matchCount() > 0) {
            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(), current.transactions(), matchResult.updatedOperations(), current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

        return ResponseEntity.ok(mapper.toAutoMatchResultMap(matchResult));
    }

    @PostMapping("/pending-operations/import-cb")
    public ResponseEntity<Map<String, Object>> importPendingCB(@RequestBody Map<String, Object> payload) {
        BankImportModel current = persistenceManager.getBankImport();

        String rawCSV = (String) payload.getOrDefault("csvText", "");
        @SuppressWarnings("unchecked")
        List<String> colRoles = (List<String>) payload.getOrDefault("colRoles", Collections.emptyList());
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) payload.get("config");

        String dateFormat = config != null ? (String) config.get("dateFormat") : "DD-MM-YYYY";
        boolean usePurchaseDate = config != null && Boolean.TRUE.equals(config.get("usePurchaseDate"));
        String delimiter = config != null ? (String) config.getOrDefault("delimiter", ";") : ";";

        List<List<String>> rawRows = BankImportCalculator.parseCSVText(rawCSV, delimiter);

        PendingImportSummaryModel summary = BankImportCalculator.importPendingCB(
                rawRows, colRoles, dateFormat, usePurchaseDate, current.pendingOperations(), current.rules()
        );

        if (!summary.newOperations().isEmpty()) {
            List<BankImportModel.PendingOperationModel> allOps = new ArrayList<>(current.pendingOperations());
            allOps.addAll(summary.newOperations());

            BankImportModel updated = new BankImportModel(
                    current.columnMapping(), current.categories(), current.rules(), current.transactions(), allOps, current.matchings()
            );
            persistenceManager.updateBankImport(updated);
        }

        return ResponseEntity.ok(mapper.toPendingImportSummaryMap(summary));
    }

    @PostMapping("/pending-operations")
    public ResponseEntity<Map<String, Object>> savePendingOperation(@RequestBody Map<String, Object> opDto) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.PendingOperationModel opModel = mapper.toPendingOperationModel(opDto);

        List<BankImportModel.PendingOperationModel> ops = new ArrayList<>(current.pendingOperations());
        int existingIndex = -1;

        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i).id().equals(opModel.id())) {
                existingIndex = i;
                break;
            }
        }

        BankImportModel.PendingOperationModel savedOp;
        if (existingIndex >= 0) {
            savedOp = opModel;
            ops.set(existingIndex, savedOp);
        } else {
            savedOp = opModel;
            ops.add(savedOp);
        }

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), current.transactions(), ops, current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(mapper.toPendingOperationMap(savedOp));
    }

    @DeleteMapping("/pending-operations/{id}")
    public ResponseEntity<Void> deletePendingOperation(@PathVariable("id") String id) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.PendingOperationModel> ops = current.pendingOperations().stream()
                .filter(o -> !o.id().equals(id))
                .collect(Collectors.toList());

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), current.transactions(), ops, current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/pending-operations/{id}/unlink")
    public ResponseEntity<Map<String, Object>> unlinkPendingOperation(@PathVariable("id") String id) {
        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.PendingOperationModel unlinkedOp = null;
        List<BankImportModel.PendingOperationModel> ops = new ArrayList<>();

        for (BankImportModel.PendingOperationModel o : current.pendingOperations()) {
            if (o.id().equals(id)) {
                unlinkedOp = new BankImportModel.PendingOperationModel(
                        o.id(), o.date(), o.expectedDate(), o.type(), o.refNumber(),
                        o.label(), o.amount(), o.categoryId(), "pending", null, null, o.notes()
                );
                ops.add(unlinkedOp);
            } else {
                ops.add(o);
            }
        }

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), current.transactions(), ops, current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(unlinkedOp != null ? mapper.toPendingOperationMap(unlinkedOp) : Collections.emptyMap());
    }

    @PostMapping("/pending-operations/{id}/link")
    public ResponseEntity<Map<String, Object>> linkPendingOperation(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body
    ) {
        BankImportModel current = persistenceManager.getBankImport();
        String txId = (String) body.get("txId");
        String txDate = (String) body.get("txDate");

        BankImportModel.PendingOperationModel linkedOp = null;
        List<BankImportModel.PendingOperationModel> ops = new ArrayList<>();

        for (BankImportModel.PendingOperationModel o : current.pendingOperations()) {
            if (o.id().equals(id)) {
                linkedOp = new BankImportModel.PendingOperationModel(
                        o.id(), o.date(), o.expectedDate(), o.type(), o.refNumber(),
                        o.label(), o.amount(), o.categoryId(), "cleared", txId, txDate, o.notes()
                );
                ops.add(linkedOp);
            } else {
                ops.add(o);
            }
        }

        BankImportModel updated = new BankImportModel(
                current.columnMapping(), current.categories(), current.rules(), current.transactions(), ops, current.matchings()
        );
        persistenceManager.updateBankImport(updated);

        return ResponseEntity.ok(linkedOp != null ? mapper.toPendingOperationMap(linkedOp) : Collections.emptyMap());
    }
}
