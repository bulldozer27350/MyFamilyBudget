package com.moe.myfamilybudget.server.internal.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
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

import jakarta.annotation.PostConstruct;

/**
 * Gestionnaire de persistance en mémoire (in-memory).
 * Les données sont conservées en mémoire vive et réinitialisées à chaque redémarrage du serveur.
 * Prévu pour être refactorisé ultérieurement vers une persistance fichier ou base de données.
 */
@Component
public class PersistenceManager {

    private final AtomicReference<BudgetDataModel> currentBudget = new AtomicReference<>();

    @PostConstruct
    public void init() {
        if (currentBudget.get() == null) {
            currentBudget.set(createDefaultBudgetData());
        }
    }

    /**
     * Récupère le modèle de budget complet actuellement en mémoire.
     */
    public BudgetDataModel getBudgetData() {
        BudgetDataModel model = currentBudget.get();
        if (model == null) {
            model = createDefaultBudgetData();
            currentBudget.set(model);
        }
        return model;
    }

    /**
     * Remplace l'intégralité du modèle de données (utilisé lors de l'import JSON).
     */
    public void setBudgetData(BudgetDataModel data) {
        if (data != null) {
            currentBudget.set(data);
        } else {
            currentBudget.set(createDefaultBudgetData());
        }
    }

    /**
     * Réinitialise les données aux valeurs par défaut.
     */
    public BudgetDataModel resetData() {
        BudgetDataModel defaultData = createDefaultBudgetData();
        currentBudget.set(defaultData);
        return defaultData;
    }

    /**
     * Ajoute une nouvelle ligne dans une section de trésorerie (incomes, charges, oneoff, variableIncomes, variableOverrides).
     */
    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        String uid = (body != null && body.containsKey("id") && body.get("id") != null)
                ? String.valueOf(body.get("id"))
                : UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);

        currentBudget.updateAndGet(current -> {
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

                return new BudgetDataModel(base.settings(), list, base.charges(), base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
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

                return new BudgetDataModel(base.settings(), base.incomes(), list, base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
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

                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), list, base.transfers(),
                        base.variableIncomes(), base.variableOverrides(), base.bankImport());
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = new ArrayList<>(base.getEffectiveVariableIncomes());
                String label = getString(body, "label", "Nouvelle prime");
                String type = getString(body, "type", "Prime");
                String firstIncomeLabel = !base.getEffectiveIncomes().isEmpty() ? base.getEffectiveIncomes().get(0).label() : "";
                String refIncomeLabel = getString(body, "refIncomeLabel", firstIncomeLabel);
                BigDecimal rate = getBigDecimal(body, "rate", new BigDecimal("0.05"));
                Integer startYear = getInteger(body, "startYear", 2026);
                Integer endYear = getInteger(body, "endYear", retireYear);
                String taxable = getString(body, "taxable", "Oui");
                String notes = getString(body, "notes", "");

                VariableIncomeModel created = new VariableIncomeModel(uid, label, type, refIncomeLabel, rate, startYear, endYear, taxable, notes);
                list.add(created);

                resultRow.put("label", label);
                resultRow.put("type", type);
                resultRow.put("refIncomeLabel", refIncomeLabel);
                resultRow.put("rate", rate);
                resultRow.put("startYear", startYear);
                resultRow.put("endYear", endYear);
                resultRow.put("taxable", taxable);
                resultRow.put("notes", notes);

                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        list, base.variableOverrides(), base.bankImport());
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

                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        base.variableIncomes(), list, base.bankImport());
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

        currentBudget.updateAndGet(current -> {
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
                return new BudgetDataModel(base.settings(), list, base.charges(), base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
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
                return new BudgetDataModel(base.settings(), base.incomes(), list, base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
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
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), list, base.transfers(),
                        base.variableIncomes(), base.variableOverrides(), base.bankImport());
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = new ArrayList<>();
                for (VariableIncomeModel r : base.getEffectiveVariableIncomes()) {
                    if (Objects.equals(r.id(), id)) {
                        list.add(new VariableIncomeModel(
                                r.id(),
                                "label".equals(field) ? (value != null ? String.valueOf(value) : "") : r.label(),
                                "type".equals(field) ? (value != null ? String.valueOf(value) : "") : r.type(),
                                "refIncomeLabel".equals(field) ? (value != null ? String.valueOf(value) : "") : r.refIncomeLabel(),
                                "rate".equals(field) ? toBigDecimal(value, new BigDecimal("0.05")) : r.rate(),
                                "startYear".equals(field) ? toInteger(value, 2026) : r.startYear(),
                                "endYear".equals(field) ? toInteger(value, 2049) : r.endYear(),
                                "taxable".equals(field) ? (value != null ? String.valueOf(value) : "") : r.taxable(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        list, base.variableOverrides(), base.bankImport());
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
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        base.variableIncomes(), list, base.bankImport());
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
                                "sweepPriority".equals(field) ? (value != null ? String.valueOf(value) : "") : r.sweepPriority(),
                                "sweepCap".equals(field) ? (value != null ? String.valueOf(value) : "") : r.sweepCap(),
                                "pauseTriggerBalance".equals(field) ? (value != null ? String.valueOf(value) : "") : r.pauseTriggerBalance(),
                                "pausePriority".equals(field) ? (value != null ? String.valueOf(value) : "") : r.pausePriority(),
                                "notes".equals(field) ? (value != null ? String.valueOf(value) : "") : r.notes(),
                                r.valueHistory()
                        ));
                    } else {
                        list.add(r);
                    }
                }
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), list, base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
            }

            return base;
        });
    }

    /**
     * Supprime une ligne d'une section de trésorerie.
     */
    public void removeTresorerieRow(String listKey, String id) {
        if (listKey == null || id == null) {
            return;
        }

        currentBudget.updateAndGet(current -> {
            BudgetDataModel base = current != null ? current : createDefaultBudgetData();

            if ("incomes".equalsIgnoreCase(listKey)) {
                List<IncomeModel> list = base.getEffectiveIncomes().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return new BudgetDataModel(base.settings(), list, base.charges(), base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
            } else if ("charges".equalsIgnoreCase(listKey)) {
                List<ChargeModel> list = base.getEffectiveCharges().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return new BudgetDataModel(base.settings(), base.incomes(), list, base.placements(), base.realEstate(),
                        base.retirement(), base.taxChildren(), base.taxBrackets(), base.taxRateOverrides(),
                        base.taxActualOverrides(), base.oneoff(), base.transfers(), base.variableIncomes(),
                        base.variableOverrides(), base.bankImport());
            } else if ("oneoff".equalsIgnoreCase(listKey)) {
                List<OneOffExpenseModel> list = base.getEffectiveOneoff().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), list, base.transfers(),
                        base.variableIncomes(), base.variableOverrides(), base.bankImport());
            } else if ("variableIncomes".equalsIgnoreCase(listKey)) {
                List<VariableIncomeModel> list = base.getEffectiveVariableIncomes().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        list, base.variableOverrides(), base.bankImport());
            } else if ("variableOverrides".equalsIgnoreCase(listKey)) {
                List<VariableOverrideModel> list = base.getEffectiveVariableOverrides().stream()
                        .filter(r -> !Objects.equals(r.id(), id))
                        .toList();
                return new BudgetDataModel(base.settings(), base.incomes(), base.charges(), base.placements(),
                        base.realEstate(), base.retirement(), base.taxChildren(), base.taxBrackets(),
                        base.taxRateOverrides(), base.taxActualOverrides(), base.oneoff(), base.transfers(),
                        base.variableIncomes(), list, base.bankImport());
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
                90,
                BigDecimal.ZERO,
                21,
                new BigDecimal("0.10"),
                new BigDecimal("0.02"),
                "",
                "manual"
        );

        RetirementModel retirement = new RetirementModel(
                new BigDecimal("47100"),
                new BigDecimal("0.015"),
                new BigDecimal("1.4386"),
                "2025-11-01",
                new BigDecimal("0.01"),
                Collections.emptyList()
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
                bankImport
        );
    }
}
