package com.moe.myfamilybudget.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.wealth.model.AssetCategoryModel;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementHistoryEntryModel;
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
     * Sauvegarde ou crée une ligne de patrimoine (placements). Les prêts s'écrivent dans leur silo
     * (SILO-214, lot B) ; R-30 (reste à nettoyer) : l'immobilier (listKey {@code realEstate}) ne passe plus
     * par ici (silo Patrimoine, {@code JpaWealthStore}) et a été retiré, de même que
     * {@code updatePlacementMonthly}, {@code updateAssetCategory}, {@code removeAssetCategory},
     * {@code replacePatrimoineSnapshot}/{@code resetPatrimoineSnapshot} et l'historique de valorisation
     * ({@code add}/{@code update}/{@code deletePlacementHistoryEntry}), tous devenus du code mort (zéro
     * appelant en production ni dans les tests). {@code savePatrimoineRow}, {@code deletePatrimoineRow} et
     * {@code addAssetCategory} restent : {@link com.moe.myfamilybudget.persistence.WriteFailureKeepsMemoryTest}
     * (VT-330) les exerce volontairement pour garder la couverture de l'ordre DB -> mémoire du cache.
     */
    public Map<String, Object> savePatrimoineRow(String listKey, Map<String, Object> body) {
        Map<String, Object> resultRow = new HashMap<>();
        String givenId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String uid = (givenId != null && !givenId.trim().isEmpty()) ? givenId : ("pat_" + UUID.randomUUID().toString().substring(0, 8));
        resultRow.put("id", uid);

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
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

                // L'historique de valorisation (releves reels) n'est pas edite depuis ce
                // formulaire generique : on le preserve tel quel pour la ligne existante afin
                // de ne pas l'ecraser a chaque enregistrement du placement.
                List<PlacementHistoryEntryModel> existingHistory = base.getEffectivePlacements().stream()
                        .filter(p -> Objects.equals(p.id(), uid))
                        .findFirst()
                        .map(PlacementModel::getEffectiveHistory)
                        .orElse(List.of());

                PlacementModel model = new PlacementModel(uid, label, category, balance, balanceDate, monthly,
                        monthlyFrom, monthlyUntil, ratePess, rateCorr, rateOpti, excludedFromRetirement, notes,
                        sweepPriority, sweepCap, pauseTriggerBalance, pausePriority, categoryId, existingHistory);
                model = syncBalanceFromHistory(model);

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
            }

            return base;
        });

        return resultRow;
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
     * {@code updateTaxSettings(String, Object)}. Le stockage reste {@code SettingsEntity} (séparation relevant
     * des patchs DB-xxx) ; seul le champ ciblé est modifié, les autres sont recopiés tels quels, y compris
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
     * Ajoute une nouvelle catégorie d'actif. R-30 (reste à nettoyer) : conservée pour
     * {@link com.moe.myfamilybudget.persistence.WriteFailureKeepsMemoryTest} (VT-330) ; voir le commentaire de
     * {@link #savePatrimoineRow}. {@code updateAssetCategory} et {@code removeAssetCategory}, non exercées par
     * ce test, étaient du code mort et ont été retirées.
     */
    public void addAssetCategory(AssetCategoryModel category) {
        if (category == null) return;
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<AssetCategoryModel> list = new ArrayList<>(base.getEffectiveAssetCategories());
            list.add(category);
            return base.withAssetCategories(list);
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

    /**
     * Supprime une ligne de patrimoine (placements).
     */
    public void deletePatrimoineRow(String listKey, String id) {
        if (listKey == null || id == null) {
            return;
        }

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();

            if ("placements".equalsIgnoreCase(listKey)) {
                List<PlacementModel> list = base.getEffectivePlacements().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return base.withPlacements(list);
            }

            return base;
        });
    }

    /**
     * Le solde de reference (balance/balanceDate) du placement reste la source utilisee par
     * Tresorerie et Vue d'ensemble (calculateDetailedFinancialTimeline cote front, projections
     * cote back). Depuis que la saisie du solde a ete retiree du formulaire d'edition du
     * placement (elle se fait desormais uniquement via l'historique), on le resynchronise ici
     * automatiquement sur la derniere valeur d'historique connue a chaque mutation, pour que
     * ces deux vues restent a jour sans action supplementaire de l'utilisateur. Les dates ISO
     * (yyyy-MM-dd) se comparent correctement en tant que chaines, pas besoin de les parser.
     */
    private PlacementModel syncBalanceFromHistory(PlacementModel p) {
        List<PlacementHistoryEntryModel> history = p.getEffectiveHistory();
        PlacementHistoryEntryModel latest = history.stream()
                .filter(h -> h.date() != null)
                .max(Comparator.comparing(PlacementHistoryEntryModel::date))
                .orElse(null);
        if (latest == null) return p;
        return new PlacementModel(
                p.id(), p.label(), p.category(), latest.getEffectiveValue(), latest.date(),
                p.monthly(), p.monthlyFrom(), p.monthlyUntil(),
                p.ratePess(), p.rateCorr(), p.rateOpti(),
                p.excludedFromRetirement(), p.notes(),
                p.sweepPriority(), p.sweepCap(), p.pauseTriggerBalance(),
                p.pausePriority(), p.categoryId(), history
        );
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
