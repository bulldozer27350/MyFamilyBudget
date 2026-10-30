package com.moe.myfamilybudget.web.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.api.controller.ImportBancaireApi;
import com.moe.myfamilybudget.api.model.BankTransactionSplitDto;
import com.moe.myfamilybudget.api.model.ImportBankTransactionsRequestDto;
import com.moe.myfamilybudget.api.model.SetBankTransactionCategoryRequestDto;
import com.moe.myfamilybudget.api.model.UpdateBankImportLigneRequestDto;
import com.moe.myfamilybudget.application.error.DataParsingException;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankLigneNotFoundException;
import com.moe.myfamilybudget.application.usecase.bankpointage.BankPointageUseCase;

import jakarta.validation.Valid;

/**
 * Contrôleur REST implémentant le contrat OpenAPI ImportBancaireApi (Tag: Import Bancaire).
 *
 * <p>R-83 (même gabarit que {@code TresorerieServiceImpl}, R-82, SILO-314) : ce contrôleur ne garde que la
 * conversion vers le monde HTTP ({@link ResponseEntity}, DTO) et la décision d'appeler ou non (corps de requête
 * absent). La composition de la réponse (lectures des fragments, appel du moteur d'import, catégorisation,
 * traduction des types du silo) est portée par le cas d'usage {@link BankPointageUseCase}
 * ({@code application-api}), implémenté par {@code DefaultBankPointageUseCase}. Les anciens 404/400 construits
 * directement dans ce contrôleur (ligne introuvable, liste inconnue) sont désormais des exceptions levées par le
 * cas d'usage ({@link BankLigneNotFoundException}, {@link IllegalArgumentException}), traduites ici.
 *
 * <p>{@code convertExcelToCsv} reste ici : utilitaire de conversion de format pur, qui ne lit ni n'écrit le silo
 * Banque/Pointage (pas de cas d'usage impliqué).
 *
 * <p>RF-B01 (voir doc/architecture/13-persistance.md) : plus d'appel direct à
 * {@code PersistenceManager}.
 */
@Service
@RestController
public class StatementBankImportServiceImpl implements ImportBancaireApi {

    private static final Logger LOG = LoggerFactory.getLogger(StatementBankImportServiceImpl.class);

    private final BankPointageUseCase bankPointageUseCase;
    private final ExcelToCsvService excelToCsvService;

    public StatementBankImportServiceImpl(BankPointageUseCase bankPointageUseCase,
                                          ExcelToCsvService excelToCsvService) {
        this.bankPointageUseCase = bankPointageUseCase;
        this.excelToCsvService = excelToCsvService;
    }

    // ---------------------------------------------------------------------------
    // TAG: IMPORT BANCAIRE (3 méthodes définies dans OpenAPI)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> getBankImport() {
        return ResponseEntity.ok(bankPointageUseCase.getBankImport());
    }

    @Override
    public ResponseEntity<Void> updateBankImportMapping(Object body) {
        bankPointageUseCase.updateBankImportMapping(toMap(body));
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> importBankCSV(MultipartFile file) {
        String csvText = "";
        if (file != null && !file.isEmpty()) {
            try (InputStream is = file.getInputStream()) {
                csvText = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Auparavant : csvText restait "" et l'import se poursuivait silencieusement,
                // aboutissant à une réponse 200 OK sans qu'aucune transaction ne soit importée
                // (faux succès). Le fichier envoyé par l'utilisateur ne peut, par construction,
                // pas être relu une seconde fois : on ne peut pas se contenter d'ignorer l'erreur
                // et de continuer avec un texte vide, il faut la remonter explicitement.
                LOG.warn("Fichier CSV illisible lors de l'import bancaire ('{}') : {}",
                        file.getOriginalFilename(), e.getMessage(), e);
                throw new DataParsingException(
                        "Impossible de lire le fichier CSV envoyé : " + e.getMessage(), e);
            }
        }
        bankPointageUseCase.importBankCSV(csvText);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Object> importBankTransactions(
            @Valid ImportBankTransactionsRequestDto request) {
        if (request == null || request.getRawRows() == null || request.getColRoles() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Données d'import manquantes."));
        }

        try {
            Map<String, Object> mapping = request.getMapping() != null ? toMap(request.getMapping()) : null;
            Map<String, Object> summary = bankPointageUseCase.importBankTransactions(
                    request.getRawRows(), request.getColRoles(), mapping);
            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Object> forceImportBankTransaction(Object body) {
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> created = bankPointageUseCase.forceImportBankTransaction(toMap(body));
        return ResponseEntity.ok(created);
    }

    @Override
    public ResponseEntity<Void> updateBankTransactionSplits(String txId,
            @Valid List<@Valid BankTransactionSplitDto> bankTransactionSplitDto) {
        List<Map<String, Object>> splits = bankTransactionSplitDto == null
                ? Collections.emptyList()
                : bankTransactionSplitDto.stream()
                        .map(StatementBankImportServiceImpl::toSplitMap)
                        .collect(Collectors.toList());
        try {
            bankPointageUseCase.updateBankTransactionSplits(txId, splits);
        } catch (BankLigneNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    // ---------------------------------------------------------------------------
    // CRUD Catégories & Règles (add / update / remove)
    // ---------------------------------------------------------------------------

    @Override
    public ResponseEntity<Object> addBankImportLigne(String listKey, Object body) {
        try {
            Map<String, Object> created = bankPointageUseCase.addBankImportLigne(listKey, toMap(body));
            return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Override
    public ResponseEntity<Void> updateBankImportLigne(String listKey, String id, UpdateBankImportLigneRequestDto body) {
        if (body == null) return ResponseEntity.badRequest().build();
        try {
            bankPointageUseCase.updateBankImportLigne(listKey, id, body.getField(), body.getValue());
        } catch (BankLigneNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> removeBankImportLigne(String listKey, String id) {
        try {
            bankPointageUseCase.removeBankImportLigne(listKey, id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
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
        bankPointageUseCase.setBankImportTransactionCategory(txId, body.getCategoryId(), body.getRuleKeyword());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> recalculateBankImportRules() {
        bankPointageUseCase.recalculateBankImportRules();
        return ResponseEntity.ok().build();
    }

    // --- Utility ---

    /**
     * Conversion de format pure (Excel → CSV) : ne lit ni n'écrit aucun silo, pas de cas d'usage impliqué
     * (cf. javadoc de la classe).
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

    private static Map<String, Object> toSplitMap(BankTransactionSplitDto s) {
        Map<String, Object> m = new HashMap<>();
        if (s != null) {
            m.put("id", s.getId());
            m.put("categoryId", s.getCategoryId());
            m.put("amount", s.getAmount());
            m.put("label", s.getLabel());
        }
        return m;
    }

}
