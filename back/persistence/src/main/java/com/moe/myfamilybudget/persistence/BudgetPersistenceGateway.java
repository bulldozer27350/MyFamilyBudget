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
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
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
import com.moe.myfamilybudget.persistence.converter.BankImportDocumentMapper;
import com.moe.myfamilybudget.persistence.converter.CashflowEntityMapper;
import com.moe.myfamilybudget.persistence.converter.CreditLoanEntityMapper;
import com.moe.myfamilybudget.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.persistence.converter.FiscalEntityMapper;
import com.moe.myfamilybudget.persistence.converter.GoalEntityMapper;
import com.moe.myfamilybudget.persistence.converter.PensionEntityMapper;
import com.moe.myfamilybudget.persistence.converter.WealthEntityMapper;
import com.moe.myfamilybudget.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowChargeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowOneOffRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowTransferRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.CashflowVariableOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.persistence.repository.CreditLoanRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.persistence.repository.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.persistence.repository.VariableOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.WealthCategoryRepository;
import com.moe.myfamilybudget.persistence.repository.WealthPlacementRepository;
import com.moe.myfamilybudget.persistence.repository.WealthRealEstateRepository;

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
    private final LoanRepository loanRepository;
    // DB-1021 / DB-1120 : tables autonomes du domaine Objectifs, alimentees en ecriture a chaque sauvegarde du
    // modele et seule source de chargement du cache (le hub ne porte plus les objectifs).
    private final GoalRepository goalRepository;
    // DB-1041 : table autonome du domaine Credit, meme principe que goalRepository.
    private final CreditLoanRepository creditLoanRepository;
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
                              LoanRepository loanRepository,
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
                              CashflowIncomeRepository cashflowIncomeRepository,
                              CashflowChargeRepository cashflowChargeRepository,
                              CashflowOneOffRepository cashflowOneOffRepository,
                              CashflowTransferRepository cashflowTransferRepository,
                              CashflowVariableIncomeRepository cashflowVariableIncomeRepository,
                              CashflowVariableOverrideRepository cashflowVariableOverrideRepository) {
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
        this.loanRepository = loanRepository;
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
        this.cashflowIncomeRepository = cashflowIncomeRepository;
        this.cashflowChargeRepository = cashflowChargeRepository;
        this.cashflowOneOffRepository = cashflowOneOffRepository;
        this.cashflowTransferRepository = cashflowTransferRepository;
        this.cashflowVariableIncomeRepository = cashflowVariableIncomeRepository;
        this.cashflowVariableOverrideRepository = cashflowVariableOverrideRepository;
    }

    /**
     * Supprime la ligne {@code budget_data} existante (et, par cascade, ses entités associées).
     */
    void deleteAll() {
        budgetDataRepository.deleteAll();
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
            // DB-1041 : idem pour la table Credit.
            syncCreditLoans(loaded.loans());
            // DB-1051 : idem pour les tables Patrimoine.
            syncWealth(loaded);
            // DB-1061 : idem pour les tables Tresorerie.
            syncCashflow(loaded);
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
                loaded.loans(),
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
        budgetDataRepository.deleteAll();

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
        saveLoans(model.loans(), entity);
        syncGoals(model.objectifs());
        syncCreditLoans(model.loans());
        syncFiscal(model);
        syncPension(model.retirement());
        syncBankImport(model.bankImport());
        syncWealth(model);
        syncCashflow(model);
    }

    private void saveLoans(List<LoanModel> loans, BudgetDataEntity budgetData) {
        loanRepository.deleteByBudgetDataId(budgetData.getId());
        if (loans != null) {
            for (LoanModel loan : loans) {
                loanRepository.save(EntityModelConverter.toEntity(loan, budgetData));
            }
        }
    }

    /**
     * DB-1021 : remplace le contenu des tables {@code goal} / {@code goal_allocation} par les objectifs du
     * modele, dans la transaction de l'appelant. Le {@code flush} apres la suppression est indispensable :
     * Hibernate execute les insertions avant les suppressions, ce qui violerait la cle primaire (identifiant
     * metier conserve) lors du remplacement d'un objectif existant. Un objectif sans identifiant ne peut etre
     * adresse par aucune API : il reste dans le cache et le hub, mais n'est pas copie dans les nouvelles tables.
     */
    private void syncGoals(List<ObjectifModel> objectifs) {
        goalRepository.deleteAll();
        goalRepository.flush();
        if (objectifs == null || objectifs.isEmpty()) {
            return;
        }
        List<ObjectifModel> identified = objectifs.stream()
                .filter(o -> o != null && o.id() != null)
                .toList();
        if (identified.size() != objectifs.size()) {
            LOG.warn("{} objectif(s) sans identifiant ignore(s) lors de la synchronisation des tables Objectifs",
                    objectifs.size() - identified.size());
        }
        goalRepository.saveAll(GoalEntityMapper.toEntities(identified));
    }

    /**
     * DB-1041 : remplace le contenu de la table {@code credit_loan} par les prets du modele, dans la
     * transaction de l'appelant. Meme contrainte que {@link #syncGoals} : {@code flush} apres la suppression
     * pour eviter la violation de la cle primaire metier. Un pret sans identifiant reste dans le cache et le
     * hub mais n'est pas copie dans la nouvelle table.
     */
    private void syncCreditLoans(List<LoanModel> loans) {
        creditLoanRepository.deleteAll();
        creditLoanRepository.flush();
        if (loans == null || loans.isEmpty()) {
            return;
        }
        List<LoanModel> identified = loans.stream()
                .filter(l -> l != null && l.id() != null)
                .toList();
        if (identified.size() != loans.size()) {
            LOG.warn("{} pret(s) sans identifiant ignore(s) lors de la synchronisation de la table Credit",
                    loans.size() - identified.size());
        }
        creditLoanRepository.saveAll(CreditLoanEntityMapper.toEntities(identified));
    }

    /**
     * DB-1011 : remplace le contenu des quatre tables {@code fiscal_*} par la fiscalite du modele, dans la
     * transaction de l'appelant ({@code flush} apres les suppressions, comme {@link #syncGoals}). Le bareme
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
     * l'appelant ({@code flush} apres la suppression, comme {@link #syncGoals}). Une retraite absente du modele
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
     * DB-1031 : remplace le document de la table {@code bank_import_document} par l'import bancaire du modele,
     * dans la transaction de l'appelant ({@code flush} apres la suppression, comme {@link #syncGoals}). Une erreur
     * de serialisation ou d'ecriture est propagee a l'appelant (FIX-010) : une ecriture en echec ne doit jamais
     * devenir une reussite en memoire.
     */
    private void syncBankImport(BankImportModel bankImport) {
        bankImportDocumentRepository.deleteAll();
        bankImportDocumentRepository.flush();
        if (bankImport == null) {
            return;
        }
        bankImportDocumentRepository.save(BankImportDocumentMapper.toEntity(bankImport));
    }

    /**
     * DB-1051 : remplace le contenu des tables {@code wealth_*} (placements avec leur historique, biens
     * immobiliers, categories d'actifs) par le patrimoine du modele, dans la transaction de l'appelant
     * ({@code flush} apres les suppressions, comme {@link #syncGoals}). Les listes sont copiees telles que le
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
     * DB-1061 : remplace le contenu des six tables {@code cashflow_*} (revenus, charges, depenses ponctuelles,
     * virements, revenus variables, surcharges annuelles) par les lignes du modele, dans la transaction de
     * l'appelant ({@code flush} apres les suppressions, comme {@link #syncGoals}). Les listes sont copiees telles
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
     * comme absent (l'appelant fournit alors un import vide) ; la lecture par {@code BankPersistenceAdapter}
     * propage en revanche l'erreur (DB-1031).
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
