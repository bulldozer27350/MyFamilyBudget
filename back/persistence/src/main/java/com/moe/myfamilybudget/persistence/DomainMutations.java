package com.moe.myfamilybudget.persistence;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

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
     * Ajoute ou met à jour une ligne de patrimoine (placements). R-30 (reste à nettoyer) : le reste de ce
     * fichier miroir ce qui subsiste dans {@link BudgetMutationService} — voir son commentaire sur
     * {@code savePatrimoineRow} pour le détail de ce qui a été retiré et de ce qui est volontairement
     * conservé pour {@code WriteFailureKeepsMemoryTest} (VT-330).
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

    /**
     * Ajoute une nouvelle catégorie d'actif.
     */
    public void addAssetCategory(AssetCategoryModel category) {
        mutationService.addAssetCategory(category);
        publishMutated("addAssetCategory");
    }

    /**
     * Réinitialise les tranches d'imposition par défaut.
     */
    public void resetDefaultTaxBrackets() {
        mutationService.resetDefaultTaxBrackets();
        publishMutated("resetDefaultTaxBrackets");
    }

    /**
     * Supprime une ligne de patrimoine (real estate, placements).
     */
    public void deletePatrimoineRow(String listKey, String id) {
        mutationService.deletePatrimoineRow(listKey, id);
        publishMutated("deletePatrimoineRow");
    }

    // --- SILO-119 (lot B1) : remplacement et reinitialisation par silo ---

    /** Remplace les parametres Retraite et le plan de retraite. */
    public void replaceRetirementSnapshot(RetirementSettingsModel settings, RetirementModel retirement) {
        mutationService.replaceRetirementSnapshot(settings, retirement);
        publishMutated("replaceRetirementSnapshot");
    }

    /** Remet la Retraite a ses valeurs par defaut. */
    public void resetRetirementSnapshot() {
        mutationService.resetRetirementSnapshot();
        publishMutated("resetRetirementSnapshot");
    }

    /** Remplace les parametres et la configuration fiscale. */
    public void replaceTaxSnapshot(TaxSettingsModel settings, List<TaxChildModel> children,
                                   List<TaxBracketModel> brackets, List<TaxRateOverrideModel> rateOverrides,
                                   List<TaxActualOverrideModel> actualOverrides) {
        mutationService.replaceTaxSnapshot(settings, children, brackets, rateOverrides, actualOverrides);
        publishMutated("replaceTaxSnapshot");
    }

    /** Remet la Fiscalite a ses valeurs par defaut. */
    public void resetTaxSnapshot() {
        mutationService.resetTaxSnapshot();
        publishMutated("resetTaxSnapshot");
    }

    /**
     * @param mutationKind nom de la methode a l'origine de la mutation, a titre diagnostique
     */
    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new BudgetMutatedEvent(mutationKind));
    }
}
