package com.moe.myfamilybudget.server.internal.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.repository.*;

import jakarta.annotation.PostConstruct;

/**
 * Gestionnaire de persistance avec Spring Data JPA.
 * Les données sont conservées dans une base de données H2 et persistent entre les redémarrages du serveur.
 */
@Component
@Transactional
public class PersistenceManager {

    private final BudgetDataRepository budgetDataRepository;
    private final SettingsRepository settingsRepository;
    private final IncomeRepository incomeRepository;
    private final ChargeRepository chargeRepository;
    private final PlacementRepository placementRepository;
    private final RealEstateRepository realEstateRepository;
    private final OneOffExpenseRepository oneOffExpenseRepository;
    private final TransferRepository transferRepository;
    private final VariableIncomeRepository variableIncomeRepository;
    private final VariableOverrideRepository variableOverrideRepository;
    private final TaxChildRepository taxChildRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final TaxRateOverrideRepository taxRateOverrideRepository;
    private final TaxActualOverrideRepository taxActualOverrideRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final RetirementRepository retirementRepository;
    private final BankImportRepository bankImportRepository;
    private final LoanRepository loanRepository;

    // Gestion programmatique de la transaction pour l'initialisation au démarrage.
    // Voir le commentaire dans BudgetPersistenceGateway.save() : le @Transactional de classe ne
    // s'applique jamais à un appel émis depuis @PostConstruct (self-invocation avant
    // la création du proxy AOP). On utilise donc un TransactionTemplate explicite
    // pour englober l'unique sauvegarde effectuée pendant init().
    private final TransactionTemplate transactionTemplate;

    // Passerelle JPA (point 6 de l'audit, 1er incrément du Strangler Fig) : concentre tout
    // l'accès direct aux repositories Spring Data. Volontairement pas un bean Spring : une
    // instance est simplement construite ici, dans chacun des deux constructeurs, avec les mêmes
    // repositories que ceux reçus par PersistenceManager — settingsRepository et
    // retirementRepository ne lui sont pas transmis car ils ne sont jamais lus (settings et
    // retirement sont rattachés à BudgetDataEntity par cascade JPA, voir le commentaire dans
    // BudgetPersistenceGateway.save()).
    private final BudgetPersistenceGateway gateway;

    // Cache mémoire + point d'entrée unique de mutation (point 6 de l'audit, 2e incrément du
    // Strangler Fig) : concentre currentBudget/mutationLock/applyAndPersist/init/getBudgetData/
    // setBudgetData/resetData/createDefaultBudgetData. Comme `gateway`, volontairement pas un
    // bean Spring.
    private final BudgetCacheStore cacheStore;

    // Logique métier de toutes les mutations (point 6 de l'audit, 3e et dernier incrément du
    // Strangler Fig) : dispatcher du point 3, sections trésorerie/patrimoine/retraite/fiscalité/
    // catégories d'actifs/historique de placement/import bancaire. PersistenceManager est
    // désormais une pure façade : chacune de ses méthodes publiques ne fait plus que déléguer à
    // la méthode de même nom ici. Comme `gateway` et `cacheStore`, volontairement pas un bean
    // Spring.
    private final BudgetMutationService mutationService;

    // Default constructor for testing compatibility
    public PersistenceManager() {
        this.budgetDataRepository = null;
        this.settingsRepository = null;
        this.incomeRepository = null;
        this.chargeRepository = null;
        this.placementRepository = null;
        this.realEstateRepository = null;
        this.oneOffExpenseRepository = null;
        this.transferRepository = null;
        this.variableIncomeRepository = null;
        this.variableOverrideRepository = null;
        this.taxChildRepository = null;
        this.taxBracketRepository = null;
        this.taxRateOverrideRepository = null;
        this.taxActualOverrideRepository = null;
        this.assetCategoryRepository = null;
        this.retirementRepository = null;
        this.bankImportRepository = null;
        this.loanRepository = null;
        this.transactionTemplate = null;
        this.gateway = new BudgetPersistenceGateway(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        this.cacheStore = new BudgetCacheStore(this.gateway, this.transactionTemplate);
        this.mutationService = new BudgetMutationService(this.cacheStore);
    }

    @Autowired
    public PersistenceManager(BudgetDataRepository budgetDataRepository,
                            SettingsRepository settingsRepository,
                            IncomeRepository incomeRepository,
                            ChargeRepository chargeRepository,
                            PlacementRepository placementRepository,
                            RealEstateRepository realEstateRepository,
                            OneOffExpenseRepository oneOffExpenseRepository,
                            TransferRepository transferRepository,
                            VariableIncomeRepository variableIncomeRepository,
                            VariableOverrideRepository variableOverrideRepository,
                            TaxChildRepository taxChildRepository,
                            TaxBracketRepository taxBracketRepository,
                            TaxRateOverrideRepository taxRateOverrideRepository,
                            TaxActualOverrideRepository taxActualOverrideRepository,
                            AssetCategoryRepository assetCategoryRepository,
                            RetirementRepository retirementRepository,
                            BankImportRepository bankImportRepository,
                            LoanRepository loanRepository,
                            PlatformTransactionManager transactionManager) {
        this.budgetDataRepository = budgetDataRepository;
        this.settingsRepository = settingsRepository;
        this.incomeRepository = incomeRepository;
        this.chargeRepository = chargeRepository;
        this.placementRepository = placementRepository;
        this.realEstateRepository = realEstateRepository;
        this.oneOffExpenseRepository = oneOffExpenseRepository;
        this.transferRepository = transferRepository;
        this.variableIncomeRepository = variableIncomeRepository;
        this.variableOverrideRepository = variableOverrideRepository;
        this.taxChildRepository = taxChildRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.taxRateOverrideRepository = taxRateOverrideRepository;
        this.taxActualOverrideRepository = taxActualOverrideRepository;
        this.assetCategoryRepository = assetCategoryRepository;
        this.retirementRepository = retirementRepository;
        this.bankImportRepository = bankImportRepository;
        this.loanRepository = loanRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.gateway = new BudgetPersistenceGateway(
                budgetDataRepository, incomeRepository, chargeRepository, placementRepository,
                realEstateRepository, oneOffExpenseRepository, transferRepository,
                variableIncomeRepository, variableOverrideRepository, taxChildRepository,
                taxBracketRepository, taxRateOverrideRepository, taxActualOverrideRepository,
                assetCategoryRepository, bankImportRepository, loanRepository);
        this.cacheStore = new BudgetCacheStore(this.gateway, this.transactionTemplate);
        this.mutationService = new BudgetMutationService(this.cacheStore);
    }

    @PostConstruct
    public void init() {
        cacheStore.init();
    }

    /**
     * Récupère le modèle de budget complet. Délègue à {@link BudgetCacheStore#getBudgetData}.
     */
    public BudgetDataModel getBudgetData() {
        return cacheStore.getBudgetData();
    }

    /**
     * Remplace l'intégralité du modèle de données (utilisé lors de l'import JSON). Délègue à
     * {@link BudgetCacheStore#setBudgetData}.
     */
    public void setBudgetData(BudgetDataModel data) {
        cacheStore.setBudgetData(data);
    }

    /**
     * Réinitialise les données aux valeurs par défaut. Délègue à
     * {@link BudgetCacheStore#resetData}.
     */
    public BudgetDataModel resetData() {
        return cacheStore.resetData();
    }

    /**
     * Ajoute une nouvelle ligne dans une section de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        return mutationService.addTresorerieRow(listKey, body);
    }

    /**
     * Met à jour une cellule d'une ligne de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        mutationService.updateTresorerieRow(listKey, id, field, value);
    }

    /**
     * Supprime une ligne d'une section de trésorerie.
     */
    public void removeTresorerieRow(String listKey, String id) {
        mutationService.removeTresorerieRow(listKey, id);
    }

    /**
     * Applique un ajustement (ex. suite à un virement automatique) sur le montant mensuel
     * d'une ligne de trésorerie.
     */
    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        mutationService.applyTresorerieAjustement(lineId, kind, newMonthly);
    }

    /**
     * Ajoute ou met à jour une ligne de patrimoine (real estate, placements, loans).
     */
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        return mutationService.savePatrimoineRow(listKey, body);
    }

    /**
     * Met à jour les données de retraite.
     */
    public void updateRetirement(RetirementModel retirement) {
        mutationService.updateRetirement(retirement);
    }

    /**
     * Met à jour la configuration d'impôts.
     */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        mutationService.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
    }

    /**
     * Met à jour un paramètre lié aux impôts ou généraux dans Settings.
     */
    public void updateTaxSettings(String field, Object value) {
        mutationService.updateTaxSettings(field, value);
    }

    /**
     * Met à jour un champ d'une catégorie d'actif.
     */
    public void updateAssetCategory(String id, String field, Object value) {
        mutationService.updateAssetCategory(id, field, value);
    }

    /**
     * Ajoute une nouvelle catégorie d'actif.
     */
    public void addAssetCategory(AssetCategoryModel category) {
        mutationService.addAssetCategory(category);
    }

    /**
     * Supprime une catégorie d'actif.
     */
    public void removeAssetCategory(String id) {
        mutationService.removeAssetCategory(id);
    }

    /**
     * Réinitialise les tranches d'imposition par défaut.
     */
    public void resetDefaultTaxBrackets() {
        mutationService.resetDefaultTaxBrackets();
    }

    /**
     * Supprime une ligne de patrimoine (real estate, placements, loans).
     */
    public void deletePatrimoineRow(String listKey, String id) {
        mutationService.deletePatrimoineRow(listKey, id);
    }

    /**
     * Ajoute une entrée d'historique de valorisation à un placement.
     */
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        return mutationService.addPlacementHistoryEntry(placementId, body);
    }

    /**
     * Met à jour une entrée d'historique de valorisation d'un placement.
     */
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        return mutationService.updatePlacementHistoryEntry(placementId, entryId, body);
    }

    /**
     * Supprime une entrée d'historique de valorisation d'un placement.
     */
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        mutationService.deletePlacementHistoryEntry(placementId, entryId);
    }

    /**
     * Obtient les données d'import bancaire.
     */
    public BankImportModel getBankImport() {
        return mutationService.getBankImport();
    }

    /**
     * Met à jour les données d'import bancaire.
     */
    public void updateBankImport(BankImportModel bankImport) {
        mutationService.updateBankImport(bankImport);
    }
}
