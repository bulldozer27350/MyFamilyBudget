package com.moe.myfamilybudget.server.internal.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.RealEstateModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TransferModel;
import com.moe.myfamilybudget.server.internal.model.VariableIncomeModel;
import com.moe.myfamilybudget.server.internal.model.VariableOverrideModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.server.internal.persistence.entity.BankImportEntity;
import com.moe.myfamilybudget.server.internal.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.*;

import jakarta.annotation.PostConstruct;

/**
 * Gestionnaire de persistance avec Spring Data JPA.
 * Les données sont conservées dans une base de données H2 et persistent entre les redémarrages du serveur.
 */
@Component
@Transactional
public class PersistenceManager {

    private final AtomicReference<BudgetDataModel> currentBudget = new AtomicReference<>();
    
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
    // Voir le commentaire dans saveToDatabase() : le @Transactional de classe ne
    // s'applique jamais à un appel émis depuis @PostConstruct (self-invocation avant
    // la création du proxy AOP). On utilise donc un TransactionTemplate explicite
    // pour englober l'unique sauvegarde effectuée pendant init().
    private final TransactionTemplate transactionTemplate;

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
    }

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    public void init() {
        // Try to load from database first
        if (budgetDataRepository != null) {
            Optional<BudgetDataEntity> existingData = budgetDataRepository.findFirstByOrderByIdAsc();
            if (existingData.isPresent()) {
                BudgetDataModel loaded = EntityModelConverter.toModel(existingData.get());
                BankImportModel bi = loadBankImport(existingData.get().getId());
                BudgetDataModel complete = new BudgetDataModel(
                        loaded.settings(), loaded.incomes(), loaded.charges(), loaded.placements(),
                        loaded.realEstate(), loaded.retirement(), loaded.taxChildren(), loaded.taxBrackets(),
                        loaded.taxRateOverrides(), loaded.taxActualOverrides(), loaded.oneoff(),
                        loaded.transfers(), loaded.variableIncomes(), loaded.variableOverrides(),
                        bi != null ? bi : new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                        loaded.assetCategories(),
                        loaded.loans()
                );
                currentBudget.set(complete);
                return;
            }
        }
        
        // Create new default data and save to database.
        // NOTE: init() est un callback @PostConstruct, invoqué par Spring AVANT que le
        // proxy AOP (@Transactional) n'enveloppe ce bean. L'appel ci-dessous à
        // saveToDatabase(...) est donc un self-invocation qui NE PASSE PAS par le proxy
        // transactionnel de la classe : sans la mesure explicite ci-dessous, les
        // opérations d'écriture déclenchées (notamment loanRepository.deleteByBudgetDataId,
        // qui exécute une requête JPQL de suppression) échouent avec
        // "TransactionRequiredException: Executing an update/delete query" dès que la
        // base est vide au démarrage (typiquement en environnement de test, où le schéma
        // est réinitialisé). On ouvre donc explicitement une transaction programmatique.
        BudgetDataModel defaultData = createDefaultBudgetData();
        if (transactionTemplate != null) {
            transactionTemplate.executeWithoutResult(status -> saveToDatabase(defaultData));
        } else {
            saveToDatabase(defaultData);
        }
        currentBudget.set(defaultData);
    }
    
    private void saveToDatabase(BudgetDataModel model) {
        // Fallback for testing when repositories are null
        if (budgetDataRepository == null) {
            return;
        }

        // IMPORTANT : EntityModelConverter.toEntity(model) renvoie toujours une entité
        // avec id=null (voir son commentaire "Lists will be set separately"). Sans ce
        // deleteAll() préalable, Hibernate ferait donc un INSERT à chaque appel de
        // saveToDatabase() (édition d'une ligne, import JSON...) au lieu d'un UPDATE,
        // créant une nouvelle ligne budget_data à chaque sauvegarde. Après un
        // redémarrage, @PostConstruct init() relit via findFirstByOrderByIdAsc(), qui
        // renvoie l'id le plus petit — donc la toute première ligne (souvent vide) —
        // au lieu de la dernière version sauvegardée. On supprime l'existant avant de
        // réinsérer (même mécanisme que resetData(), déjà en place plus bas) pour
        // garantir qu'une seule ligne budget_data existe à tout moment.
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
                org.slf4j.LoggerFactory.getLogger(PersistenceManager.class).error("Erreur lors de la lecture de BankImport depuis la base: ", e);
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
                org.slf4j.LoggerFactory.getLogger(PersistenceManager.class).error("Erreur lors de la sauvegarde de BankImport dans la base: ", e);
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

    /**
     * Récupère le modèle de budget complet depuis la base de données.
     */
    public BudgetDataModel getBudgetData() {
        BudgetDataModel model = currentBudget.get();
        if (model == null) {
            // Reload from database
            if (budgetDataRepository != null) {
                Optional<BudgetDataEntity> existingData = budgetDataRepository.findFirstByOrderByIdAsc();
                if (existingData.isPresent()) {
                    BudgetDataModel loaded = EntityModelConverter.toModel(existingData.get());
                    BankImportModel bi = loadBankImport(existingData.get().getId());
                    model = new BudgetDataModel(
                            loaded.settings(), loaded.incomes(), loaded.charges(), loaded.placements(),
                            loaded.realEstate(), loaded.retirement(), loaded.taxChildren(), loaded.taxBrackets(),
                            loaded.taxRateOverrides(), loaded.taxActualOverrides(), loaded.oneoff(),
                            loaded.transfers(), loaded.variableIncomes(), loaded.variableOverrides(),
                            bi != null ? bi : new BankImportModel(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                            loaded.assetCategories(),
                            loaded.loans()
                    );
                }
            }
            
            if (model == null) {
                model = createDefaultBudgetData();
                saveToDatabase(model);
            }
            currentBudget.set(model);
        }
        return model;
    }

    /**
     * Remplace l'intégralité du modèle de données (utilisé lors de l'import JSON).
     */
    public void setBudgetData(BudgetDataModel data) {
        if (data != null) {
            saveToDatabase(data);
            currentBudget.set(data);
        } else {
            BudgetDataModel defaultData = createDefaultBudgetData();
            saveToDatabase(defaultData);
            currentBudget.set(defaultData);
        }
    }

    /**
     * Réinitialise les données aux valeurs par défaut.
     */
    public BudgetDataModel resetData() {
        // Clear existing data
        if (budgetDataRepository != null) {
            budgetDataRepository.deleteAll();
        }
        
        BudgetDataModel defaultData = createDefaultBudgetData();
        saveToDatabase(defaultData);
        currentBudget.set(defaultData);
        return defaultData;
    }

    /**
     * Ajoute une nouvelle ligne dans une section de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        String uid = (body != null && body.containsKey("id") && body.get("id") != null)
                ? String.valueOf(body.get("id"))
                : UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            int birthYear = (base.settings() != null && base.settings().birthYear() != null)
                    ? base.settings().birthYear() : 1985;
            int retireAge = (base.settings() != null && base.settings().retireAge() != null)
                    ? base.settings().retireAge() : 64;
            int retireYear = birthYear + retireAge;

            if ("incomes".equalsIgnoreCase(listKey)) {
                List<IncomeModel> list = new ArrayList<>(base.getEffectiveIncomes());
                String label = getString(body, "label", "Nouveau revenu");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String start = getString(body, "start", "2026-01-01");
                String end = getString(body, "end", retireYear + "-12-31");
                BigDecimal growthRate = getBigDecimal(body, "growthRate", BigDecimal.ZERO);
                String categoryId = getString(body, "categoryId", "");
                String notes = getString(body, "notes", "");

                IncomeModel created = new IncomeModel(uid, label, monthly, start, end, growthRate, categoryId, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("monthly", monthly);
                resultRow.put("start", start);
                resultRow.put("end", end);
                resultRow.put("growthRate", growthRate);
                resultRow.put("categoryId", categoryId);
                resultRow.put("notes", notes);

                return base.withIncomes(list);
            } else if ("charges".equalsIgnoreCase(listKey)) {
                List<ChargeModel> list = new ArrayList<>(base.getEffectiveCharges());
                String label = getString(body, "label", "Nouvelle charge");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String start = getString(body, "start", "2026-01-01");
                String end = getString(body, "end", retireYear + "-12-31");
                BigDecimal growthRate = getBigDecimal(body, "growthRate", BigDecimal.ZERO);
                String categoryId = getString(body, "categoryId", "");
                String notes = getString(body, "notes", "");

                ChargeModel created = new ChargeModel(uid, label, monthly, start, end, growthRate, categoryId, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("monthly", monthly);
                resultRow.put("start", start);
                resultRow.put("end", end);
                resultRow.put("growthRate", growthRate);
                resultRow.put("categoryId", categoryId);
                resultRow.put("notes", notes);

                return base.withCharges(list);
            } else if ("oneoff".equalsIgnoreCase(listKey)) {
                List<OneOffExpenseModel> list = new ArrayList<>(base.getEffectiveOneoff());
                String label = getString(body, "label", "Nouvelle dépense");
                String date = getString(body, "date", "2026-01-01");
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String notes = getString(body, "notes", "");

                OneOffExpenseModel created = new OneOffExpenseModel(uid, label, date, amount, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("date", date);
                resultRow.put("amount", amount);
                resultRow.put("notes", notes);

                return base.withOneoff(list);
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = new ArrayList<>(base.getEffectiveVariableIncomes());
                String label = getString(body, "label", "Nouvelle prime");
                String firstIncomeLabel = !base.getEffectiveIncomes().isEmpty() ? base.getEffectiveIncomes().get(0).label() : "";
                String refIncomeLabel = getString(body, "refIncomeLabel", firstIncomeLabel);
                BigDecimal rate = getBigDecimal(body, "rate", new BigDecimal("0.05"));
                Integer startYear = getInteger(body, "startYear", 2026);
                Integer endYear = getInteger(body, "endYear", retireYear);
                String taxable = getString(body, "taxable", "Oui");
                String type = getString(body, "type", "prime");
                String notes = getString(body, "notes", "");

                VariableIncomeModel created = new VariableIncomeModel(uid, label, refIncomeLabel, rate, startYear, endYear, taxable, type, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("refIncomeLabel", refIncomeLabel);
                resultRow.put("rate", rate);
                resultRow.put("startYear", startYear);
                resultRow.put("endYear", endYear);
                resultRow.put("taxable", taxable);
                resultRow.put("type", type);
                resultRow.put("notes", notes);

                return base.withVariableIncomes(list);
            } else if ("variableOverrides".equalsIgnoreCase(listKey)) {
                List<VariableOverrideModel> list = new ArrayList<>(base.getEffectiveVariableOverrides());
                String firstVarLabel = !base.getEffectiveVariableIncomes().isEmpty() ? base.getEffectiveVariableIncomes().get(0).label() : "";
                String label = getString(body, "label", firstVarLabel);
                Integer year = getInteger(body, "year", LocalDate.now().getYear());
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String taxable = getString(body, "taxable", "");
                String notes = getString(body, "notes", "");

                VariableOverrideModel created = new VariableOverrideModel(uid, label, year, amount, taxable, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("year", year);
                resultRow.put("amount", amount);
                resultRow.put("taxable", taxable);
                resultRow.put("notes", notes);

                return base.withVariableOverrides(list);
            } else if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = new ArrayList<>(base.getEffectivePlacements());
                String label = getString(body, "label", "Nouveau placement");
                String category = getString(body, "category", "Epargne");
                BigDecimal balance = getBigDecimal(body, "balance", BigDecimal.ZERO);
                String balanceDate = getString(body, "balanceDate", "2026-01-01");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String monthlyFrom = getString(body, "monthlyFrom", "2026-01-01");
                String monthlyUntil = getString(body, "monthlyUntil", retireYear + "-12-31");
                BigDecimal ratePess = getBigDecimal(body, "ratePess", BigDecimal.ZERO);
                BigDecimal rateCorr = getBigDecimal(body, "rateCorr", BigDecimal.ZERO);
                BigDecimal rateOpti = getBigDecimal(body, "rateOpti", BigDecimal.ZERO);
                Boolean excludedFromRetirement = body != null && body.containsKey("excludedFromRetirement")
                        ? Boolean.valueOf(String.valueOf(body.get("excludedFromRetirement"))) : false;
                String notes = getString(body, "notes", "");
                Integer sweepPriority = getInteger(body, "sweepPriority", null);
                BigDecimal sweepCap = getBigDecimal(body, "sweepCap", null);
                BigDecimal pauseTriggerBalance = getBigDecimal(body, "pauseTriggerBalance", null);
                Integer pausePriority = getInteger(body, "pausePriority", null);
                String categoryId = getString(body, "categoryId", "");

                PlacementModel created = new PlacementModel(uid, label, category, balance, balanceDate, monthly,
                        monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes,
                        sweepPriority, sweepCap, pauseTriggerBalance, pausePriority, categoryId);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("category", category);
                resultRow.put("balance", balance);
                resultRow.put("balanceDate", balanceDate);
                resultRow.put("monthly", monthly);
                resultRow.put("monthlyFrom", monthlyFrom);
                resultRow.put("monthlyUntil", monthlyUntil);
                resultRow.put("ratePess", ratePess);
                resultRow.put("rateCorr", rateCorr);
                resultRow.put("rateOpti", rateOpti);
                resultRow.put("excludedFromRetirement", excludedFromRetirement);
                resultRow.put("notes", notes);
                resultRow.put("sweepPriority", sweepPriority);
                resultRow.put("sweepCap", sweepCap);
                resultRow.put("pauseTriggerBalance", pauseTriggerBalance);
                resultRow.put("pausePriority", pausePriority);
                resultRow.put("categoryId", categoryId);

                return base.withPlacements(list);
            }
            return base;
        });
        
        // Save to database
        saveToDatabase(updated);

        return resultRow;
    }

    /**
     * Met à jour une cellule d'une ligne de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        if (listKey == null || id == null || field == null) {
            return;
        }

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();

            if ("incomes".equalsIgnoreCase(listKey)) {
                List<IncomeModel> list = new ArrayList<>();
                for (IncomeModel r : base.getEffectiveIncomes()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new IncomeModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "monthly".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.monthly(),
                                "start".equals(field) ? (value != null ? String.valueOf(value) : "") : r.start(),
                                "end".equals(field) ? (value != null ? String.valueOf(value) : "") : r.end(),
                                "growthRate".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.growthRate(),
                                "categoryId".equals(field) ? (value != null ? String.valueOf(value) : "") : r.categoryId(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withIncomes(list);
            } else if ("charges".equalsIgnoreCase(listKey)) {
                List<ChargeModel> list = new ArrayList<>();
                for (ChargeModel r : base.getEffectiveCharges()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new ChargeModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "monthly".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.monthly(),
                                "start".equals(field) ? (value != null ? String.valueOf(value) : "") : r.start(),
                                "end".equals(field) ? (value != null ? String.valueOf(value) : "") : r.end(),
                                "growthRate".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.growthRate(),
                                "categoryId".equals(field) ? (value != null ? String.valueOf(value) : "") : r.categoryId(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withCharges(list);
            } else if ("oneoff".equalsIgnoreCase(listKey)) {
                List<OneOffExpenseModel> list = new ArrayList<>();
                for (OneOffExpenseModel r : base.getEffectiveOneoff()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new OneOffExpenseModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "date".equals(field) ? (value != null ? String.valueOf(value) : "") : r.date(),
                                "amount".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.amount(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withOneoff(list);
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = new ArrayList<>();
                for (VariableIncomeModel r : base.getEffectiveVariableIncomes()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new VariableIncomeModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "refIncomeLabel".equals(field) ? (value != null ? String.valueOf(value) : "") : r.refIncomeLabel(),
                                "rate".equals(field) ? toBigDecimal(value, new BigDecimal("0.05")) : r.rate(),
                                "startYear".equals(field) ? toInteger(value, 2026) : r.startYear(),
                                "endYear".equals(field) ? toInteger(value, 2049) : r.endYear(),
                                "taxable".equals(field) ? (value != null ? String.valueOf(value) : "") : r.taxable(),
                                "type".equals(field) ? (value != null ? String.valueOf(value) : "prime") : r.type(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withVariableIncomes(list);
            } else if ("variableOverrides".equalsIgnoreCase(listKey)) {
                List<VariableOverrideModel> list = new ArrayList<>();
                for (VariableOverrideModel r : base.getEffectiveVariableOverrides()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new VariableOverrideModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "year".equals(field) ? toInteger(value, LocalDate.now().getYear()) : r.year(),
                                "amount".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.amount(),
                                "taxable".equals(field) ? (value != null ? String.valueOf(value) : "") : r.taxable(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withVariableOverrides(list);
            } else if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = new ArrayList<>();
                for (PlacementModel r : base.getEffectivePlacements()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new PlacementModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "category".equals(field) ? (value != null ? String.valueOf(value) : "") : r.category(),
                                "balance".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.balance(),
                                "balanceDate".equals(field) ? (value != null ? String.valueOf(value) : "") : r.balanceDate(),
                                "monthly".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.monthly(),
                                "monthlyFrom".equals(field) ? (value != null ? String.valueOf(value) : "") : r.monthlyFrom(),
                                "monthlyUntil".equals(field) ? (value != null ? String.valueOf(value) : "") : r.monthlyUntil(),
                                "ratePess".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.ratePess(),
                                "rateCorr".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.rateCorr(),
                                "rateOpti".equals(field) ? toBigDecimal(value, BigDecimal.ZERO) : r.rateOpti(),
                                "excludedFromRetirement".equals(field) ? (value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value))) : r.excludedFromRetirement(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes(),
                                "sweepPriority".equals(field) ? toInteger(value, null) : r.sweepPriority(),
                                "sweepCap".equals(field) ? toBigDecimal(value, null) : r.sweepCap(),
                                "pauseTriggerBalance".equals(field) ? toBigDecimal(value, null) : r.pauseTriggerBalance(),
                                "pausePriority".equals(field) ? toInteger(value, null) : r.pausePriority(),
                                "categoryId".equals(field) ? (value != null ? String.valueOf(value) : "") : r.categoryId()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return base.withPlacements(list);
            }

            return base;
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Supprime une ligne d'une section de trésorerie.
     */
    public void removeTresorerieRow(String listKey, String id) {
        if (listKey == null || id == null) {
            return;
        }

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();

            if ("incomes".equalsIgnoreCase(listKey)) {
                List<IncomeModel> list = base.getEffectiveIncomes().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withIncomes(list);
            } else if ("charges".equalsIgnoreCase(listKey)) {
                List<ChargeModel> list = base.getEffectiveCharges().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withCharges(list);
            } else if ("oneoff".equalsIgnoreCase(listKey)) {
                List<OneOffExpenseModel> list = base.getEffectiveOneoff().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withOneoff(list);
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = base.getEffectiveVariableIncomes().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withVariableIncomes(list);
            } else if ("variableOverrides".equalsIgnoreCase(listKey)) {
                List<VariableOverrideModel> list = base.getEffectiveVariableOverrides().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withVariableOverrides(list);
            } else if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = base.getEffectivePlacements().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withPlacements(list);
            }

            return base;
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Applique un ajustement de montant mensuel sur une ligne de charges, revenus ou placements.
     */
    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        if (lineId == null || kind == null || newMonthly == null) {
            return;
        }

        String listKey = "charge".equalsIgnoreCase(kind) ? "charges"
                : ("revenu".equalsIgnoreCase(kind) || "income".equalsIgnoreCase(kind)) ? "incomes" : "placements";
        updateTresorerieRow(listKey, lineId, "monthly", newMonthly);
    }

    /**
     * Sauvegarde ou crée une ligne de patrimoine (placements, transfers, loans/credits, realEstate).
     */
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        Map<String, Object> resultRow = new HashMap<>();
        String givenId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String uid = (givenId != null && !givenId.trim().isEmpty()) ? givenId : ("pat_" + UUID.randomUUID().toString().substring(0, 8));
        resultRow.put("id", uid);

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            int retireYear = (base.settings() != null ? base.settings().getEffectiveBirthYear() : 1985)
                    + (base.settings() != null ? base.settings().getEffectiveRetireAge() : 64);

            if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = new ArrayList<>();
                boolean found = false;

                String label = getString(body, "label", "Nouveau placement");
                String category = getString(body, "category", "Epargne");
                BigDecimal balance = getBigDecimal(body, "balance", BigDecimal.ZERO);
                String balanceDate = getString(body, "balanceDate", "2026-01-01");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String monthlyFrom = getString(body, "monthlyFrom", "2026-01-01");
                String monthlyUntil = getString(body, "monthlyUntil", retireYear + "-12-31");
                BigDecimal ratePess = getBigDecimal(body, "ratePess", BigDecimal.ZERO);
                BigDecimal rateCorr = getBigDecimal(body, "rateCorr", BigDecimal.ZERO);
                BigDecimal rateOpti = getBigDecimal(body, "rateOpti", BigDecimal.ZERO);
                Boolean excludedFromRetirement = body != null && body.containsKey("excludedFromRetirement")
                        ? Boolean.valueOf(String.valueOf(body.get("excludedFromRetirement"))) : false;
                String notes = getString(body, "notes", "");
                Integer sweepPriority = getInteger(body, "sweepPriority", null);
                BigDecimal sweepCap = getBigDecimal(body, "sweepCap", null);
                BigDecimal pauseTriggerBalance = getBigDecimal(body, "pauseTriggerBalance", null);
                Integer pausePriority = getInteger(body, "pausePriority", null);
                String categoryId = getString(body, "categoryId", "");

                PlacementModel model = new PlacementModel(uid, label, category, balance, balanceDate, monthly,
                        monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes,
                        sweepPriority, sweepCap, pauseTriggerBalance, pausePriority, categoryId);

                for (PlacementModel p : base.getEffectivePlacements()) {
                    if (Objects.equals(p.id(), uid)) {
                        list.add(model);
                        found = true;
                    } else {
                        list.add(p);
                    }
                }
                if (!found) {
                    list.add(model);
                }

                resultRow.put("label", label);
                resultRow.put("category", category);
                resultRow.put("balance", balance);
                resultRow.put("balanceDate", balanceDate);
                resultRow.put("monthly", monthly);
                resultRow.put("monthlyFrom", monthlyFrom);
                resultRow.put("monthlyUntil", monthlyUntil);
                resultRow.put("ratePess", ratePess);
                resultRow.put("rateCorr", rateCorr);
                resultRow.put("rateOpti", rateOpti);
                resultRow.put("excludedFromRetirement", excludedFromRetirement);
                resultRow.put("notes", notes);
                resultRow.put("sweepPriority", sweepPriority);
                resultRow.put("sweepCap", sweepCap);
                resultRow.put("pauseTriggerBalance", pauseTriggerBalance);
                resultRow.put("pausePriority", pausePriority);
                resultRow.put("categoryId", categoryId);

                return base.withPlacements(list);
            } else if ("transfers".equalsIgnoreCase(listKey)) {
                List<TransferModel> list = new ArrayList<>();
                boolean found = false;

                String placement = getString(body, "placement", "");
                String date = getString(body, "date", "2026-01-01");
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String notes = getString(body, "notes", "");

                TransferModel model = new TransferModel(uid, placement, date, amount, notes);

                for (TransferModel t : base.getEffectiveTransfers()) {
                    if (Objects.equals(t.id(), uid)) {
                        list.add(model);
                        found = true;
                    } else {
                        list.add(t);
                    }
                }
                if (!found) {
                    list.add(model);
                }

                resultRow.put("placement", placement);
                resultRow.put("date", date);
                resultRow.put("amount", amount);
                resultRow.put("notes", notes);

                return base.withTransfers(list);
            } else if ("realEstate".equalsIgnoreCase(listKey)) {
                List<RealEstateModel> list = new ArrayList<>();
                boolean found = false;

                String label = getString(body, "label", "Nouveau bien");
                String type = getString(body, "type", "Résidence Principale");
                BigDecimal currentValue = getBigDecimal(body, "currentValue", BigDecimal.ZERO);
                Integer valuationYear = getInteger(body, "valuationYear", 2026);
                BigDecimal annualGrowthRate = getBigDecimal(body, "annualGrowthRate", new BigDecimal("0.02"));
                String notes = getString(body, "notes", "");

                RealEstateModel model = new RealEstateModel(uid, label, type, currentValue, valuationYear, annualGrowthRate, notes);

                for (RealEstateModel re : base.getEffectiveRealEstate()) {
                    if (Objects.equals(re.id(), uid)) {
                        list.add(model);
                        found = true;
                    } else {
                        list.add(re);
                    }
                }
                if (!found) {
                    list.add(model);
                }

                resultRow.put("label", label);
                resultRow.put("type", type);
                resultRow.put("currentValue", currentValue);
                resultRow.put("valuationYear", valuationYear);
                resultRow.put("annualGrowthRate", annualGrowthRate);
                resultRow.put("notes", notes);

                return base.withRealEstate(list);
            } else if ("loans".equalsIgnoreCase(listKey) || "credits".equalsIgnoreCase(listKey)) {
                List<LoanModel> list = new ArrayList<>();
                boolean found = false;

                String label = getString(body, "label", "Nouveau prêt");
                BigDecimal crd = getBigDecimal(body, "crd", BigDecimal.ZERO);
                BigDecimal rate = getBigDecimal(body, "rate", BigDecimal.ZERO);
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                BigDecimal insurance = getBigDecimal(body, "insurance", BigDecimal.ZERO);
                String startDate = getString(body, "startDate", "2026-01-01");
                String endDate = getString(body, "endDate", "2046-01-01");

                LoanModel model = new LoanModel(uid, label, crd, rate, monthly, insurance, startDate, endDate);

                for (LoanModel l : base.getEffectiveLoans()) {
                    if (Objects.equals(l.id(), uid)) {
                        list.add(model);
                        found = true;
                    } else {
                        list.add(l);
                    }
                }
                if (!found) {
                    list.add(model);
                }

                resultRow.put("label", label);
                resultRow.put("crd", crd);
                resultRow.put("rate", rate);
                resultRow.put("monthly", monthly);
                resultRow.put("insurance", insurance);
                resultRow.put("startDate", startDate);
                resultRow.put("endDate", endDate);

                return base.withLoans(list);
            }

            return base;
        });
        
        // Save to database
        saveToDatabase(updated);

        return resultRow;
    }

    /**
     * Maintient et sauvegarde la configuration de retraite.
     */
    public void updateRetirement(RetirementModel retirement) {
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            return base.withRetirement(retirement);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Met à jour la configuration d'impôts.
     */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            return base.withTaxChildren(children != null ? children : base.taxChildren())
                    .withTaxBrackets(brackets != null ? brackets : base.taxBrackets())
                    .withTaxRateOverrides(rateOverrides != null ? rateOverrides : base.taxRateOverrides())
                    .withTaxActualOverrides(actualOverrides != null ? actualOverrides : base.taxActualOverrides());
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Met à jour un paramètre lié aux impôts ou généraux dans Settings.
     */
    public void updateTaxSettings(String field, Object value) {
        if (field == null) return;
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            SettingsModel s = base.settings();
            SettingsModel updatedSettings = new SettingsModel(
                    "birthYear".equals(field) ? toInteger(value, 1985) : s.birthYear(),
                    "retireAge".equals(field) ? toInteger(value, 64) : s.retireAge(),
                    "simulateUntilAge".equals(field) ? toInteger(value, 85) : s.simulateUntilAge(),
                    "inflationRate".equals(field) ? toBigDecimal(value, new BigDecimal("0.02")) : s.inflationRate(),
                    "pivotDate".equals(field) ? (value != null ? String.valueOf(value) : "") : s.pivotDate(),
                    "pivotMode".equals(field) ? (value != null ? String.valueOf(value) : "") : s.pivotMode(),
                    ("startBalance".equals(field) || "pivotBalanceManual".equals(field)) ? toBigDecimal(value, BigDecimal.ZERO) : s.startBalance(),
                    "childExitAge".equals(field) ? toInteger(value, 21) : s.childExitAge(),
                    "taxAbattement".equals(field) ? toBigDecimal(value, new BigDecimal("0.10")) : s.taxAbattement(),
                    "pass2026".equals(field) ? toBigDecimal(value, new BigDecimal("47100")) : s.pass2026(),
                    "passGrowthRate".equals(field) ? toBigDecimal(value, new BigDecimal("0.015")) : s.passGrowthRate(),
                    "sweepEnabled".equals(field) ? (value != null && Boolean.parseBoolean(String.valueOf(value))) : s.sweepEnabled(),
                    "cashCeiling".equals(field) ? toBigDecimal(value, null) : s.cashCeiling(),
                    "cashFloor".equals(field) ? toBigDecimal(value, null) : s.cashFloor()
            );
            return base.withSettings(updatedSettings);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Met à jour une cellule d'une catégorie d'actif.
     */
    public void updateAssetCategory(String id, String field, Object value) {
        if (id == null || field == null) return;
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            List<AssetCategoryModel> list = new ArrayList<>(base.getEffectiveAssetCategories());
            List<AssetCategoryModel> updatedList = new ArrayList<>();
            for (AssetCategoryModel c : list) {
                if (Objects.equals(c.id(), id)) {
                    AssetCategoryModel updatedCategory = new AssetCategoryModel(
                            c.id(),
                            "icon".equals(field) ? String.valueOf(value) : c.icon(),
                            "name".equals(field) ? String.valueOf(value) : c.name(),
                            "bucket".equals(field) ? String.valueOf(value) : c.bucket(),
                            "color".equals(field) ? String.valueOf(value) : c.color()
                    );
                    updatedList.add(updatedCategory);
                } else {
                    updatedList.add(c);
                }
            }
            return base.withAssetCategories(updatedList);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Ajoute une nouvelle catégorie d'actif.
     */
    public void addAssetCategory(AssetCategoryModel category) {
        if (category == null) return;
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            List<AssetCategoryModel> list = new ArrayList<>(base.getEffectiveAssetCategories());
            list.add(category);
            return base.withAssetCategories(list);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Supprime une catégorie d'actif par ID.
     */
    public void removeAssetCategory(String id) {
        if (id == null) return;
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            List<AssetCategoryModel> list = base.getEffectiveAssetCategories().stream()
                    .filter(c -> !Objects.equals(c.id(), id))
                    .toList();
            return base.withAssetCategories(list);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Réinitialise les tranches d'impôt par défaut.
     */
    public void resetDefaultTaxBrackets() {
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            List<TaxBracketModel> defaultBrackets = List.of(
                    new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                    new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                    new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                    new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                    new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
            );
            return base.withTaxBrackets(defaultBrackets);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Supprime une ligne de patrimoine (placements, transfers, loans/credits, realEstate).
     */
    public void deletePatrimoineRow(String listKey, String id) {
        if (listKey == null || id == null) {
            return;
        }

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();

            if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = base.getEffectivePlacements().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withPlacements(list);
            } else if ("transfers".equalsIgnoreCase(listKey)) {
                List<TransferModel> list = base.getEffectiveTransfers().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withTransfers(list);
            } else if ("realEstate".equalsIgnoreCase(listKey)) {
                List<RealEstateModel> list = base.getEffectiveRealEstate().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withRealEstate(list);
            } else if ("loans".equalsIgnoreCase(listKey) || "credits".equalsIgnoreCase(listKey)) {
                List<LoanModel> list = base.getEffectiveLoans().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withLoans(list);
            }

            return base;
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    /**
     * Ajoute un point d'historique de valorisation sur un placement.
     */
    public Map<String, Object> addPlacementHistoriquePoint(String placementId, Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        if (placementId == null || body == null) return result;

        String date = getString(body, "date", LocalDate.now().toString());
        BigDecimal value = getBigDecimal(body, "value", BigDecimal.ZERO);
        result.put("date", date);
        result.put("value", value);

        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            List<PlacementModel> list = new ArrayList<>();

            for (PlacementModel p : base.getEffectivePlacements()) {
                if (Objects.equals(p.id(), placementId)) {
                    // Update latest balance and date if newer or equal
                    PlacementModel updatedPlacement = new PlacementModel(
                            p.id(), p.label(), p.category(), value, date,
                            p.monthly(), p.monthlyFrom(), p.monthlyUntil(),
                            p.ratePess(), p.rateCorr(), p.rateOpti(),
                            p.excludedFromRetirement(), p.notes(),
                            p.sweepPriority(), p.sweepCap(), p.pauseTriggerBalance(),
                            p.pausePriority(), p.categoryId()
                    );
                    list.add(updatedPlacement);
                } else {
                    list.add(p);
                }
            }

            return base.withPlacements(list);
        });
        
        // Save to database
        saveToDatabase(updated);

        return result;
    }

    /**
     * Supprime un point d'historique de valorisation d'un placement.
     */
    public void deletePlacementHistoriquePoint(String placementId, Integer index) {
        // En persistance in-memory simple, on conserve la cohérence du placement
    }

    /**
     * Obtient les données d'import bancaire.
     */
    public BankImportModel getBankImport() {
        return getBudgetData().bankImport();
    }

    /**
     * Met à jour les données d'import bancaire.
     */
    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) return;
        BudgetDataModel updated = currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();
            return base.withBankImport(bankImport);
        });
        
        // Save to database
        saveToDatabase(updated);
    }

    // --- Utilitaires de conversion ---

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toBigDecimal(map.get(key), defaultValue);
    }

    private Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toInteger(map.get(key), defaultValue);
    }

    private BigDecimal toBigDecimal(Object val, BigDecimal fallback) {
        if (val == null) return fallback;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(val).trim().replace(",", ".");
            if (s.isEmpty()) return fallback;
            return new BigDecimal(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer toInteger(Object val, Integer fallback) {
        if (val == null) return fallback;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(val).trim();
            if (s.isEmpty()) return fallback;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Initialise un jeu de données par défaut complet conforme à DEFAULT_DATA
     */
    public BudgetDataModel createDefaultBudgetData() {
        SettingsModel settings = new SettingsModel(
                1985,
                64,
                85,
                new BigDecimal("0.02"),
                "",
                "manual",
                BigDecimal.ZERO,
                21,
                new BigDecimal("0.10"),
                new BigDecimal("47100"),
                new BigDecimal("0.015"),
                false,
                null,
                null
        );

        RetirementModel retirement = new RetirementModel(
                Collections.emptyList(),
                new BigDecimal("47100"),
                new BigDecimal("0.015"),
                new BigDecimal("1.4386"),
                "2025-11-01",
                new BigDecimal("0.01")
        );

        List<TaxBracketModel> taxBrackets = List.of(
                new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
        );

        BankImportModel bankImport = new BankImportModel(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        return new BudgetDataModel(
                settings,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                retirement,
                new ArrayList<>(),
                taxBrackets,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                bankImport,
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
