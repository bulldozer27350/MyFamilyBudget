package com.moe.myfamilybudget.server.internal.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.server.internal.model.AssetCategoryModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.PlacementHistoryEntryModel;
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
 * l'audit, {@link com.moe.myfamilybudget.server.internal.updater.TresorerieFieldUpdateDispatcher}),
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

    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        String uid = (body != null && body.containsKey("id") && body.get("id") != null)
                ? String.valueOf(body.get("id"))
                : UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
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

        return resultRow;
    }

    /**
     * Met à jour une cellule d'une ligne de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides, placements).
     */
    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        if (listKey == null || id == null || field == null) {
            return;
        }

        // Le if/else répété par type de ligne (incomes/charges/oneoff/...) et par champ a été
        // déplacé dans server.internal.updater.TresorerieFieldUpdateDispatcher : cf. le point 3
        // de l'audit. Comportement inchangé pour tout champ/listKey déjà valide ; un listKey ou
        // un field inconnu lève désormais UnknownTresorerieFieldException au lieu de renvoyer
        // silencieusement l'état inchangé.
        cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            return com.moe.myfamilybudget.server.internal.updater.TresorerieFieldUpdateDispatcher
                    .update(base, listKey, id, field, value);
        });
    }

    /**
     * Supprime une ligne d'une section de trésorerie.
     */
    public void removeTresorerieRow(String listKey, String id) {
        if (listKey == null || id == null) {
            return;
        }

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();

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
     * Met à jour un paramètre lié aux impôts ou généraux dans Settings.
     */
    public void updateTaxSettings(String field, Object value) {
        if (field == null) return;
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
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
    }

    /**
     * Met à jour une cellule d'une catégorie d'actif.
     */
    public void updateAssetCategory(String id, String field, Object value) {
        if (id == null || field == null) return;
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
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
    }

    /**
     * Ajoute une nouvelle catégorie d'actif.
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
     * Supprime une catégorie d'actif par ID.
     */
    public void removeAssetCategory(String id) {
        if (id == null) return;
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<AssetCategoryModel> list = base.getEffectiveAssetCategories().stream()
                    .filter(c -> !Objects.equals(c.id(), id))
                    .toList();
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
     * Supprime une ligne de patrimoine (placements, transfers, loans/credits, realEstate).
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
    }

    /**
     * Ajoute une valeur reelle constatee a l'historique d'un placement (fenetre dediee
     * "Historique" de l'onglet Patrimoine). Cree une ligne d'historique independante,
     * conservee telle quelle (contrairement a l'ancienne implementation qui se contentait
     * d'ecraser le solde de reference du placement).
     */
    public Map<String, Object> addPlacementHistoryEntry(String placementId, Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        if (placementId == null || body == null) return result;

        String uid = "hist_" + UUID.randomUUID().toString().substring(0, 8);
        String date = getString(body, "date", LocalDate.now().toString());
        BigDecimal value = getBigDecimal(body, "value", BigDecimal.ZERO);
        String notes = getString(body, "notes", "");

        PlacementHistoryEntryModel entry = new PlacementHistoryEntryModel(uid, date, value, notes);
        result.put("id", uid);
        result.put("date", date);
        result.put("value", value);
        result.put("notes", notes);

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<PlacementModel> list = base.getEffectivePlacements().stream()
                    .map(p -> {
                        if (!Objects.equals(p.id(), placementId)) return p;
                        List<PlacementHistoryEntryModel> history = new ArrayList<>(p.getEffectiveHistory());
                        history.add(entry);
                        return withHistory(p, history);
                    })
                    .toList();
            return base.withPlacements(list);
        });
        return result;
    }

    /**
     * Met a jour une ligne existante de l'historique d'un placement (date, valeur ou notes).
     */
    public Map<String, Object> updatePlacementHistoryEntry(String placementId, String entryId, Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        if (placementId == null || entryId == null || body == null) return result;

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<PlacementModel> list = base.getEffectivePlacements().stream()
                    .map(p -> {
                        if (!Objects.equals(p.id(), placementId)) return p;
                        List<PlacementHistoryEntryModel> history = p.getEffectiveHistory().stream()
                                .map(h -> {
                                    if (!Objects.equals(h.id(), entryId)) return h;
                                    String date = body.containsKey("date") ? getString(body, "date", h.date()) : h.date();
                                    BigDecimal value = body.containsKey("value") ? getBigDecimal(body, "value", h.value()) : h.value();
                                    String notes = body.containsKey("notes") ? getString(body, "notes", h.notes()) : h.notes();
                                    return new PlacementHistoryEntryModel(h.id(), date, value, notes);
                                })
                                .toList();
                        return withHistory(p, history);
                    })
                    .toList();
            return base.withPlacements(list);
        });
        return result;
    }

    /**
     * Supprime une ligne de l'historique d'un placement.
     */
    public void deletePlacementHistoryEntry(String placementId, String entryId) {
        if (placementId == null || entryId == null) return;

        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            List<PlacementModel> list = base.getEffectivePlacements().stream()
                    .map(p -> {
                        if (!Objects.equals(p.id(), placementId)) return p;
                        List<PlacementHistoryEntryModel> history = p.getEffectiveHistory().stream()
                                .filter(h -> !Objects.equals(h.id(), entryId))
                                .toList();
                        return withHistory(p, history);
                    })
                    .toList();
            return base.withPlacements(list);
        });
    }

    /**
     * PlacementModel n'a pas de methode "withHistory" (ce n'est pas un besoin ailleurs dans le
     * code) : ce petit utilitaire local reconstruit un PlacementModel identique avec un
     * historique different, en reutilisant le constructeur complet (19 champs).
     */
    private PlacementModel withHistory(PlacementModel p, List<PlacementHistoryEntryModel> history) {
        PlacementModel next = new PlacementModel(
                p.id(), p.label(), p.category(), p.balance(), p.balanceDate(),
                p.monthly(), p.monthlyFrom(), p.monthlyUntil(),
                p.ratePess(), p.rateCorr(), p.rateOpti(),
                p.excludedFromRetirement(), p.notes(),
                p.sweepPriority(), p.sweepCap(), p.pauseTriggerBalance(),
                p.pausePriority(), p.categoryId(), history
        );
        return syncBalanceFromHistory(next);
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

    /**
     * Obtient les données d'import bancaire.
     */
    public BankImportModel getBankImport() {
        return cacheStore.getBudgetData().bankImport();
    }

    /**
     * Met à jour les données d'import bancaire.
     */
    public void updateBankImport(BankImportModel bankImport) {
        if (bankImport == null) return;
        BudgetDataModel updated = cacheStore.applyAndPersist(current -> {
            BudgetDataModel base = current != null ? current : cacheStore.createDefaultBudgetData();
            return base.withBankImport(bankImport);
        });
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
