package com.moe.myfamilybudget.server.internal.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.moe.myfamilybudget.server.internal.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.server.internal.persistence.entity.BankImportEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BankImportRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.ChargeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.IncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.LoanRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.OneOffExpenseRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxActualOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxBracketRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxChildRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TaxRateOverrideRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.TransferRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableIncomeRepository;
import com.moe.myfamilybudget.server.internal.persistence.repository.VariableOverrideRepository;

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
 * {@code PersistenceManager} dans ses deux constructeurs (constructeur de test à repositories
 * {@code null}, et constructeur {@code @Autowired}), exactement comme
 * {@code PersistenceManager} l'était déjà pour les appelants. Cela évite d'introduire une
 * nouvelle dépendance Spring à câbler, et préserve strictement les points d'entrée de test
 * existants ({@code new PersistenceManager()}).
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
    private final TaxChildRepository taxChildRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final TaxRateOverrideRepository taxRateOverrideRepository;
    private final TaxActualOverrideRepository taxActualOverrideRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final BankImportRepository bankImportRepository;
    private final LoanRepository loanRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    BudgetPersistenceGateway(BudgetDataRepository budgetDataRepository,
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
                              BankImportRepository bankImportRepository,
                              LoanRepository loanRepository) {
        this.budgetDataRepository = budgetDataRepository;
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
        this.bankImportRepository = bankImportRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * Indique si une base de données est réellement configurée (faux en mode test, où
     * {@code PersistenceManager} est construit via son constructeur sans argument et tous les
     * repositories valent {@code null} : le cache fonctionne alors en mémoire pure).
     */
    boolean hasDatabase() {
        return budgetDataRepository != null;
    }

    /**
     * Supprime la ligne {@code budget_data} existante (et, par cascade, ses entités associées).
     * Ne fait rien en mode test sans base.
     */
    void deleteAll() {
        if (budgetDataRepository != null) {
            budgetDataRepository.deleteAll();
        }
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
        if (budgetDataRepository == null) {
            return null;
        }
        Optional<BudgetDataEntity> existingData = budgetDataRepository.findFirstByOrderByIdAsc();
        return existingData.map(this::loadCompleteBudgetData).orElse(null);
    }

    private BudgetDataModel loadCompleteBudgetData(BudgetDataEntity entity) {
        BudgetDataModel loaded = EntityModelConverter.toModel(entity);
        BankImportModel bi = loadBankImport(entity.getId());
        return new BudgetDataModel(
                loaded.settings(), loaded.incomes(), loaded.charges(), loaded.placements(),
                loaded.realEstate(), loaded.retirement(), loaded.taxChildren(), loaded.taxBrackets(),
                loaded.taxRateOverrides(), loaded.taxActualOverrides(), loaded.oneoff(),
                loaded.transfers(), loaded.variableIncomes(), loaded.variableOverrides(),
                bi != null ? bi : new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                loaded.assetCategories(),
                loaded.loans()
        );
    }

    /**
     * Persiste l'intégralité du modèle en base (stratégie delete-all + réinsertion complète —
     * voir le point 7 de l'audit pour l'alternative incrémentale envisagée). Ne fait rien si
     * {@code budgetDataRepository} est {@code null} (mode test en mémoire, sans base).
     */
    void save(BudgetDataModel model) {
        // Fallback for testing when repositories are null
        if (budgetDataRepository == null) {
            return;
        }

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

        // NOTE: settings/retirement ne doivent PAS être sauvegardés séparément ici.
        // Ils sont rattachés à `entity` (relations @OneToOne en CascadeType.ALL) et seront
        // persistés automatiquement par le save() ci-dessous, dans la MÊME transaction/
        // persistence context. Les sauvegarder au préalable via leur propre repository
        // les détache du contexte de persistance (chaque appel de repository Spring Data
        // s'exécute dans sa propre transaction), ce qui provoque ensuite un
        // "PersistentObjectException: detached entity passed to persist" lors du
        // cascade effectué par budgetDataRepository.save(entity) - en particulier au
        // démarrage de l'application (@PostConstruct init()), qui s'exécute hors de toute
        // transaction Spring.

        // Save the main entity (cascade ALL persiste settings/retirement automatiquement)
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
        saveTaxChildren(model.taxChildren(), entity);
        saveTaxBrackets(model.taxBrackets(), entity);
        saveTaxRateOverrides(model.taxRateOverrides(), entity);
        saveTaxActualOverrides(model.taxActualOverrides(), entity);
        saveAssetCategories(model.assetCategories(), entity);
        saveLoans(model.loans(), entity);
        saveBankImport(model.bankImport(), entity);
    }

    private void saveLoans(List<LoanModel> loans, BudgetDataEntity budgetData) {
        if (loanRepository == null) return;
        loanRepository.deleteByBudgetDataId(budgetData.getId());
        if (loans != null) {
            for (LoanModel loan : loans) {
                loanRepository.save(EntityModelConverter.toEntity(loan, budgetData));
            }
        }
    }

    private BankImportModel loadBankImport(Long budgetDataId) {
        if (bankImportRepository == null || budgetDataId == null) return null;
        Optional<BankImportEntity> biEntity = bankImportRepository.findFirstByBudgetDataId(budgetDataId);
        if (biEntity.isPresent() && biEntity.get().getJsonData() != null && !biEntity.get().getJsonData().isBlank()) {
            try {
                return objectMapper.readValue(biEntity.get().getJsonData(), BankImportModel.class);
            } catch (Exception e) {
                LOG.error("Erreur lors de la lecture de BankImport depuis la base: ", e);
            }
        }
        return null;
    }

    private void saveBankImport(BankImportModel bankImport, BudgetDataEntity budgetData) {
        if (bankImportRepository == null || budgetData == null) return;
        bankImportRepository.deleteByBudgetDataId(budgetData.getId());
        if (bankImport != null) {
            try {
                String json = objectMapper.writeValueAsString(bankImport);
                BankImportEntity biEntity = new BankImportEntity(json);
                biEntity.setBudgetData(budgetData);
                bankImportRepository.save(biEntity);
            } catch (Exception e) {
                LOG.error("Erreur lors de la sauvegarde de BankImport dans la base: ", e);
            }
        }
    }

    private void saveIncomes(List<IncomeModel> incomes, BudgetDataEntity budgetData) {
        if (incomeRepository == null) return;
        incomeRepository.deleteByBudgetDataId(budgetData.getId());
        for (IncomeModel income : incomes) {
            incomeRepository.save(EntityModelConverter.toEntity(income, budgetData));
        }
    }

    private void saveCharges(List<ChargeModel> charges, BudgetDataEntity budgetData) {
        if (chargeRepository == null) return;
        chargeRepository.deleteByBudgetDataId(budgetData.getId());
        for (ChargeModel charge : charges) {
            chargeRepository.save(EntityModelConverter.toEntity(charge, budgetData));
        }
    }

    private void savePlacements(List<PlacementModel> placements, BudgetDataEntity budgetData) {
        if (placementRepository == null) return;
        placementRepository.deleteByBudgetDataId(budgetData.getId());
        for (PlacementModel placement : placements) {
            placementRepository.save(EntityModelConverter.toEntity(placement, budgetData));
        }
    }

    private void saveRealEstate(List<RealEstateModel> realEstate, BudgetDataEntity budgetData) {
        if (realEstateRepository == null) return;
        realEstateRepository.deleteByBudgetDataId(budgetData.getId());
        for (RealEstateModel re : realEstate) {
            realEstateRepository.save(EntityModelConverter.toEntity(re, budgetData));
        }
    }

    private void saveOneOffExpenses(List<OneOffExpenseModel> oneoff, BudgetDataEntity budgetData) {
        if (oneOffExpenseRepository == null) return;
        oneOffExpenseRepository.deleteByBudgetDataId(budgetData.getId());
        for (OneOffExpenseModel expense : oneoff) {
            oneOffExpenseRepository.save(EntityModelConverter.toEntity(expense, budgetData));
        }
    }

    private void saveTransfers(List<TransferModel> transfers, BudgetDataEntity budgetData) {
        if (transferRepository == null) return;
        transferRepository.deleteByBudgetDataId(budgetData.getId());
        for (TransferModel transfer : transfers) {
            transferRepository.save(EntityModelConverter.toEntity(transfer, budgetData));
        }
    }

    private void saveVariableIncomes(List<VariableIncomeModel> variableIncomes, BudgetDataEntity budgetData) {
        if (variableIncomeRepository == null) return;
        variableIncomeRepository.deleteByBudgetDataId(budgetData.getId());
        for (VariableIncomeModel vi : variableIncomes) {
            variableIncomeRepository.save(EntityModelConverter.toEntity(vi, budgetData));
        }
    }

    private void saveVariableOverrides(List<VariableOverrideModel> variableOverrides, BudgetDataEntity budgetData) {
        if (variableOverrideRepository == null) return;
        variableOverrideRepository.deleteByBudgetDataId(budgetData.getId());
        for (VariableOverrideModel vo : variableOverrides) {
            variableOverrideRepository.save(EntityModelConverter.toEntity(vo, budgetData));
        }
    }

    private void saveTaxChildren(List<TaxChildModel> taxChildren, BudgetDataEntity budgetData) {
        if (taxChildRepository == null) return;
        taxChildRepository.deleteByBudgetDataId(budgetData.getId());
        for (TaxChildModel tc : taxChildren) {
            taxChildRepository.save(EntityModelConverter.toEntity(tc, budgetData));
        }
    }

    private void saveTaxBrackets(List<TaxBracketModel> taxBrackets, BudgetDataEntity budgetData) {
        if (taxBracketRepository == null) return;
        taxBracketRepository.deleteByBudgetDataId(budgetData.getId());
        for (TaxBracketModel tb : taxBrackets) {
            taxBracketRepository.save(EntityModelConverter.toEntity(tb, budgetData));
        }
    }

    private void saveTaxRateOverrides(List<TaxRateOverrideModel> taxRateOverrides, BudgetDataEntity budgetData) {
        if (taxRateOverrideRepository == null) return;
        taxRateOverrideRepository.deleteByBudgetDataId(budgetData.getId());
        for (TaxRateOverrideModel tro : taxRateOverrides) {
            taxRateOverrideRepository.save(EntityModelConverter.toEntity(tro, budgetData));
        }
    }

    private void saveTaxActualOverrides(List<TaxActualOverrideModel> taxActualOverrides, BudgetDataEntity budgetData) {
        if (taxActualOverrideRepository == null) return;
        taxActualOverrideRepository.deleteByBudgetDataId(budgetData.getId());
        for (TaxActualOverrideModel tao : taxActualOverrides) {
            taxActualOverrideRepository.save(EntityModelConverter.toEntity(tao, budgetData));
        }
    }

    private void saveAssetCategories(List<AssetCategoryModel> assetCategories, BudgetDataEntity budgetData) {
        if (assetCategoryRepository == null) return;
        assetCategoryRepository.deleteByBudgetDataId(budgetData.getId());
        for (AssetCategoryModel ac : assetCategories) {
            assetCategoryRepository.save(EntityModelConverter.toEntity(ac, budgetData));
        }
    }
}
