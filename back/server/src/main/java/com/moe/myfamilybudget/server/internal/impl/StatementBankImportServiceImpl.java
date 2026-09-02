package com.moe.myfamilybudget.server.internal.impl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moe.myfamilybudget.api.controller.ImportBancaireApi;
import com.moe.myfamilybudget.api.model.BankTransactionSplitDto;
import com.moe.myfamilybudget.api.model.ImportBankTransactionsRequestDto;
import com.moe.myfamilybudget.api.model.SetBankTransactionCategoryRequestDto;
import com.moe.myfamilybudget.api.model.UpdateBankImportLigneRequestDto;
import com.moe.myfamilybudget.server.internal.mapper.StatementBankImportMapper;
import com.moe.myfamilybudget.server.internal.model.BankImportCalculator;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportSummaryModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

import jakarta.validation.Valid;

/**
 * Service et Contrôleur REST implémentant le contrat OpenAPI ImportBancaireApi (Tag: Import Bancaire).
 */
@Service
@RestController
public class StatementBankImportServiceImpl implements ImportBancaireApi {

    private final PersistenceManager persistenceManager;
    private final StatementBankImportMapper mapper;
    private final ExcelToCsvService excelToCsvService;

    public StatementBankImportServiceImpl(PersistenceManager persistenceManager,
                                          StatementBankImportMapper mapper,
                                          ExcelToCsvService excelToCsvService) {
        this.persistenceManager = persistenceManager;
        this.mapper = mapper;
        this.excelToCsvService = excelToCsvService;
    }

    // ---------------------------------------------------------------------------
    // TAG: IMPORT BANCAIRE (3 méthodes définies dans OpenAPI)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> getBankImport() {
        BankImportModel internalModel = persistenceManager.getBankImport();
        Map<String, Object> responseMap = mapper.toBankImportResponseMap(internalModel);
        return ResponseEntity.ok(responseMap);
    }

    @Override
    public ResponseEntity<Void> updateBankImportMapping(Object body) {
        BankImportModel current = persistenceManager.getBankImport();
        Map<String, Object> mappingMap = toMap(body);
        BankImportModel.BankColumnMappingModel newMapping = mapper.toColumnMappingModel(mappingMap);

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

    @Override
    public ResponseEntity<Void> importBankCSV(MultipartFile file) {
        String csvText = "";
        if (file != null && !file.isEmpty()) {
            try (InputStream is = file.getInputStream()) {
                csvText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                csvText = "";
            }
        }

        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankColumnMappingModel mappingModel = current.columnMapping();

        List<String> colRoles = new ArrayList<>();
        if (mappingModel.dateCol() != null) colRoles.add("date");
        if (mappingModel.labelCol() != null) colRoles.add("label");
        if (mappingModel.amountCol() != null) colRoles.add("amount");

        List<List<String>> rawRows = BankImportCalculator.parseCSVText(csvText, mappingModel.delimiter());
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
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Object> importBankTransactions(
            @Valid ImportBankTransactionsRequestDto request) {
        if (request == null || request.getRawRows() == null || request.getColRoles() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Données d'import manquantes."));
        }

        BankImportModel current = persistenceManager.getBankImport();
        BankImportModel.BankColumnMappingModel mappingModel;
        if (request.getMapping() != null) {
            mappingModel = mapper.toColumnMappingModel(toMap(request.getMapping()));
        } else {
            mappingModel = current.columnMapping();
        }

        try {
            BankImportSummaryModel summary = BankImportCalculator.importTransactions(
                    request.getRawRows(),
                    request.getColRoles(),
                    mappingModel,
                    current.transactions(),
                    current.rules()
            );

            List<BankImportModel.BankTransactionModel> allTransactions = new ArrayList<>(
                    current.transactions() != null ? current.transactions() : Collections.emptyList()
            );
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Object> forceImportBankTransaction(Object body) {
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> txMap = toMap(body);
        BankImportModel.BankTransactionModel tx = mapper.toTransactionModel(txMap);

        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankImportRuleModel> rules = current.rules() != null ? current.rules() : Collections.emptyList();

        List<BankImportModel.BankTransactionModel> categorized = BankImportCalculator.applyRulesToTransactions(List.of(tx), rules);
        BankImportModel.BankTransactionModel finalTx = (!categorized.isEmpty()) ? categorized.get(0) : tx;

        List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>(
                current.transactions() != null ? current.transactions() : Collections.emptyList()
        );
        updatedTxs.add(finalTx);

        BankImportModel updatedModel = new BankImportModel(
                current.columnMapping(),
                current.categories(),
                current.rules(),
                updatedTxs,
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok(mapper.toTransactionMap(finalTx));
    }

    @Override
    public ResponseEntity<Void> updateBankTransactionSplits(String txId,
            @Valid List<@Valid BankTransactionSplitDto> bankTransactionSplitDto) {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankTransactionModel> currentTxs = current.transactions();

        int txIndex = -1;
        for (int i = 0; i < currentTxs.size(); i++) {
            if (currentTxs.get(i).id().equals(txId)) {
                txIndex = i;
                break;
            }
        }
        if (txIndex == -1) {
            return ResponseEntity.notFound().build();
        }

        List<BankImportModel.BankTransactionSplitModel> splits = mapper.toSplitList(bankTransactionSplitDto);
        BankImportModel.BankTransactionModel existingTx = currentTxs.get(txIndex);
        BankImportModel.BankTransactionModel updatedTx = new BankImportModel.BankTransactionModel(
                existingTx.id(),
                existingTx.date(),
                existingTx.label(),
                existingTx.type(),
                existingTx.amount(),
                existingTx.categoryId(),
                splits
        );

        try {
            updatedTx.validateSplits();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>(currentTxs);
        updatedTxs.set(txIndex, updatedTx);

        BankImportModel updatedModel = new BankImportModel(
                current.columnMapping(),
                current.categories(),
                current.rules(),
                updatedTxs,
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok().build();
    }

    // ---------------------------------------------------------------------------
    // CRUD Catégories & Règles (add / update / remove)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> addBankImportLigne(String listKey, Object body) {
        BankImportModel current = persistenceManager.getBankImport();
        Map<String, Object> bodyMap = toMap(body);

        if ("categories".equals(listKey)) {
            BankImportModel.CategoryModel newCat = mapper.toCategoryModel(bodyMap);
            List<BankImportModel.CategoryModel> cats = new ArrayList<>(current.categories());
            cats.add(newCat);
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), cats, current.rules(),
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                    .body(mapper.toCategoryMap(newCat));
        } else if ("rules".equals(listKey)) {
            BankImportModel.BankImportRuleModel newRule = mapper.toRuleModel(bodyMap);
            List<BankImportModel.BankImportRuleModel> rules = new ArrayList<>(current.rules());
            rules.add(newRule);
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), current.categories(), rules,
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                    .body(mapper.toRuleMap(newRule));
        }
        return ResponseEntity.badRequest().build();
    }

    @Override
    public ResponseEntity<Void> updateBankImportLigne(String listKey, String id,
            UpdateBankImportLigneRequestDto body) {
        if (body == null) return ResponseEntity.badRequest().build();
        String field = body.getField();
        Object value = body.getValue();

        BankImportModel current = persistenceManager.getBankImport();

        if ("categories".equals(listKey)) {
            List<BankImportModel.CategoryModel> cats = current.categories();
            int idx = -1;
            for (int i = 0; i < cats.size(); i++) {
                if (cats.get(i).id().equals(id)) { idx = i; break; }
            }
            if (idx == -1) return ResponseEntity.notFound().build();

            BankImportModel.CategoryModel existing = cats.get(idx);
            String label = "label".equals(field) ? String.valueOf(value) : existing.label();
            String kind = "kind".equals(field) ? String.valueOf(value) : existing.kind();
            String compressible = "compressible".equals(field) ? String.valueOf(value) : existing.compressible();

            List<BankImportModel.CategoryModel> updated = new ArrayList<>(cats);
            updated.set(idx, new BankImportModel.CategoryModel(existing.id(), label, kind, compressible));
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), updated, current.rules(),
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.ok().build();

        } else if ("rules".equals(listKey)) {
            List<BankImportModel.BankImportRuleModel> rules = current.rules();
            int idx = -1;
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).id().equals(id)) { idx = i; break; }
            }
            if (idx == -1) return ResponseEntity.notFound().build();

            BankImportModel.BankImportRuleModel existing = rules.get(idx);
            String matchText = "matchText".equals(field) ? String.valueOf(value) : existing.matchText();
            String categoryId = "categoryId".equals(field) ? String.valueOf(value) : existing.categoryId();

            List<BankImportModel.BankImportRuleModel> updated = new ArrayList<>(rules);
            updated.set(idx, new BankImportModel.BankImportRuleModel(existing.id(), matchText, categoryId));
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), current.categories(), updated,
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().build();
    }

    @Override
    public ResponseEntity<Void> removeBankImportLigne(String listKey, String id) {
        BankImportModel current = persistenceManager.getBankImport();

        if ("categories".equals(listKey)) {
            List<BankImportModel.CategoryModel> cats = current.categories().stream()
                    .filter(c -> !c.id().equals(id))
                    .collect(Collectors.toList());
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), cats, current.rules(),
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.noContent().build();

        } else if ("rules".equals(listKey)) {
            List<BankImportModel.BankImportRuleModel> rules = current.rules().stream()
                    .filter(r -> !r.id().equals(id))
                    .collect(Collectors.toList());
            persistenceManager.updateBankImport(new BankImportModel(
                    current.columnMapping(), current.categories(), rules,
                    current.transactions(), current.pendingOperations(), current.matchings()));
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.badRequest().build();
    }

    // ---------------------------------------------------------------------------
    // Catégorisation manuelle & Recalcul de règles
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Void> setBankImportTransactionCategory(String txId,
            SetBankTransactionCategoryRequestDto body) {
        if (body == null || body.getCategoryId() == null) {
            return ResponseEntity.badRequest().build();
        }
        String categoryId = body.getCategoryId();
        String ruleKeyword = body.getRuleKeyword();

        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankImportRuleModel> currentRules = current.rules() != null ? current.rules() : Collections.emptyList();
        List<BankImportModel.BankImportRuleModel> newRules = new ArrayList<>(currentRules);

        if (ruleKeyword != null && !ruleKeyword.trim().isEmpty()) {
            String key = ruleKeyword.trim().toUpperCase();
            boolean found = false;
            for (int i = 0; i < newRules.size(); i++) {
                BankImportModel.BankImportRuleModel r = newRules.get(i);
                if (r.matchText() != null && r.matchText().trim().toUpperCase().equals(key)) {
                    newRules.set(i, new BankImportModel.BankImportRuleModel(r.id(), r.matchText(), categoryId));
                    found = true;
                    break;
                }
            }
            if (!found) {
                newRules.add(new BankImportModel.BankImportRuleModel(null, ruleKeyword.trim(), categoryId));
            }
        }

        List<BankImportModel.BankTransactionModel> currentTxs = current.transactions() != null ? current.transactions() : Collections.emptyList();
        List<BankImportModel.BankTransactionModel> updatedTxs = new ArrayList<>();
        for (BankImportModel.BankTransactionModel t : currentTxs) {
            if (t.id() != null && t.id().equals(txId)) {
                updatedTxs.add(new BankImportModel.BankTransactionModel(
                        t.id(), t.date(), t.label(), t.type(), t.amount(), categoryId, t.splits()
                ));
            } else {
                updatedTxs.add(t);
            }
        }

        List<BankImportModel.BankTransactionModel> finalTxs = BankImportCalculator.applyRulesToTransactions(updatedTxs, newRules);

        BankImportModel updatedModel = new BankImportModel(
                current.columnMapping(),
                current.categories(),
                newRules,
                finalTxs,
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> recalculateBankImportRules() {
        BankImportModel current = persistenceManager.getBankImport();
        List<BankImportModel.BankTransactionModel> currentTxs = current.transactions() != null ? current.transactions() : Collections.emptyList();
        List<BankImportModel.BankImportRuleModel> currentRules = current.rules() != null ? current.rules() : Collections.emptyList();

        List<BankImportModel.BankTransactionModel> recalculated = BankImportCalculator.applyRulesToTransactions(currentTxs, currentRules);

        BankImportModel updatedModel = new BankImportModel(
                current.columnMapping(),
                current.categories(),
                currentRules,
                recalculated,
                current.pendingOperations(),
                current.matchings()
        );

        persistenceManager.updateBankImport(updatedModel);
        return ResponseEntity.ok().build();
    }

    // --- Utility ---

    /**
     * Convertit un fichier Excel (.xls ou .xlsx) en texte CSV.
     * Retourne le CSV en text/plain pour que le front-end puisse ensuite
     * le traiter exactement comme un fichier CSV normal.
     */
    @Override
    public ResponseEntity<String> convertExcelToCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Fichier manquant ou vide.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        try {
            String csvText = excelToCsvService.convert(file, filename);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/plain; charset=UTF-8")
                    .body(csvText);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Erreur lors de la conversion Excel → CSV : " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

}
