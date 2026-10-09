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
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentMapper;
import com.moe.myfamilybudget.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalEntityMapper;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntityMapper;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionEntityMapper;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthEntityMapper;
import com.moe.myfamilybudget.persistence.entity.BudgetDataEntity;
import com.moe.myfamilybudget.persistence.repository.AssetCategoryRepository;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsMapper;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsMapper;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.persistence.repository.PlacementRepository;
import com.moe.myfamilybudget.persistence.repository.RealEstateRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * Passerelle vers la couche JPA : seule classe qui parle directement aux repositories Spring Data.
 */
class BudgetPersistenceGateway {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetPersistenceGateway.class);

    private final BudgetDataRepository budgetDataRepository;
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
    // SILO-220 (lot B) : tables de parametres chez leurs proprietaires (Retraite, Fiscalite, Tresorerie),
    // alimentees en parallele de `settings` (meme principe que goalRepository avant SILO-212). R-50 : la table du
    // silo Parametres (`app_settings`) n'est plus alimentee ici, elle est ecrite directement par son silo.
    private final PensionSettingsRepository pensionSettingsRepository;
    private final FiscalSettingsRepository fiscalSettingsRepository;
    private final CashflowSettingsRepository cashflowSettingsRepository;

    BudgetPersistenceGateway(BudgetDataRepository budgetDataRepository,
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
                              CashflowSettingsRepository cashflowSettingsRepository) {
        this.budgetDataRepository = budgetDataRepository;
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
        this.pensionSettingsRepository = pensionSettingsRepository;
        this.fiscalSettingsRepository = fiscalSettingsRepository;
        this.cashflowSettingsRepository = cashflowSettingsRepository;
    }

    void deleteAll() {
        deleteExistingBudgetData();
    }

    private void deleteExistingBudgetData() {
        for (BudgetDataEntity existing : budgetDataRepository.findAll()) {
            Long id = existing.getId();
            placementRepository.deleteByBudgetDataId(id);
            realEstateRepository.deleteByBudgetDataId(id);
            assetCategoryRepository.deleteByBudgetDataId(id);
        }
        budgetDataRepository.flush();
        budgetDataRepository.deleteAll();
        budgetDataRepository.flush();
    }

    BudgetDataModel loadExistingIfPresent() {
        Optional<BudgetDataEntity> existingData = budgetDataRepository.findFirstByOrderByIdAsc();
        BudgetDataModel loaded = existingData.map(this::loadCompleteBudgetData).orElse(null);
        if (loaded != null) {
            syncWealth(loaded);
            syncSettings(loaded);
        }
        return loaded;
    }

    private BudgetDataModel loadCompleteBudgetData(BudgetDataEntity entity) {
        BudgetDataModel loaded = EntityModelConverter.toModel(entity);
        BankImportModel bi = loadBankImport();
        RetirementModel retirement = PensionEntityMapper.toModel(pensionPlanRepository.findFirstByOrderByIdAsc().orElse(null));
        List<TaxChildModel> taxChildren = FiscalEntityMapper.toChildModels(fiscalChildRepository.findAllByOrderByPositionAsc());
        List<TaxBracketModel> taxBrackets = FiscalEntityMapper.toBracketModels(fiscalBracketRepository.findAllByOrderByPositionAsc());
        List<TaxRateOverrideModel> taxRateOverrides =
                FiscalEntityMapper.toRateOverrideModels(fiscalRateOverrideRepository.findAllByOrderByPositionAsc());
        List<TaxActualOverrideModel> taxActualOverrides =
                FiscalEntityMapper.toActualOverrideModels(fiscalActualOverrideRepository.findAllByOrderByPositionAsc());
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
                List.of(),
                objectifs
        );
    }

    void save(BudgetDataModel model) {
        deleteExistingBudgetData();

        BudgetDataEntity entity = EntityModelConverter.toEntity(model);
        entity = budgetDataRepository.save(entity);

        savePlacements(model.placements(), entity);
        saveRealEstate(model.realEstate(), entity);
        saveAssetCategories(model.assetCategories(), entity);
        syncFiscal(model);
        syncPension(model.retirement());
        syncWealth(model);
        syncSettings(model);
    }

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

    private void syncPension(RetirementModel retirement) {
        pensionPlanRepository.deleteAll();
        pensionPlanRepository.flush();
        if (retirement == null) {
            return;
        }
        pensionPlanRepository.save(PensionEntityMapper.toEntity(retirement));
    }

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

    private void syncSettings(BudgetDataModel model) {
        SettingsModel s = model.getEffectiveSettings();
        // R-20 : cashflow_settings appartient au silo Tresorerie (JpaTreasuryStore) ; le cache, perime pour
        // ces champs, ne doit plus l'ecraser a chaque mutation d'un autre domaine.
        pensionSettingsRepository.deleteAll();
        fiscalSettingsRepository.deleteAll();
        pensionSettingsRepository.flush();
        fiscalSettingsRepository.flush();
        pensionSettingsRepository.save(PensionSettingsMapper.toEntity(
                new RetirementSettingsModel(s.birthYear(), s.retireAge())));
        fiscalSettingsRepository.save(FiscalSettingsMapper.toEntity(
                new TaxSettingsModel(s.childExitAge(), s.taxAbattement())));
    }

    private BankImportModel loadBankImport() {
        try {
            return BankImportDocumentMapper.toModel(bankImportDocumentRepository.findFirstByOrderByIdAsc().orElse(null));
        } catch (IllegalStateException e) {
            LOG.error("Erreur lors de la lecture de BankImport depuis la base: ", e);
            return null;
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

    private void saveAssetCategories(List<AssetCategoryModel> assetCategories, BudgetDataEntity budgetData) {
        assetCategoryRepository.deleteByBudgetDataId(budgetData.getId());
        for (AssetCategoryModel ac : assetCategories) {
            assetCategoryRepository.save(EntityModelConverter.toEntity(ac, budgetData));
        }
    }
}
