package com.moe.myfamilybudget.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.persistence.migration.LegacyObjectifAllocationMigrator;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentMapper;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowEntityMapper;
import com.moe.myfamilybudget.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalEntityMapper;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntityMapper;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionEntityMapper;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthEntityMapper;
import com.moe.myfamilybudget.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsMapper;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsMapper;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsMapper;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.VariableOverrideRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * Passerelle vers la couche JPA : seule classe qui parle directement aux repositories Spring Data.
 *
 * Premier incrément du Strangler Fig prévu par le point 6 de l'audit (God Class
 * {@code PersistenceManager}, 1512 lignes). Cette classe reprend, à l'identique, la logique de
 * sauvegarde/chargement qui vivait auparavant dans {@code PersistenceManager}
 * ({@code saveToDatabase}, les {@code saveXxx} par collection, {@code loadBankImport},
 * {@code saveBankImport}, la reconstruction du modèle complet depuis les entités JPA) : aucun
 * changement de comportement fonctionnel, uniquement un déplacement de code.
 *
 * Volontairement une classe simple (pas un bean Spring) : elle est instanciée directement par
 * {@code PersistenceManager} dans son constructeur {@code @Autowired}, exactement comme
 * {@code PersistenceManager} l'était déjà pour les appelants. Cela évite d'introduire une
 * nouvelle dépendance Spring à câbler.
 *
 * Les repositories reçus sont toujours pleinement fonctionnels (injectés par Spring en
 * production, ou mockés par {@code PersistenceManagerTestFactory} dans les tests unitaires —
 * point 10 de l'audit) : aucune méthode de cette classe n'a donc plus besoin de tolérer un
 * repository {@code null}.
 *
 * Ce qui reste dans {@code PersistenceManager} pour l'instant (prochains incréments du point 6) :
 * le cache mémoire ({@code AtomicReference}, {@code mutationLock}, {@code applyAndPersist}) et la
 * logique métier des mutations ({@code updateXxx}/{@code removeXxx}/dispatcher du point 3).
 */
class BudgetPersistenceGateway {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetPersistenceGateway.class);

    private final BudgetDataRepository budgetDataRepository;
    private final IncomeRepository incomeRepository;
    private final ChargeRepository chargeRepository;
    private final PlacementRepository placementRepository;
    private final RealEstateRepository realEstateRepository;
    private final OneOffExpenseRepository oneOffExpenseRepository;
    private final TransferRepository transferRepository;
    private final VariableIncomeRepository variableIncomeRepository;
    private final VariableOverrideRepository variableOverrideRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    // DB-1120 : tables autonomes du domaine Objectifs, seule source de chargement du cache (le hub ne porte plus
    // les objectifs). SILO-212 (lot B1) : elles ne sont plus alimentees ici, le silo les ecrit directement
    // (JpaGoalStore) ; la copie des objectifs portee par le cache n'est plus autoritative ni consommee.
    private final GoalRepository goalRepository;
    // DB-1011 : tables autonomes du domaine Fiscalite, meme principe que goalRepository.
    private final FiscalChildRepository fiscalChildRepository;
    private final FiscalBracketRepository fiscalBracketRepository;
    private final FiscalRateOverrideRepository fiscalRateOverrideRepository;
    private final FiscalActualOverrideRepository fiscalActualOverrideRepository;
    // DB-1001 : tables autonomes du domaine Retraite, meme principe que goalRepository.
    private final PensionPlanRepository pensionPlanRepository;
    // DB-1031 : table autonome du domaine Banque (document JSON), meme principe que goalRepository.
    private final BankImportDocumentRepository bankImportDocumentRepository;
    // DB-1051 : tables autonomes du domaine Patrimoine (placements, immobilier, categories), meme principe
    // que goalRepository.
    private final WealthPlacementRepository wealthPlacementRepository;
    private final WealthRealEstateRepository wealthRealEstateRepository;
    private final WealthCategoryRepository wealthCategoryRepository;
    // DB-1061 : tables autonomes du domaine Tresorerie (revenus, charges, ponctuels, virements, revenus
    // variables et surcharges), meme principe que goalRepository.
    private final CashflowIncomeRepository cashflowIncomeRepository;
    private final CashflowChargeRepository cashflowChargeRepository;
    private final CashflowOneOffRepository cashflowOneOffRepository;
    private final CashflowTransferRepository cashflowTransferRepository;
    private final CashflowVariableIncomeRepository cashflowVariableIncomeRepository;
    private final CashflowVariableOverrideRepository cashflowVariableOverrideRepository;
    // SILO-220 (lot B) : tables de parametres chez leurs proprietaires (Retraite, Fiscalite, Tresorerie),
    // alimentees en parallele de `settings` (meme principe que goalRepository avant SILO-212). R-50 : la table du
    // silo Parametres (`app_settings`) n'est plus alimentee ici, elle est ecrite directement par son silo.
    private final PensionSettingsRepository pensionSettingsRepository;
    private final FiscalSettingsRepository fiscalSettingsRepository;
    private final CashflowSettingsRepository cashflowSettingsRepository;

    BudgetPersistenceGateway(BudgetDataRepository budgetDataRepository,
                              IncomeRepository incomeRepository,
                              ChargeRepository chargeRepository,
                              PlacementRepository placementRepository,
                              RealEstateRepository realEstateRepository,
                              OneOffExpenseRepository oneOffExpenseRepository,
                              TransferRepository transferRepository,
                              VariableIncomeRepository variableIncomeRepository,
                              VariableOverrideRepository variableOverrideRepository,
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
                              CashflowIncomeRepository cashflowIncomeRepository,
                              CashflowChargeRepository cashflowChargeRepository,
                              CashflowOneOffRepository cashflowOneOffRepository,
                              CashflowTransferRepository cashflowTransferRepository,
                              CashflowVariableIncomeRepository cashflowVariableIncomeRepository,
                              CashflowVariableOverrideRepository cashflowVariableOverrideRepository,
                              PensionSettingsRepository pensionSettingsRepository,
                              FiscalSettingsRepository fiscalSettingsRepository,
                              CashflowSettingsRepository cashflowSettingsRepository) {
        this.budgetDataRepository = budgetDataRepository;
        this.incomeRepository = incomeRepository;
        this.chargeRepository = chargeRepository;
        this.placementRepository = placementRepository;
        this.realEstateRepository = realEstateRepository;
        this.oneOffExpenseRepository = oneOffExpenseRepository;
        this.transferRepository = transferRepository;
        this.variableIncomeRepository = variableIncomeRepository;
        this.variableOverrideRepository = variableOverrideRepository;
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
        this.cashflowIncomeRepository = cashflowIncomeRepository;
        this.cashflowChargeRepository = cashflowChargeRepository;
        this.cashflowOneOffRepository = cashflowOneOffRepository;
        this.cashflowTransferRepository = cashflowTransferRepository;
        this.cashflowVariableIncomeRepository = cashflowVariableIncomeRepository;
        this.cashflowVariableOverrideRepository = cashflowVariableOverrideRepository;
        this.pensionSettingsRepository = pensionSettingsRepository;
        this.fiscalSettingsRepository = fiscalSettingsRepository;
        this.cashflowSettingsRepository = cashflowSettingsRepository;
    }

    /**
     * Supprime la ligne {@code budget_data} existante (et, par cascade, ses entités associées).
     */
    void deleteAll() {
        deleteExistingBudgetData();
    }

    /**
     * Supprime les lignes {@code budget_data} existantes ET leurs lignes enfants, dans cet ordre, avec un
     * {@code flush} entre les deux.
     *
     * <p>SILO-119 (lot B2) : un import ou un reset enchaine plusieurs {@code save} dans UNE transaction. Les
     * enfants ecrits par un {@code save} precedent (revenus, charges...) sont rattaches a leur parent par
     * {@code child.setBudgetData(parent)} uniquement : les collections {@code @OneToMany(mappedBy)} du parent
     * restent vides en memoire, donc le {@code CascadeType.ALL} d'un {@code deleteAll()} ulterieur ne les
     * supprime pas. Ils resteraient geres par le contexte de persistance en designant un parent supprime, et
     * le prochain auto-flush levait {@code TransientObjectException}. Supprimer explicitement les enfants
     * d'abord (requete par {@code budget_data_id}, sauf les prets) rend la suppression independante de l'etat des
     * collections en memoire.
     */
    private void deleteExistingBudgetData() {
        for (BudgetDataEntity existing : budgetDataRepository.findAll()) {
            Long id = existing.getId();
            incomeRepository.deleteByBudgetDataId(id);
            chargeRepository.deleteByBudgetDataId(id);
            placementRepository.deleteByBudgetDataId(id);
            realEstateRepository.deleteByBudgetDataId(id);
            oneOffExpenseRepository.deleteByBudgetDataId(id);
            transferRepository.deleteByBudgetDataId(id);
            variableIncomeRepository.deleteByBudgetDataId(id);
            variableOverrideRepository.deleteByBudgetDataId(id);
            assetCategoryRepository.deleteByBudgetDataId(id);
        }
        budgetDataRepository.flush();
        budgetDataRepository.deleteAll();
        budgetDataRepository.flush();
    }

    /**
     * Recharge le modèle complet (y compris l'import bancaire) depuis la base, ou {@code null} si
     * aucune ligne {@code budget_data} n'existe encore.
     *
     * Doit s'exécuter dans une transaction déjà ouverte par l'appelant (le proxy
     * {@code @Transactional} de {@code PersistenceManager} pour un appel applicatif normal, ou le
     * {@code TransactionTemplate} explicite utilisé par {@code PersistenceManager.init()} lors du
     * démarrage — voir le commentaire historique conservé sur ce point dans {@code init()}) : la
     * lecture paresseuse de certaines collections et du blob {@code BankImportEntity.jsonData}
     * l'exige.
     */
    BudgetDataModel loadExistingIfPresent() {
        Optional<BudgetDataEntity> existingData = budgetDataRepository.findFirstByOrderByIdAsc();
        BudgetDataModel loaded = existingData.map(this::loadCompleteBudgetData).orElse(null);
        if (loaded != null) {
            // DB-1051 : idem pour les tables Patrimoine.
            syncWealth(loaded);
            // DB-1061 : idem pour les tables Tresorerie.
            syncCashflow(loaded);
            // SILO-220 (lot B) : migration des parametres de `settings` vers les tables des proprietaires
            // (idempotente : les trois tables sont remplacees par l'etat du hub a chaque demarrage).
            syncSettings(loaded);
        }
        return loaded;
    }

    private BudgetDataModel loadCompleteBudgetData(BudgetDataEntity entity) {
        BudgetDataModel loaded = EntityModelConverter.toModel(entity);
        BankImportModel bi = loadBankImport();
        // DB-1100 : la retraite n'est plus portee par le hub, elle est relue depuis les tables pension_*.
        RetirementModel retirement = PensionEntityMapper.toModel(pensionPlanRepository.findFirstByOrderByIdAsc().orElse(null));
        // DB-1110 : la fiscalite n'est plus portee par le hub, elle est relue depuis les tables fiscal_*.
        List<TaxChildModel> taxChildren = FiscalEntityMapper.toChildModels(fiscalChildRepository.findAllByOrderByPositionAsc());
        List<TaxBracketModel> taxBrackets = FiscalEntityMapper.toBracketModels(fiscalBracketRepository.findAllByOrderByPositionAsc());
        List<TaxRateOverrideModel> taxRateOverrides =
                FiscalEntityMapper.toRateOverrideModels(fiscalRateOverrideRepository.findAllByOrderByPositionAsc());
        List<TaxActualOverrideModel> taxActualOverrides =
                FiscalEntityMapper.toActualOverrideModels(fiscalActualOverrideRepository.findAllByOrderByPositionAsc());
        // DB-1120 : les objectifs ne sont plus portes par le hub, ils sont relus depuis les tables goal_*. Le filet
        // LegacyObjectifAllocationMigrator reste applique au chargement du cache, comme avant (sans effet sur un
        // objectif deja porteur d'allocations).
        List<ObjectifModel> objectifs = GoalEntityMapper.toModels(goalRepository.findAllByOrderByPositionAsc()).stream()
                .map(LegacyObjectifAllocationMigrator::migrate)
                .toList();
        return new BudgetDataModel(
                loaded.settings(), loaded.incomes(), loaded.charges(), loaded.placements(),
                loaded.realEstate(), retirement, taxChildren, taxBrackets,
                taxRateOverrides, taxActualOverrides, loaded.oneoff(),
                loaded.transfers(), loaded.variableIncomes(), loaded.variableOverrides(),
                bi != null ? bi : new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                loaded.assetCategories(),
                List.of(), // prets : portes par le silo Credit (credit_loan), plus par le cache (SILO-214)
                objectifs
        );
    }

    /**
     * Persiste l'intégralité du modèle en base (stratégie delete-all + réinsertion complète —
     * voir le point 7 de l'audit pour l'alternative incrémentale envisagée).
     */
    void save(BudgetDataModel model) {
        // IMPORTANT : EntityModelConverter.toEntity(model) renvoie toujours une entité
        // avec id=null (voir son commentaire "Lists will be set separately"). Sans ce
        // deleteAll() préalable, Hibernate ferait donc un INSERT à chaque appel de
        // save() (édition d'une ligne, import JSON...) au lieu d'un UPDATE, créant une
        // nouvelle ligne budget_data à chaque sauvegarde. Après un redémarrage,
        // PersistenceManager.init() relit via findFirstByOrderByIdAsc(), qui renvoie
        // l'id le plus petit — donc la toute première ligne (souvent vide) — au lieu de
        // la dernière version sauvegardée. On supprime l'existant avant de réinsérer
        // (même mécanisme que PersistenceManager.resetData()) pour garantir qu'une seule
        // ligne budget_data existe à tout moment.
        deleteExistingBudgetData();

        BudgetDataEntity entity = EntityModelConverter.toEntity(model);

        // NOTE: settings ne doit PAS être sauvegardé séparément ici (DB-1100 : la retraite n'est plus
        // rattachée au hub, voir syncPension). Il est rattaché à `entity` (relation @OneToOne en CascadeType.ALL) et sera
        // persistés automatiquement par le save() ci-dessous, dans la MÊME transaction/
        // persistence context. Les sauvegarder au préalable via leur propre repository
        // les détache du contexte de persistance (chaque appel de repository Spring Data
        // s'exécute dans sa propre transaction), ce qui provoque ensuite un
        // "PersistentObjectException: detached entity passed to persist" lors du
        // cascade effectué par budgetDataRepository.save(entity) - en particulier au
        // démarrage de l'application (@PostConstruct init()), qui s'exécute hors de toute
        // transaction Spring.

        // Save the main entity (cascade ALL persiste settings automatiquement)
        entity = budgetDataRepository.save(entity);

        // Save all child entities with proper parent references
        saveIncomes(model.incomes(), entity);
        saveCharges(model.charges(), entity);
        savePlacements(model.placements(), entity);
        saveRealEstate(model.realEstate(), entity);
        saveOneOffExpenses(model.oneoff(), entity);
        saveTransfers(model.transfers(), entity);
        saveVariableIncomes(model.variableIncomes(), entity);
        saveVariableOverrides(model.variableOverrides(), entity);
        saveAssetCategories(model.assetCategories(), entity);
        syncFiscal(model);
        syncPension(model.retirement());
        syncWealth(model);
        syncCashflow(model);
        syncSettings(model);
    }

    /**
     * DB-1011 : remplace le contenu des quatre tables {@code fiscal_*} par la fiscalite du modele, dans la
     * transaction de l'appelant ({@code flush} apres les suppressions, comme les autres synchronisations). Le bareme
     * copie est le bareme <em>effectif</em> : si la liste du modele est vide, le bareme par defaut est ecrit,
     * de sorte que la lecture JPA restitue exactement ce que le cache expose.
     */
    private void syncFiscal(BudgetDataModel model) {
        fiscalChildRepository.deleteAll();
        fiscalBracketRepository.deleteAll();
        fiscalRateOverrideRepository.deleteAll();
        fiscalActualOverrideRepository.deleteAll();
        fiscalChildRepository.flush();
        fiscalBracketRepository.flush();
        fiscalRateOverrideRepository.flush();
        fiscalActualOverrideRepository.flush();
        fiscalChildRepository.saveAll(FiscalEntityMapper.toChildEntities(model.getEffectiveTaxChildren()));
        fiscalBracketRepository.saveAll(FiscalEntityMapper.toBracketEntities(model.getEffectiveTaxBrackets()));
        fiscalRateOverrideRepository.saveAll(
                FiscalEntityMapper.toRateOverrideEntities(model.getEffectiveTaxRateOverrides()));
        fiscalActualOverrideRepository.saveAll(
                FiscalEntityMapper.toActualOverrideEntities(model.getEffectiveTaxActualOverrides()));
    }

    /**
     * DB-1001 : remplace le contenu des tables {@code pension_*} par la retraite du modele, dans la transaction de
     * l'appelant ({@code flush} apres la suppression, comme les autres synchronisations). Une retraite absente du modele
     * laisse les tables vides : la lecture JPA restitue alors {@code null}, comme le cache.
     */
    private void syncPension(RetirementModel retirement) {
        pensionPlanRepository.deleteAll();
        pensionPlanRepository.flush();
        if (retirement == null) {
            return;
        }
        pensionPlanRepository.save(PensionEntityMapper.toEntity(retirement));
    }

    /**
     * DB-1051 : remplace le contenu des tables {@code wealth_*} (placements avec leur historique, biens
     * immobiliers, categories d'actifs) par le patrimoine du modele, dans la transaction de l'appelant
     * ({@code flush} apres les suppressions, comme les autres synchronisations). Les listes sont copiees telles que le
     * cache les expose ({@code getEffective*}) : la lecture JPA restitue donc exactement le contenu du cache.
     * Les virements ne sont pas concernes : ils relevent de Tresorerie.
     */
    private void syncWealth(BudgetDataModel model) {
        wealthPlacementRepository.deleteAll();
        wealthRealEstateRepository.deleteAll();
        wealthCategoryRepository.deleteAll();
        wealthPlacementRepository.flush();
        wealthRealEstateRepository.flush();
        wealthCategoryRepository.flush();
        wealthPlacementRepository.saveAll(WealthEntityMapper.toPlacementEntities(model.getEffectivePlacements()));
        wealthRealEstateRepository.saveAll(WealthEntityMapper.toRealEstateEntities(model.getEffectiveRealEstate()));
        wealthCategoryRepository.saveAll(WealthEntityMapper.toCategoryEntities(model.getEffectiveAssetCategories()));
    }

    /**
     * SILO-220 (lot B) : remplace le contenu des trois tables de parametres des proprietaires
     * ({@code pension_settings}, {@code fiscal_settings}, {@code cashflow_settings}) par les
     * parametres <em>effectifs</em> du modele, dans la transaction de l'appelant ({@code flush} apres les
     * suppressions, comme les autres synchronisations). Appele au chargement (migration des donnees de la table
     * {@code settings}, qui reste la source) et a chaque sauvegarde (double ecriture). Les valeurs par defaut de
     * {@code getEffectiveSettings()} sont ecrites : une lecture future par ces tables restitue donc exactement ce
     * que le cache expose. Le PASS et son taux restent portes par {@code pension_plan} (SET-040).
     */
    private void syncSettings(BudgetDataModel model) {
        SettingsModel s = model.getEffectiveSettings();
        pensionSettingsRepository.deleteAll();
        fiscalSettingsRepository.deleteAll();
        cashflowSettingsRepository.deleteAll();
        pensionSettingsRepository.flush();
        fiscalSettingsRepository.flush();
        cashflowSettingsRepository.flush();
        pensionSettingsRepository.save(PensionSettingsMapper.toEntity(
                new RetirementSettingsModel(s.birthYear(), s.retireAge())));
        fiscalSettingsRepository.save(FiscalSettingsMapper.toEntity(
                new TaxSettingsModel(s.childExitAge(), s.taxAbattement())));
        cashflowSettingsRepository.save(CashflowSettingsMapper.toEntity(
                new TresorerieSettingsModel(s.pivotDate(), s.pivotMode(), s.startBalance(), s.sweepEnabled(),
                        s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold())));
    }

    /**
     * DB-1061 : remplace le contenu des six tables {@code cashflow_*} (revenus, charges, depenses ponctuelles,
     * virements, revenus variables, surcharges annuelles) par les lignes du modele, dans la transaction de
     * l'appelant ({@code flush} apres les suppressions, comme les autres synchronisations). Les listes sont copiees telles
     * que le cache les expose ({@code getEffective*}) : la lecture JPA restitue donc exactement son contenu.
     */
    private void syncCashflow(BudgetDataModel model) {
        cashflowIncomeRepository.deleteAll();
        cashflowChargeRepository.deleteAll();
        cashflowOneOffRepository.deleteAll();
        cashflowTransferRepository.deleteAll();
        cashflowVariableIncomeRepository.deleteAll();
        cashflowVariableOverrideRepository.deleteAll();
        cashflowIncomeRepository.flush();
        cashflowChargeRepository.flush();
        cashflowOneOffRepository.flush();
        cashflowTransferRepository.flush();
        cashflowVariableIncomeRepository.flush();
        cashflowVariableOverrideRepository.flush();
        cashflowIncomeRepository.saveAll(CashflowEntityMapper.toIncomeEntities(model.getEffectiveIncomes()));
        cashflowChargeRepository.saveAll(CashflowEntityMapper.toChargeEntities(model.getEffectiveCharges()));
        cashflowOneOffRepository.saveAll(CashflowEntityMapper.toOneOffEntities(model.getEffectiveOneoff()));
        cashflowTransferRepository.saveAll(CashflowEntityMapper.toTransferEntities(model.getEffectiveTransfers()));
        cashflowVariableIncomeRepository.saveAll(
                CashflowEntityMapper.toVariableIncomeEntities(model.getEffectiveVariableIncomes()));
        cashflowVariableOverrideRepository.saveAll(
                CashflowEntityMapper.toVariableOverrideEntities(model.getEffectiveVariableOverrides()));
    }

    /**
     * DB-1130 : l'import bancaire n'est plus porte par le hub, il est relu depuis la table autonome
     * {@code bank_import_document}. Comme le chemin legacy, un document illisible est journalise puis restitue
     * comme absent (l'appelant fournit alors un import vide) ; la lecture par {@code JpaBankStore} propage en
     * revanche l'erreur (DB-1031). SILO-213 (lot B) : la table n'est plus alimentee ici, le silo l'ecrit
     * directement ; la copie de l'import portee par le cache n'est plus autoritative ni consommee.
     */
    private BankImportModel loadBankImport() {
        try {
            return BankImportDocumentMapper.toModel(bankImportDocumentRepository.findFirstByOrderByIdAsc().orElse(null));
        } catch (IllegalStateException e) {
            LOG.error("Erreur lors de la lecture de BankImport depuis la base: ", e);
            return null;
        }
    }

    private void saveIncomes(List<IncomeModel> incomes, BudgetDataEntity budgetData) {
        incomeRepository.deleteByBudgetDataId(budgetData.getId());
        for (IncomeModel income : incomes) {
            incomeRepository.save(EntityModelConverter.toEntity(income, budgetData));
        }
    }

    private void saveCharges(List<ChargeModel> charges, BudgetDataEntity budgetData) {
        chargeRepository.deleteByBudgetDataId(budgetData.getId());
        for (ChargeModel charge : charges) {
            chargeRepository.save(EntityModelConverter.toEntity(charge, budgetData));
        }
    }

    private void savePlacements(List<PlacementModel> placements, BudgetDataEntity budgetData) {
        placementRepository.deleteByBudgetDataId(budgetData.getId());
        for (PlacementModel placement : placements) {
            placementRepository.save(EntityModelConverter.toEntity(placement, budgetData));
        }
    }

    private void saveRealEstate(List<RealEstateModel> realEstate, BudgetDataEntity budgetData) {
        realEstateRepository.deleteByBudgetDataId(budgetData.getId());
        for (RealEstateModel re : realEstate) {
            realEstateRepository.save(EntityModelConverter.toEntity(re, budgetData));
        }
    }

    private void saveOneOffExpenses(List<OneOffExpenseModel> oneoff, BudgetDataEntity budgetData) {
        oneOffExpenseRepository.deleteByBudgetDataId(budgetData.getId());
        for (OneOffExpenseModel expense : oneoff) {
            oneOffExpenseRepository.save(EntityModelConverter.toEntity(expense, budgetData));
        }
    }

    private void saveTransfers(List<TransferModel> transfers, BudgetDataEntity budgetData) {
        transferRepository.deleteByBudgetDataId(budgetData.getId());
        for (TransferModel transfer : transfers) {
            transferRepository.save(EntityModelConverter.toEntity(transfer, budgetData));
        }
    }

    private void saveVariableIncomes(List<VariableIncomeModel> variableIncomes, BudgetDataEntity budgetData) {
        variableIncomeRepository.deleteByBudgetDataId(budgetData.getId());
        for (VariableIncomeModel vi : variableIncomes) {
            variableIncomeRepository.save(EntityModelConverter.toEntity(vi, budgetData));
        }
    }

    private void saveVariableOverrides(List<VariableOverrideModel> variableOverrides, BudgetDataEntity budgetData) {
        variableOverrideRepository.deleteByBudgetDataId(budgetData.getId());
        for (VariableOverrideModel vo : variableOverrides) {
            variableOverrideRepository.save(EntityModelConverter.toEntity(vo, budgetData));
        }
    }

    private void saveAssetCategories(List<AssetCategoryModel> assetCategories, BudgetDataEntity budgetData) {
        assetCategoryRepository.deleteByBudgetDataId(budgetData.getId());
        for (AssetCategoryModel ac : assetCategories) {
            assetCategoryRepository.save(EntityModelConverter.toEntity(ac, budgetData));
        }
    }
}
