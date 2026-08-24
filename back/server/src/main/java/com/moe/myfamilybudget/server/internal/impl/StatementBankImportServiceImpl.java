package com.moe.myfamilybudget.server.internal.impl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.moe.myfamilybudget.api.controller.ImportBancaireApi;
import com.moe.myfamilybudget.api.model.BankTransactionSplitDto;
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

    public StatementBankImportServiceImpl(PersistenceManager persistenceManager, StatementBankImportMapper mapper) {
        this.persistenceManager = persistenceManager;
        this.mapper = mapper;
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

    // --- Utility ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object body) {
        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Collections.emptyMap();
    }

}
