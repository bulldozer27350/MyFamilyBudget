package com.moe.myfamilybudget.server.internal.persistence;

import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
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
    private final ObjectifRepository objectifRepository;
    private final GoalRepository goalRepository;
    private final CreditLoanRepository creditLoanRepository;
    private final FiscalChildRepository fiscalChildRepository;
    private final FiscalBracketRepository fiscalBracketRepository;
    private final FiscalRateOverrideRepository fiscalRateOverrideRepository;
    private final FiscalActualOverrideRepository fiscalActualOverrideRepository;
    private final PensionPlanRepository pensionPlanRepository;
    private final BankImportDocumentRepository bankImportDocumentRepository;
    private final WealthPlacementRepository wealthPlacementRepository;
    private final WealthRealEstateRepository wealthRealEstateRepository;
    private final WealthCategoryRepository wealthCategoryRepository;

    // Gestion programmatique de la transaction pour l'initialisation au démarrage.
    // Voir le commentaire dans BudgetPersistenceGateway.save() : le @Transactional de classe ne
    // s'applique jamais à un appel émis depuis @PostConstruct (self-invocation avant
    // la création du proxy AOP). On utilise donc un TransactionTemplate explicite
    // pour englober l'unique sauvegarde effectuée pendant init().
    private final TransactionTemplate transactionTemplate;

    // Passerelle JPA (point 6 de l'audit, 1er incrément du Strangler Fig) : concentre tout
    // l'accès direct aux repositories Spring Data. Volontairement pas un bean Spring : une
    // instance est simplement construite ci-dessous, dans le constructeur, avec les mêmes
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
    // la méthode de même nom ici.
    private final BudgetMutationService mutationService;

    // DB-060 : mutations de domaine (ex-methodes de cette classe), atteintes via write/writeAndGet.
    private final DomainMutations domainMutations;

    // Publication d'un BudgetMutatedEvent après chaque mutation réussie (voir publishMutated),
    // consommé par NotificationDispatchService pour déclencher un contrôle des notifications.
    // Bean Spring générique (contexte d'application), aucune dépendance vers le package
    // notification depuis PersistenceManager.
    private final ApplicationEventPublisher eventPublisher;

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
                            ObjectifRepository objectifRepository,
                            GoalRepository goalRepository,
                            CreditLoanRepository creditLoanRepository,
                            FiscalChildRepository fiscalChildRepository,
                            FiscalBracketRepository fiscalBracketRepository,
                            FiscalRateOverrideRepository fiscalRateOverrideRepository,
                            FiscalActualOverrideRepository fiscalActualOverrideRepository,
                            PensionPlanRepository pensionPlanRepository,
                            BankImportDocumentRepository bankImportDocumentRepository,
                            WealthPlacementRepository wealthPlacementRepository,
                            WealthRealEstateRepository wealthRealEstateRepository,
                            WealthCategoryRepository wealthCategoryRepository,
                            PlatformTransactionManager transactionManager,
                            ApplicationEventPublisher eventPublisher) {
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
        this.objectifRepository = objectifRepository;
        this.goalRepository = goalRepository;
        this.creditLoanRepository = creditLoanRepository;
        this.fiscalChildRepository = fiscalChildRepository;
        this.fiscalBracketRepository = fiscalBracketRepository;
        this.fiscalRateOverrideRepository = fiscalRateOverrideRepository;
        this.fiscalActualOverrideRepository = fiscalActualOverrideRepository;
        this.pensionPlanRepository = pensionPlanRepository;
        this.bankImportDocumentRepository = bankImportDocumentRepository;
        this.wealthPlacementRepository = wealthPlacementRepository;
        this.wealthRealEstateRepository = wealthRealEstateRepository;
        this.wealthCategoryRepository = wealthCategoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.gateway = new BudgetPersistenceGateway(
                budgetDataRepository, incomeRepository, chargeRepository, placementRepository,
                realEstateRepository, oneOffExpenseRepository, transferRepository,
                variableIncomeRepository, variableOverrideRepository, taxChildRepository,
                taxBracketRepository, taxRateOverrideRepository, taxActualOverrideRepository,
                assetCategoryRepository, bankImportRepository, loanRepository, objectifRepository,
                goalRepository, creditLoanRepository, fiscalChildRepository, fiscalBracketRepository,
                fiscalRateOverrideRepository, fiscalActualOverrideRepository, pensionPlanRepository,
                bankImportDocumentRepository, wealthPlacementRepository, wealthRealEstateRepository,
                wealthCategoryRepository);
        this.cacheStore = new BudgetCacheStore(this.gateway, this.transactionTemplate);
        this.mutationService = new BudgetMutationService(this.cacheStore);
        this.eventPublisher = eventPublisher;
        this.domainMutations = new DomainMutations(this.mutationService, eventPublisher);
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
     * VT-350b : prend, pour toute la durée de la transaction en cours, le verrou de mutation du
     * budget. À appeler en premier par une façade {@code @Transactional} qui écrit dans plusieurs
     * domaines, avant sa première écriture (voir {@link BudgetCacheStore#lockForCurrentTransaction}).
     * Sans transaction active, ne fait rien.
     */
    public void lockForCurrentTransaction() {
        cacheStore.lockForCurrentTransaction();
    }

    /**
     * Remplace l'intégralité du modèle de données (utilisé lors de l'import JSON). Délègue à
     * {@link BudgetCacheStore#setBudgetData}.
     */
    public void setBudgetData(BudgetDataModel data) {
        cacheStore.setBudgetData(data);
        publishMutated("setBudgetData");
    }

    /**
     * Réinitialise les données aux valeurs par défaut. Délègue à
     * {@link BudgetCacheStore#resetData}.
     */
    public BudgetDataModel resetData() {
        BudgetDataModel result = cacheStore.resetData();
        publishMutated("resetData");
        return result;
    }

    /**
     * Ecrit dans un domaine (DB-060). Point d'entree transactionnel unique des mutations de domaine :
     * le {@code @Transactional} de classe englobe l'action, comme avant l'extraction des methodes
     * vers {@link DomainMutations}.
     */
    public void write(Consumer<DomainMutations> action) {
        action.accept(domainMutations);
    }

    /** Variante de {@link #write} pour les mutations qui renvoient la ligne ecrite. */
    public <R> R writeAndGet(Function<DomainMutations, R> action) {
        return action.apply(domainMutations);
    }

    /**
     * Obtient les données d'import bancaire.
     */
    public BankImportModel getBankImport() {
        return mutationService.getBankImport();
    }

    /**
     * Publie un {@link BudgetMutatedEvent} après une mutation réussie, écouté par
     * {@code NotificationDispatchService} (package {@code notification}) après le commit de la
     * transaction en cours pour déclencher un contrôle des règles de notification.
     *
     * @param mutationKind nom de la méthode à l'origine de la mutation, à titre diagnostique
     */
    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new BudgetMutatedEvent(mutationKind));
    }
}
