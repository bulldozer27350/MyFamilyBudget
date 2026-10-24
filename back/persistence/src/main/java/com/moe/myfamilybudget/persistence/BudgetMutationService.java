package com.moe.myfamilybudget.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.transition.model.SettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxActualOverrideModel;
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.model.TaxRateOverrideModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingField;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;

/**
 * Logique métier de toutes les mutations du budget : sections trésorerie (revenus, charges,
 * dépenses ponctuelles, revenus variables, overrides, placements), patrimoine (immobilier,
 * placements, prêts), retraite, fiscalité, catégories d'actifs, historique de valorisation des
 * placements, et import bancaire.
 *
 * Troisième et dernier incrément du Strangler Fig prévu par le point 6 de l'audit (God Class
 * {@code PersistenceManager}, 1512 lignes à l'origine). Reprend, à l'identique, la logique qui
 * vivait auparavant dans {@code PersistenceManager} : aucun changement de comportement
 * fonctionnel, uniquement un déplacement de code (les seuls changements mécaniques sont les appels
 * à {@code applyAndPersist}/{@code getBudgetData}/{@code createDefaultBudgetData}, désormais
 * qualifiés par {@code cacheStore.} puisqu'ils vivent dans {@link BudgetCacheStore}, 2e
 * incrément).
 *
 * Contient également le dispatcher par nom de champ pour les lignes de trésorerie (point 3 de
 * l'audit, TresorerieFieldUpdateDispatcher, retire avec R-20),
 * invoqué directement depuis {@link #updateTresorerieRow}.
 *
 * Volontairement une classe simple (pas un bean Spring), instanciée directement par
 * {@code PersistenceManager} dans ses deux constructeurs — voir la note à ce sujet dans
 * {@link BudgetPersistenceGateway}. {@code PersistenceManager} devient une pure façade : chacune
 * de ses méthodes publiques ne fait plus que déléguer à la méthode de même nom ici.
 */
class BudgetMutationService {

    private static final Logger LOG = LoggerFactory.getLogger(BudgetMutationService.class);

    private final BudgetCacheStore cacheStore;

    BudgetMutationService(BudgetCacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    /**
     * Maintient et sauvegarde la configuration de retraite.
     */
    public void updateRetirement(RetirementModel retirement) {
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            return base.withRetirement(retirement);
        });
    }

    /**
     * Met à jour la configuration d'impôts.
     */
    public void updateTaxConfig(List<TaxChildModel> children, List<TaxBracketModel> brackets,
                                List<TaxRateOverrideModel> rateOverrides, List<TaxActualOverrideModel> actualOverrides) {
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            return base.withTaxChildren(children != null ? children : base.taxChildren())
                    .withTaxBrackets(brackets != null ? brackets : base.taxBrackets())
                    .withTaxRateOverrides(rateOverrides != null ? rateOverrides : base.taxRateOverrides())
                    .withTaxActualOverrides(actualOverrides != null ? actualOverrides : base.taxActualOverrides());
        });
    }

    /**
     * SET-030 : une mutation explicite par owner de paramètre, en remplacement de l'ancien dispatcher générique
     * {@code updateTaxSettings(String, Object)}. Le stockage reste {@code BudgetDataModel.settings()}, en
     * mémoire uniquement depuis le retrait de {@code SettingsEntity} (R-51) ; seul le champ ciblé est
     * modifié, les autres sont recopiés tels quels, y compris
     * lorsqu'ils sont absents (FIX-020 : {@code sweepEnabled} est un {@code Boolean} nullable, aucun champ ne
     * doit être déballé pour la simple recopie, sous peine de {@link NullPointerException}).
     *
     * <p>Chaque {@code switch} porte sur l'enum de champs de l'owner : ajouter un paramètre à cet enum sans le
     * traiter ici est détecté par {@code UpdateTaxSettingsMinimalBudgetTest#everyOwnerSettingFieldIsApplied}.
     */
    private void updateSettings(UnaryOperator<SettingsModel> change) {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            return base.withSettings(change.apply(base.settings()));
        });
    }

    /**
     * Paramètres Retraite de {@code /settings}. {@code birthYear} et {@code retireAge} restent portés par
     * {@link SettingsModel} ; {@code pass2026} et {@code passGrowthRate} sont écrits dans
     * {@link RetirementModel} (SET-040 : source unique, plus de seconde copie dans les paramètres).
     */
    public void updateRetirementSetting(RetirementSettingField field, Object value) {
        if (field == null) return;
        switch (field) {
            case BIRTH_YEAR -> updateSettings(s -> new SettingsModel(toInteger(value, 1985), s.retireAge(),
                    s.simulateUntilAge(), s.inflationRate(), s.pivotDate(), s.pivotMode(), s.startBalance(),
                    s.childExitAge(), s.taxAbattement(), s.sweepEnabled(), s.cashCeiling(), s.cashFloor(),
                    s.cashAlertThreshold()));
            case RETIRE_AGE -> updateSettings(s -> new SettingsModel(s.birthYear(), toInteger(value, 64),
                    s.simulateUntilAge(), s.inflationRate(), s.pivotDate(), s.pivotMode(), s.startBalance(),
                    s.childExitAge(), s.taxAbattement(), s.sweepEnabled(), s.cashCeiling(), s.cashFloor(),
                    s.cashAlertThreshold()));
            case PASS_2026, PASS_GROWTH_RATE -> updateRetirementPass(field, value);
        }
    }

    private void updateRetirementPass(RetirementSettingField field, Object value) {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            RetirementModel retirement = base.retirement() != null
                    ? base.retirement()
                    : cacheStore.createDefaultBudgetData().retirement();
            BigDecimal pass2026 = retirement.pass2026();
            BigDecimal passGrowthRate = retirement.passGrowthRate();
            if (field == RetirementSettingField.PASS_2026) {
                pass2026 = toBigDecimal(value, new BigDecimal("47100"));
            } else if (field == RetirementSettingField.PASS_GROWTH_RATE) {
                passGrowthRate = toBigDecimal(value, new BigDecimal("0.015"));
            }
            return base.withRetirement(new RetirementModel(retirement.people(), pass2026, passGrowthRate,
                    retirement.agircPointValue(), retirement.agircPointDateGlobal(),
                    retirement.agircPointGrowthRate()));
        });
    }

    /** Paramètres Trésorerie de {@code /settings} (pivot, solde de départ, sweep, plafonds). */
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        if (field == null) return;
        updateSettings(s -> {
            String pivotDate = s.pivotDate();
            String pivotMode = s.pivotMode();
            BigDecimal startBalance = s.startBalance();
            Boolean sweepEnabled = s.sweepEnabled();
            BigDecimal cashCeiling = s.cashCeiling();
            BigDecimal cashFloor = s.cashFloor();
            BigDecimal cashAlertThreshold = s.cashAlertThreshold();
            switch (field) {
                case PIVOT_DATE -> pivotDate = value != null ? String.valueOf(value) : "";
                case PIVOT_MODE -> pivotMode = value != null ? String.valueOf(value) : "";
                case START_BALANCE, PIVOT_BALANCE_MANUAL -> startBalance = toBigDecimal(value, BigDecimal.ZERO);
                case SWEEP_ENABLED -> sweepEnabled = toBoolean(value);
                case CASH_CEILING -> cashCeiling = toBigDecimal(value, null);
                case CASH_FLOOR -> cashFloor = toBigDecimal(value, null);
                case CASH_ALERT_THRESHOLD -> cashAlertThreshold = toBigDecimal(value, null);
            }
            return new SettingsModel(s.birthYear(), s.retireAge(), s.simulateUntilAge(), s.inflationRate(),
                    pivotDate, pivotMode, startBalance, s.childExitAge(), s.taxAbattement(), sweepEnabled, cashCeiling, cashFloor, cashAlertThreshold);
        });
    }

    /** Paramètres Fiscalité de {@code /settings} ({@code childExitAge}, {@code taxAbattement}). */
    public void updateFiscalSetting(TaxSettingField field, Object value) {
        if (field == null) return;
        updateSettings(s -> {
            Integer childExitAge = s.childExitAge();
            BigDecimal taxAbattement = s.taxAbattement();
            switch (field) {
                case CHILD_EXIT_AGE -> childExitAge = toInteger(value, 21);
                case TAX_ABATTEMENT -> taxAbattement = toBigDecimal(value, new BigDecimal("0.10"));
            }
            return new SettingsModel(s.birthYear(), s.retireAge(), s.simulateUntilAge(), s.inflationRate(),
                    s.pivotDate(), s.pivotMode(), s.startBalance(), childExitAge, taxAbattement, s.sweepEnabled(), s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold());
        });
    }

    /**
     * Réinitialise les tranches d'impôt par défaut.
     */
    public void resetDefaultTaxBrackets() {
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<TaxBracketModel> defaultBrackets = List.of(
                    new TaxBracketModel("tb_1", new BigDecimal("11294"), BigDecimal.ZERO),
                    new TaxBracketModel("tb_2", new BigDecimal("28797"), new BigDecimal("0.11")),
                    new TaxBracketModel("tb_3", new BigDecimal("82341"), new BigDecimal("0.30")),
                    new TaxBracketModel("tb_4", new BigDecimal("177106"), new BigDecimal("0.41")),
                    new TaxBracketModel("tb_5", null, new BigDecimal("0.45"))
            );
            return base.withTaxBrackets(defaultBrackets);
        });
    }

    // --- SILO-119 (lot B1) : remplacement et réinitialisation par silo (import et reset par fragments) ---
    //
    // Chaque méthode ne modifie que les champs du silo concerné ; les autres silos sont recopiés tels quels.
    // Une liste absente est lue comme vide, un paramètre absent prend sa valeur par défaut (celle du budget
    // par défaut). Elles s'appellent dans une transaction déjà ouverte, après la prise du verrou de mutation.

    /** Remplace les paramètres Retraite et le plan de retraite. */
    public void replaceRetirementSnapshot(RetirementSettingsModel settings, RetirementModel retirement) {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            SettingsModel defaults = cacheStore.createDefaultBudgetData().settings();
            Integer birthYear = settings != null ? settings.birthYear() : defaults.birthYear();
            Integer retireAge = settings != null ? settings.retireAge() : defaults.retireAge();
            return base.withSettings(settingsWithRetirement(base.getEffectiveSettings(), birthYear, retireAge))
                    .withRetirement(retirement);
        });
    }

    /** Remet les paramètres Retraite et le plan de retraite à leurs valeurs par défaut. */
    public void resetRetirementSnapshot() {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            BudgetDataModel defaults = cacheStore.createDefaultBudgetData();
            return base.withSettings(settingsWithRetirement(base.getEffectiveSettings(),
                            defaults.settings().birthYear(), defaults.settings().retireAge()))
                    .withRetirement(defaults.retirement());
        });
    }

    /** Remplace les paramètres et la configuration fiscale. */
    public void replaceTaxSnapshot(TaxSettingsModel settings, List<TaxChildModel> children,
                                   List<TaxBracketModel> brackets, List<TaxRateOverrideModel> rateOverrides,
                                   List<TaxActualOverrideModel> actualOverrides) {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            SettingsModel defaults = cacheStore.createDefaultBudgetData().settings();
            Integer childExitAge = settings != null ? settings.childExitAge() : defaults.childExitAge();
            BigDecimal taxAbattement = settings != null ? settings.taxAbattement() : defaults.taxAbattement();
            return base.withSettings(settingsWithTax(base.getEffectiveSettings(), childExitAge, taxAbattement))
                    .withTaxChildren(orEmpty(children))
                    .withTaxBrackets(orEmpty(brackets))
                    .withTaxRateOverrides(orEmpty(rateOverrides))
                    .withTaxActualOverrides(orEmpty(actualOverrides));
        });
    }

    /** Remet la fiscalité à ses valeurs par défaut (barème par défaut, aucune surcharge). */
    public void resetTaxSnapshot() {
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            BudgetDataModel defaults = cacheStore.createDefaultBudgetData();
            return base.withSettings(settingsWithTax(base.getEffectiveSettings(),
                            defaults.settings().childExitAge(), defaults.settings().taxAbattement()))
                    .withTaxChildren(defaults.taxChildren())
                    .withTaxBrackets(defaults.taxBrackets())
                    .withTaxRateOverrides(defaults.taxRateOverrides())
                    .withTaxActualOverrides(defaults.taxActualOverrides());
        });
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private static SettingsModel settingsWithRetirement(SettingsModel s, Integer birthYear, Integer retireAge) {
        return new SettingsModel(birthYear, retireAge, s.simulateUntilAge(), s.inflationRate(), s.pivotDate(),
                s.pivotMode(), s.startBalance(), s.childExitAge(), s.taxAbattement(), s.sweepEnabled(),
                s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold());
    }

    private static SettingsModel settingsWithTax(SettingsModel s, Integer childExitAge, BigDecimal taxAbattement) {
        return new SettingsModel(s.birthYear(), s.retireAge(), s.simulateUntilAge(), s.inflationRate(),
                s.pivotDate(), s.pivotMode(), s.startBalance(), childExitAge, taxAbattement, s.sweepEnabled(),
                s.cashCeiling(), s.cashFloor(), s.cashAlertThreshold());
    }

    private static SettingsModel settingsWithTresorerie(SettingsModel s, TresorerieSettingsModel t) {
        return new SettingsModel(s.birthYear(), s.retireAge(), s.simulateUntilAge(), s.inflationRate(),
                t.pivotDate(), t.pivotMode(), t.startBalance(), s.childExitAge(), s.taxAbattement(),
                t.sweepEnabled(), t.cashCeiling(), t.cashFloor(), t.cashAlertThreshold());
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
            LOG.warn("Valeur numérique décimale illisible, valeur par défaut '{}' utilisée : '{}'", fallback, val, e);
            return fallback;
        }
    }

    /**
     * Convertit une valeur de paramètre en {@link Boolean} (jamais {@code null}). Retourne volontairement un
     * {@code Boolean} et non un {@code boolean} : mélangé à un {@code Boolean} nullable dans une expression
     * ternaire, un {@code boolean} primitif provoquerait un unboxing implicite (NPE si la valeur est absente).
     */
    private Boolean toBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        return val != null && Boolean.parseBoolean(String.valueOf(val));
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
            LOG.warn("Valeur entière illisible, valeur par défaut '{}' utilisée : '{}'", fallback, val, e);
            return fallback;
        }
    }
}
