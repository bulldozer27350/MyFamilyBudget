package com.moe.myfamilybudget.server.internal.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.server.internal.model.TaxBracketModel;
import com.moe.myfamilybudget.server.internal.model.TaxChildModel;
import com.moe.myfamilybudget.server.internal.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.server.internal.port.RetirementSettingField;
import com.moe.myfamilybudget.server.internal.port.TaxSettingField;
import com.moe.myfamilybudget.server.internal.port.TresorerieSettingField;

/**
 * Mutations de domaine du budget (DB-060), extraites de {@link PersistenceManager}.
 *
 * <p>Chaque methode delegue a {@link BudgetMutationService} puis publie un {@link BudgetMutatedEvent}.
 * Ce n'est pas un bean Spring : l'instance est portee par {@link PersistenceManager} et n'est accessible
 * que par {@link PersistenceManager#write} / {@link PersistenceManager#writeAndGet}, qui fournissent la
 * frontiere transactionnelle (le {@code @Transactional} de classe de {@code PersistenceManager}).
 *
 * <p>Classe de transition : seuls les adaptateurs de persistance l'utilisent. Elle disparait avec la
 * bascule JPA par domaine (DB-1001 a DB-1061) et le nettoyage final (DB-1190).
 */
public final class DomainMutations {

    private final BudgetMutationService mutationService;
    private final ApplicationEventPublisher eventPublisher;

    DomainMutations(BudgetMutationService mutationService, ApplicationEventPublisher eventPublisher) {
        this.mutationService = mutationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ajoute une nouvelle ligne dans une section de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        Map<String, Object> result = mutationService.addTresorerieRow(listKey, body);
        publishMutated("addTresorerieRow");
        return result;
    }

    /**
     * Met à jour une cellule d'une ligne de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        mutationService.updateTresorerieRow(listKey, id, field, value);
        publishMutated("updateTresorerieRow");
    }

    /**
     * Supprime une ligne d'une section de trésorerie.
     */
    public void removeTresorerieRow(String listKey, String id) {
        mutationService.removeTresorerieRow(listKey, id);
        publishMutated("removeTresorerieRow");
    }

    /**
     * Applique un ajustement (ex. suite à un virement automatique) sur le montant mensuel
     * d'une ligne de trésorerie.
     */
    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        mutationService.applyTresorerieAjustement(lineId, kind, newMonthly);
        publishMutated("applyTresorerieAjustement");
    }

    /**
     * Ajoute ou met à jour une ligne de patrimoine (real estate, placements, loans).
     */
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        Map<String, Object> result = mutationService.savePatrimoineRow(listKey, body);
        publishMutated("savePatrimoineRow");
        return result;
    }

    /**
     * Met à jour les données de retraite.
     */
    public void updateRetirement(RetirementModel retirement) {
        mutationService.updateRetirement(retirement);
        publishMutated("updateRetirement");
    }

    /**
     * Met à jour la configuration d'impôts.
     */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        mutationService.updateTaxConfig(children, brackets, rateOverrides, actualOverrides);
        publishMutated("updateTaxConfig");
    }

    /** SET-030 : paramètre Retraite de {@code /settings}. */
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        mutationService.updateRetirementSetting(field, value);
        publishMutated("updateRetirementSetting");
    }

    /** SET-030 : paramètre Trésorerie de {@code /settings}. */
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        mutationService.updateTresorerieSetting(field, value);
        publishMutated("updateTresorerieSetting");
    }

    /** SET-030 : paramètre Fiscalité de {@code /settings}. */
    public void updateFiscalSetting(TaxSettingField field, Object value) {
        mutationService.updateFiscalSetting(field, value);
        publishMutated("updateFiscalSetting");
    }

    /** SET-030 : paramètre Simulation de {@code /settings}. */
    public void updateSimulateUntilAge(Object value) {
        mutationService.updateSimulateUntilAge(value);
        publishMutated("updateSimulateUntilAge");
    }

    /** SET-030 : hypothèse économique de {@code /settings}. */
    public void updateInflationRate(Object value) {
        mutationService.updateInflationRate(value);
        publishMutated("updateInflationRate");
    }

    /**
     * Met à jour un champ d'une catégorie d'actif.
     */
    public void updateAssetCategory(String id, String field, Object value) {
        mutationService.updateAssetCategory(id, field, value);
        publishMutated("updateAssetCategory");
    }

    /**
     * Ajoute une nouvelle catégorie d'actif.
     */
    public void addAssetCategory(AssetCategoryModel category) {
        mutationService.addAssetCategory(category);
        publishMutated("addAssetCategory");
    }

    /**
     * Supprime une catégorie d'actif.
     */
    public void removeAssetCategory(String id) {
        mutationService.removeAssetCategory(id);
        publishMutated("removeAssetCategory");
    }

    /**
     * Réinitialise les tranches d'imposition par défaut.
     */
    public void resetDefaultTaxBrackets() {
        mutationService.resetDefaultTaxBrackets();
        publishMutated("resetDefaultTaxBrackets");
    }

    /**
     * Supprime une ligne de patrimoine (real estate, placements, loans).
     */
    public void deletePatrimoineRow(String listKey, String id) {
        mutationService.deletePatrimoineRow(listKey, id);
        publishMutated("deletePatrimoineRow");
    }

    /**
     * Ajoute une entrée d'historique de valorisation à un placement.
     */
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        Map<String, Object> result = mutationService.addPlacementHistoryEntry(placementId, body);
        publishMutated("addPlacementHistoryEntry");
        return result;
    }

    /**
     * Met à jour une entrée d'historique de valorisation d'un placement.
     */
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        Map<String, Object> result = mutationService.updatePlacementHistoryEntry(placementId, entryId, body);
        publishMutated("updatePlacementHistoryEntry");
        return result;
    }

    /**
     * Supprime une entrée d'historique de valorisation d'un placement.
     */
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        mutationService.deletePlacementHistoryEntry(placementId, entryId);
        publishMutated("deletePlacementHistoryEntry");
    }

    /**
     * Met à jour les données d'import bancaire.
     */
    public void updateBankImport(BankImportModel bankImport) {
        mutationService.updateBankImport(bankImport);
        publishMutated("updateBankImport");
    }

    /**
     * @param mutationKind nom de la methode a l'origine de la mutation, a titre diagnostique
     */
    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new BudgetMutatedEvent(mutationKind));
    }
}
