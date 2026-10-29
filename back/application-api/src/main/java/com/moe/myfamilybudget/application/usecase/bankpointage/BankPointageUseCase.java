package com.moe.myfamilybudget.application.usecase.bankpointage;

import java.util.List;
import java.util.Map;

/**
 * Cas d'usage « Banque et Pointage » exposé par l'application (R-83, même esprit que {@code TreasuryUseCase}
 * (R-82) : import bancaire, opérations en attente, pointage, SILO-314).
 *
 * <p>Contrat applicatif : il ne connaît ni DTO OpenAPI, ni {@code ResponseEntity}, ni type du silo
 * Banque/Pointage ou des silos lus pour composer les réponses Pointage/Opérations en attente. Le module
 * {@code web} (aujourd'hui {@code StatementBankImportServiceImpl}, {@code PendingOperationsServiceImpl},
 * {@code PointageServiceImpl}) convertit vers/depuis le monde HTTP : les corps de requête (DTO OpenAPI ou
 * {@code Object}) sont ramenés à des {@code Map}/listes avant l'appel, la réponse ({@code Map}) est renvoyée
 * telle quelle (contrat REST déjà « objet libre » sur ce périmètre, inchangé par ce patch).
 *
 * <p>Contrairement à {@code TreasuryUseCase}, ce cas d'usage n'introduit aucun modèle applicatif dédié : le silo
 * Banque/Pointage n'exposait déjà, avant ce patch, que des {@code Map<String, Object>} au-delà de la frontière
 * REST (pas de DTO fortement typé comme pour la Trésorerie). Dupliquer ici le modèle interne de
 * {@code BankImportModel} (huit records imbriqués) n'aurait rien ajouté au contrat et aurait beaucoup élargi la
 * surface du patch pour un bénéfice nul. {@code application-api} reste donc sans dépendance vers
 * {@code bank-pointage-api} ni vers aucun autre module du reactor (D3).
 */
public interface BankPointageUseCase {

    // --- Import bancaire (ex ImportBancaireApi) ---

    /** Compose la réponse Import Bancaire : mapping de colonnes, catégories, règles, transactions. */
    Map<String, Object> getBankImport();

    /** Remplace le mapping de colonnes de l'import bancaire. */
    void updateBankImportMapping(Map<String, Object> mapping);

    /** Importe un relevé CSV déjà décodé en texte, avec le mapping de colonnes courant. */
    void importBankCSV(String csvText);

    /**
     * Importe des lignes brutes déjà découpées en colonnes, avec un mapping de colonnes explicite.
     *
     * @throws IllegalArgumentException si les colonnes demandées ne correspondent pas aux lignes fournies
     */
    Map<String, Object> importBankTransactions(List<List<String>> rawRows, List<String> colRoles, Map<String, Object> mapping);

    /** Ajoute une transaction unique, catégorisée par les règles courantes si elle n'a ni catégorie ni ventilation. */
    Map<String, Object> forceImportBankTransaction(Map<String, Object> transaction);

    /**
     * Remplace les ventilations d'une transaction.
     *
     * @throws BankLigneNotFoundException si {@code txId} ne désigne aucune transaction connue
     * @throws IllegalArgumentException   si la somme des ventilations ne correspond pas au montant de la
     *                                     transaction
     */
    void updateBankTransactionSplits(String txId, List<Map<String, Object>> splits);

    /**
     * Ajoute une ligne (catégorie ou règle).
     *
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    Map<String, Object> addBankImportLigne(String listKey, Map<String, Object> row);

    /**
     * Met à jour un champ d'une ligne (catégorie ou règle).
     *
     * @throws IllegalArgumentException   si {@code listKey} ne désigne aucune liste connue
     * @throws BankLigneNotFoundException si {@code id} ne désigne aucune ligne connue dans cette liste
     */
    void updateBankImportLigne(String listKey, String id, String field, Object value);

    /**
     * Supprime une ligne (catégorie ou règle) ; sans effet si {@code id} est inconnu dans la liste.
     *
     * @throws IllegalArgumentException si {@code listKey} ne désigne aucune liste connue
     */
    void removeBankImportLigne(String listKey, String id);

    /** Catégorise manuellement une transaction, et mémorise la règle associée si un mot-clé est fourni. */
    void setBankImportTransactionCategory(String txId, String categoryId, String ruleKeyword);

    /** Réapplique les règles de catégorisation courantes à toutes les transactions. */
    void recalculateBankImportRules();

    // --- Opérations en attente (ex OperationsEnCoursApi) ---

    /**
     * Compose la réponse Opérations en cours : opérations en attente, transactions, catégories, règles,
     * charges, revenus, dépenses ponctuelles, paramètres.
     */
    Map<String, Object> getPendingOperations();

    /**
     * Rapproche automatiquement les opérations en attente avec les transactions importées.
     *
     * @return une réponse avec les clés {@code matchCount} et {@code needsReviewCount}
     */
    Map<String, Object> reconcilePendingOperations();

    /** Marque une opération en attente comme ignorée ; sans effet si l'identifiant est absent ou inconnu. */
    void ignorePendingOperation(String operationId);

    /** Importe des opérations CB en attente depuis des lignes brutes déjà découpées en colonnes. */
    Map<String, Object> importPendingCB(List<List<String>> rawRows, List<String> colRoles, String dateFormat, boolean usePurchaseDate);

    /** Fusionne une opération bancaire importée avec une opération manuelle existante ; sans effet si
     * {@code manualOpId} est absent. */
    void mergePendingOperation(String manualOpId, Map<String, Object> bankOp);

    /** Force l'ajout d'une opération en attente, catégorisée par les règles courantes si besoin. */
    void forceImportPendingOperation(Map<String, Object> operation);

    // --- Pointage (ex PointageApi) ---

    /**
     * Compose la réponse Pointage : transactions, catégories, rapprochements, charges, revenus, placements,
     * paramètres.
     */
    Map<String, Object> getPointage();

    /**
     * Enregistre les rapprochements (transaction ↔ ligne budgétaire) d'un mois.
     *
     * @param matchingLinksBody corps de requête brut (liste, ou objet portant une clé {@code links})
     * @throws IllegalArgumentException si {@code monthISO} est vide
     */
    void savePointageMatching(String monthISO, Object matchingLinksBody);
}
