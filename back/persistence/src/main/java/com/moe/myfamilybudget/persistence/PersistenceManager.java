package com.moe.myfamilybudget.persistence;

import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.*;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;

import jakarta.annotation.PostConstruct;

/**
 * Gestionnaire de persistance avec Spring Data JPA.
 * Les données sont conservées dans une base de données H2 et persistent entre les redémarrages du serveur.
 */
@Component
@DependsOn("legacySchemaCleanup")
@Transactional
public class PersistenceManager {

    private final BudgetDataRepository budgetDataRepository;
    private final SettingsRepository settingsRepository;
    private final PlacementRepository placementRepository;
    private final RealEstateRepository realEstateRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final GoalRepository goalRepository;
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
    // repositories que ceux reçus par PersistenceManager — settingsRepository ne lui
    // est pas transmis car il n'est jamais lu (settings est rattaché à BudgetDataEntity par cascade
    // JPA, voir le commentaire dans BudgetPersistenceGateway.save()).
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

    // R-20 : les lignes de tresorerie (revenus, charges, ponctuels, variables, virements) et leurs parametres
    // vivent uniquement dans les tables cashflow_*, ecrites par le silo Tresorerie. Le cache ne les alimente
    // plus : setBudgetData/resetData doivent donc les confier explicitement au silo. Injection par setter pour
    // ne pas changer le constructeur ; reste null quand le gestionnaire est construit a la main (tests unitaires).
    private TresorerieSnapshotWriter treasurySnapshotWriter;

    @Autowired
    public PersistenceManager(BudgetDataRepository budgetDataRepository,
                            SettingsRepository settingsRepository,
                            PlacementRepository placementRepository,
                            RealEstateRepository realEstateRepository,
                            AssetCategoryRepository assetCategoryRepository,
                            GoalRepository goalRepository,
                            FiscalChildRepository fiscalChildRepository,
                            FiscalBracketRepository fiscalBracketRepository,
                            FiscalRateOverrideRepository fiscalRateOverrideRepository,
                            FiscalActualOverrideRepository fiscalActualOverrideRepository,
                            PensionPlanRepository pensionPlanRepository,
                            BankImportDocumentRepository bankImportDocumentRepository,
                            WealthPlacementRepository wealthPlacementRepository,
                            WealthRealEstateRepository wealthRealEstateRepository,
                            WealthCategoryRepository wealthCategoryRepository,
                            PensionSettingsRepository pensionSettingsRepository,
                            FiscalSettingsRepository fiscalSettingsRepository,
                            CashflowSettingsRepository cashflowSettingsRepository,
                            PlatformTransactionManager transactionManager,
                            ApplicationEventPublisher eventPublisher) {
        this.budgetDataRepository = budgetDataRepository;
        this.settingsRepository = settingsRepository;
        this.placementRepository = placementRepository;
        this.realEstateRepository = realEstateRepository;
        this.assetCategoryRepository = assetCategoryRepository;
        this.goalRepository = goalRepository;
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
                budgetDataRepository, placementRepository,
                realEstateRepository, assetCategoryRepository,
                goalRepository, fiscalChildRepository, fiscalBracketRepository,
                fiscalRateOverrideRepository, fiscalActualOverrideRepository, pensionPlanRepository,
                bankImportDocumentRepository, wealthPlacementRepository, wealthRealEstateRepository,
                wealthCategoryRepository,
                pensionSettingsRepository, fiscalSettingsRepository, cashflowSettingsRepository);
        this.cacheStore = new BudgetCacheStore(this.gateway, this.transactionTemplate);
        this.mutationService = new BudgetMutationService(this.cacheStore);
        this.eventPublisher = eventPublisher;
        this.domainMutations = new DomainMutations(this.mutationService, eventPublisher);
    }

    /** R-20 : silo Tresorerie auquel setBudgetData/resetData delegent les lignes de tresorerie. */
    @Autowired(required = false)
    public void setTreasurySnapshotWriter(TresorerieSnapshotWriter treasurySnapshotWriter) {
        this.treasurySnapshotWriter = treasurySnapshotWriter;
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
        // Le silo d'abord : en cas d'echec, la transaction est annulee et le cache n'est pas touche.
        if (treasurySnapshotWriter != null && data != null) {
            var s = data.getEffectiveSettings();
            treasurySnapshotWriter.replace(
                    new TresorerieSettingsModel(s.pivotDate(), s.pivotMode(), s.startBalance(), s.sweepEnabled(),
                            s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold()),
                    data.getEffectiveIncomes(), data.getEffectiveCharges(), data.getEffectiveOneoff(),
                    data.getEffectiveVariableIncomes(), data.getEffectiveVariableOverrides(),
                    data.getEffectiveTransfers());
        }
        cacheStore.setBudgetData(data);
        publishMutated("setBudgetData");
    }

    /**
     * Réinitialise les données aux valeurs par défaut. Délègue à
     * {@link BudgetCacheStore#resetData}.
     */
    public BudgetDataModel resetData() {
        if (treasurySnapshotWriter != null) {
            treasurySnapshotWriter.reset();
        }
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
