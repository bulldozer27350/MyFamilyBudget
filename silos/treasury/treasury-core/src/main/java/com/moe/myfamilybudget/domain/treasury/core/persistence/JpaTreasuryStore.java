package com.moe.myfamilybudget.domain.treasury.core.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.OneOffExpenseModel;
import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.treasury.model.TreasuryMutatedEvent;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableIncomeModel;
import com.moe.myfamilybudget.domain.treasury.model.VariableOverrideModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieLineField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieList;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingField;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSnapshotWriter;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieWriter;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.treasury.port.UnknownTresorerieFieldException;

/**
 * Adaptateur JPA du silo Tresorerie (SILO-216, lot B1) : implemente {@link BudgetReader},
 * {@link TresorerieWriter} et {@link TresorerieSnapshotWriter} directement sur les tables
 * {@code cashflow_*}, sans passer par le cache global ni par le {@code PersistenceManager}.
 */
@Component
@Primary
public class JpaTreasuryStore implements BudgetReader, TresorerieWriter, TresorerieSnapshotWriter, TresorerieSettingsReader {

    private static final Logger LOG = LoggerFactory.getLogger(JpaTreasuryStore.class);

    private final CashflowIncomeRepository incomeRepo;
    private final CashflowChargeRepository chargeRepo;
    private final CashflowOneOffRepository oneOffRepo;
    private final CashflowTransferRepository transferRepo;
    private final CashflowVariableIncomeRepository variableIncomeRepo;
    private final CashflowVariableOverrideRepository variableOverrideRepo;
    private final CashflowSettingsRepository settingsRepo;
    private final ApplicationEventPublisher eventPublisher;

    public JpaTreasuryStore(CashflowIncomeRepository incomeRepo,
                            CashflowChargeRepository chargeRepo,
                            CashflowOneOffRepository oneOffRepo,
                            CashflowTransferRepository transferRepo,
                            CashflowVariableIncomeRepository variableIncomeRepo,
                            CashflowVariableOverrideRepository variableOverrideRepo,
                            CashflowSettingsRepository settingsRepo,
                            ApplicationEventPublisher eventPublisher) {
        this.incomeRepo = incomeRepo;
        this.chargeRepo = chargeRepo;
        this.oneOffRepo = oneOffRepo;
        this.transferRepo = transferRepo;
        this.variableIncomeRepo = variableIncomeRepo;
        this.variableOverrideRepo = variableOverrideRepo;
        this.settingsRepo = settingsRepo;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------------
    // BudgetReader
    // -------------------------------------------------------------------------

    @Override
    public List<IncomeModel> getIncomes() {
        return CashflowEntityMapper.toIncomeModels(incomeRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<ChargeModel> getCharges() {
        return CashflowEntityMapper.toChargeModels(chargeRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<OneOffExpenseModel> getOneoffExpenses() {
        return CashflowEntityMapper.toOneOffModels(oneOffRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<VariableIncomeModel> getVariableIncomes() {
        return CashflowEntityMapper.toVariableIncomeModels(variableIncomeRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<VariableOverrideModel> getVariableOverrides() {
        return CashflowEntityMapper.toVariableOverrideModels(variableOverrideRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public List<TransferModel> getTransfers() {
        return CashflowEntityMapper.toTransferModels(transferRepo.findAllByOrderByPositionAsc());
    }

    @Override
    public TresorerieSettingsModel getTresorerieSettings() {
        return settingsRepo.findFirstByOrderByIdAsc()
                .map(CashflowSettingsMapper::toModel)
                .orElseGet(() -> new TresorerieSettingsModel("", "manual", BigDecimal.ZERO, false, null, null, null));
    }

    // -------------------------------------------------------------------------
    // TresorerieWriter — addTresorerieRow
    // -------------------------------------------------------------------------

    @Override
    public Map<String, Object> addTresorerieRow(TresorerieList list, Map<String, Object> body) {
        String uid = (body != null && body.containsKey("id") && body.get("id") != null)
                ? String.valueOf(body.get("id"))
                : UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);

        switch (list) {
            case INCOMES -> {
                String label = getString(body, "label", "Nouveau revenu");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String start = getString(body, "start", "2026-01-01");
                String end = getString(body, "end", "2050-12-31");
                BigDecimal growthRate = getBigDecimal(body, "growthRate", BigDecimal.ZERO);
                String categoryId = getString(body, "categoryId", "");
                String notes = getString(body, "notes", "");
                IncomeModel created = new IncomeModel(uid, label, monthly, start, end, growthRate, categoryId, notes);
                List<IncomeModel> models = new ArrayList<>(getIncomes());
                models.add(created);
                rewriteIncomes(models);

                resultRow.put("label", label);
                resultRow.put("monthly", monthly);
                resultRow.put("start", start);
                resultRow.put("end", end);
                resultRow.put("growthRate", growthRate);
                resultRow.put("categoryId", categoryId);
                resultRow.put("notes", notes);
            }
            case CHARGES -> {
                String label = getString(body, "label", "Nouvelle charge");
                BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
                String start = getString(body, "start", "2026-01-01");
                String end = getString(body, "end", "2050-12-31");
                BigDecimal growthRate = getBigDecimal(body, "growthRate", BigDecimal.ZERO);
                String categoryId = getString(body, "categoryId", "");
                String notes = getString(body, "notes", "");
                ChargeModel created = new ChargeModel(uid, label, monthly, start, end, growthRate, categoryId, notes);
                List<ChargeModel> models = new ArrayList<>(getCharges());
                models.add(created);
                rewriteCharges(models);

                resultRow.put("label", label);
                resultRow.put("monthly", monthly);
                resultRow.put("start", start);
                resultRow.put("end", end);
                resultRow.put("growthRate", growthRate);
                resultRow.put("categoryId", categoryId);
                resultRow.put("notes", notes);
            }
            case ONEOFF -> {
                String label = getString(body, "label", "Nouvelle dépense");
                String date = getString(body, "date", "2026-01-01");
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String notes = getString(body, "notes", "");
                OneOffExpenseModel created = new OneOffExpenseModel(uid, label, date, amount, notes);
                List<OneOffExpenseModel> models = new ArrayList<>(getOneoffExpenses());
                models.add(created);
                rewriteOneOff(models);

                resultRow.put("label", label);
                resultRow.put("date", date);
                resultRow.put("amount", amount);
                resultRow.put("notes", notes);
            }
            case TRANSFERS -> {
                String placement = getString(body, "placement", "");
                String date = getString(body, "date", "2026-01-01");
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String notes = getString(body, "notes", "");
                TransferModel created = new TransferModel(uid, placement, date, amount, notes);
                List<TransferModel> models = new ArrayList<>();
                boolean found = false;
                for (TransferModel t : getTransfers()) {
                    if (Objects.equals(t.id(), uid)) {
                        models.add(created);
                        found = true;
                    } else {
                        models.add(t);
                    }
                }
                if (!found) {
                    models.add(created);
                }
                rewriteTransfers(models);

                resultRow.put("placement", placement);
                resultRow.put("date", date);
                resultRow.put("amount", amount);
                resultRow.put("notes", notes);
            }
            case VARIABLE_INCOMES -> {
                String label = getString(body, "label", "Nouvelle prime");
                List<IncomeModel> currentIncomes = getIncomes();
                String firstIncomeLabel = !currentIncomes.isEmpty() ? currentIncomes.get(0).label() : "";
                String refIncomeLabel = getString(body, "refIncomeLabel", firstIncomeLabel);
                BigDecimal rate = getBigDecimal(body, "rate", new BigDecimal("0.05"));
                Integer startYear = getInteger(body, "startYear", 2026);
                Integer endYear = getInteger(body, "endYear", 2050);
                String taxable = getString(body, "taxable", "Oui");
                String type = getString(body, "type", "prime");
                String notes = getString(body, "notes", "");
                VariableIncomeModel created = new VariableIncomeModel(uid, label, refIncomeLabel, rate, startYear, endYear, taxable, type, notes);
                List<VariableIncomeModel> models = new ArrayList<>(getVariableIncomes());
                models.add(created);
                rewriteVariableIncomes(models);

                resultRow.put("label", label);
                resultRow.put("refIncomeLabel", refIncomeLabel);
                resultRow.put("rate", rate);
                resultRow.put("startYear", startYear);
                resultRow.put("endYear", endYear);
                resultRow.put("taxable", taxable);
                resultRow.put("type", type);
                resultRow.put("notes", notes);
            }
            case VARIABLE_OVERRIDES -> {
                List<VariableIncomeModel> currentVars = getVariableIncomes();
                String firstVarLabel = !currentVars.isEmpty() ? currentVars.get(0).label() : "";
                String label = getString(body, "label", firstVarLabel);
                Integer year = getInteger(body, "year", LocalDate.now().getYear());
                BigDecimal amount = getBigDecimal(body, "amount", BigDecimal.ZERO);
                String taxable = getString(body, "taxable", "");
                String notes = getString(body, "notes", "");
                VariableOverrideModel created = new VariableOverrideModel(uid, label, year, amount, taxable, notes);
                List<VariableOverrideModel> models = new ArrayList<>(getVariableOverrides());
                models.add(created);
                rewriteVariableOverrides(models);

                resultRow.put("label", label);
                resultRow.put("year", year);
                resultRow.put("amount", amount);
                resultRow.put("taxable", taxable);
                resultRow.put("notes", notes);
            }
            case PLACEMENTS -> throw new IllegalArgumentException(
                    "Les placements ne relevent pas du silo Tresorerie (SILO-216) : utiliser le command service Patrimoine");
        }

        publishMutated("addTresorerieRow:" + list.key());
        return resultRow;
    }

    // -------------------------------------------------------------------------
    // TresorerieWriter — updateTresorerieRow
    // -------------------------------------------------------------------------

    @Override
    public void updateTresorerieRow(TresorerieList list, String id, TresorerieLineField field, Object value) {
        if (id == null || field == null) {
            return;
        }

        switch (list) {
            case INCOMES -> {
                List<IncomeModel> models = getIncomes().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyIncomeField(m, field, value) : m)
                        .toList();
                rewriteIncomes(models);
            }
            case CHARGES -> {
                List<ChargeModel> models = getCharges().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyChargeField(m, field, value) : m)
                        .toList();
                rewriteCharges(models);
            }
            case ONEOFF -> {
                List<OneOffExpenseModel> models = getOneoffExpenses().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyOneOffField(m, field, value) : m)
                        .toList();
                rewriteOneOff(models);
            }
            case TRANSFERS -> {
                List<TransferModel> models = getTransfers().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyTransferField(m, field, value) : m)
                        .toList();
                rewriteTransfers(models);
            }
            case VARIABLE_INCOMES -> {
                List<VariableIncomeModel> models = getVariableIncomes().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyVariableIncomeField(m, field, value) : m)
                        .toList();
                rewriteVariableIncomes(models);
            }
            case VARIABLE_OVERRIDES -> {
                List<VariableOverrideModel> models = getVariableOverrides().stream()
                        .map(m -> Objects.equals(m.id(), id) ? applyVariableOverrideField(m, field, value) : m)
                        .toList();
                rewriteVariableOverrides(models);
            }
            case PLACEMENTS -> throw new IllegalArgumentException(
                    "Les placements ne relevent pas du silo Tresorerie (SILO-216) : utiliser le command service Patrimoine");
        }

        publishMutated("updateTresorerieRow:" + list.key());
    }

    // -------------------------------------------------------------------------
    // TresorerieWriter — removeTresorerieRow
    // -------------------------------------------------------------------------

    @Override
    public void removeTresorerieRow(TresorerieList list, String id) {
        if (id == null) {
            return;
        }

        switch (list) {
            case INCOMES -> rewriteIncomes(getIncomes().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case CHARGES -> rewriteCharges(getCharges().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case ONEOFF -> rewriteOneOff(getOneoffExpenses().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case TRANSFERS -> rewriteTransfers(getTransfers().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case VARIABLE_INCOMES -> rewriteVariableIncomes(getVariableIncomes().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case VARIABLE_OVERRIDES -> rewriteVariableOverrides(getVariableOverrides().stream().filter(m -> !Objects.equals(m.id(), id)).toList());
            case PLACEMENTS -> throw new IllegalArgumentException(
                    "Les placements ne relevent pas du silo Tresorerie (SILO-216) : utiliser le command service Patrimoine");
        }

        publishMutated("removeTresorerieRow:" + list.key());
    }

    // -------------------------------------------------------------------------
    // TresorerieWriter — applyTresorerieAjustement
    // -------------------------------------------------------------------------

    @Override
    public void applyTresorerieAjustement(String lineId, TresorerieAdjustmentKind kind, BigDecimal newMonthly) {
        if (lineId == null || kind == null || newMonthly == null) {
            return;
        }

        switch (kind) {
            case CHARGE -> updateTresorerieRow(TresorerieList.CHARGES, lineId, TresorerieLineField.MONTHLY, newMonthly);
            case INCOME -> updateTresorerieRow(TresorerieList.INCOMES, lineId, TresorerieLineField.MONTHLY, newMonthly);
            case PLACEMENT -> throw new IllegalArgumentException(
                    "Les placements ne relevent pas du silo Tresorerie (SILO-216) : utiliser le command service Patrimoine");
        }
    }

    // -------------------------------------------------------------------------
    // TresorerieWriter — updateTresorerieSetting
    // -------------------------------------------------------------------------

    @Override
    public void updateTresorerieSetting(TresorerieSettingField field, Object value) {
        if (field == null) {
            return;
        }

        CashflowSettingsEntity entity = settingsRepo.findFirstByOrderByIdAsc()
                .orElseGet(CashflowSettingsEntity::new);

        switch (field) {
            case PIVOT_DATE -> entity.setPivotDate(value != null ? String.valueOf(value) : "");
            case PIVOT_MODE -> entity.setPivotMode(value != null ? String.valueOf(value) : "");
            case START_BALANCE, PIVOT_BALANCE_MANUAL -> entity.setStartBalance(toBigDecimal(value, BigDecimal.ZERO));
            case SWEEP_ENABLED -> entity.setSweepEnabled(toBoolean(value));
            case CASH_CEILING -> entity.setCashCeiling(toBigDecimal(value, null));
            case CASH_FLOOR -> entity.setCashFloor(toBigDecimal(value, null));
            case CASH_ALERT_THRESHOLD -> entity.setCashAlertThreshold(toBigDecimal(value, null));
        }

        settingsRepo.save(entity);
        publishMutated("updateTresorerieSetting");
    }

    // -------------------------------------------------------------------------
    // TresorerieSnapshotWriter
    // -------------------------------------------------------------------------

    @Override
    public void replace(TresorerieSettingsModel settings, List<IncomeModel> incomes, List<ChargeModel> charges,
                        List<OneOffExpenseModel> oneoffExpenses, List<VariableIncomeModel> variableIncomes,
                        List<VariableOverrideModel> variableOverrides, List<TransferModel> transfers) {
        rewriteIncomes(orEmpty(incomes));
        rewriteCharges(orEmpty(charges));
        rewriteOneOff(orEmpty(oneoffExpenses));
        rewriteVariableIncomes(orEmpty(variableIncomes));
        rewriteVariableOverrides(orEmpty(variableOverrides));
        rewriteTransfers(orEmpty(transfers));

        if (settings != null) {
            settingsRepo.deleteAll();
            settingsRepo.flush();
            settingsRepo.save(CashflowSettingsMapper.toEntity(settings));
        }

        publishMutated("replace");
    }

    @Override
    public void reset() {
        rewriteIncomes(List.of());
        rewriteCharges(List.of());
        rewriteOneOff(List.of());
        rewriteVariableIncomes(List.of());
        rewriteVariableOverrides(List.of());
        rewriteTransfers(List.of());
        settingsRepo.deleteAll();
        settingsRepo.flush();
        publishMutated("reset");
    }

    // -------------------------------------------------------------------------
    // Private rewrite helpers
    // -------------------------------------------------------------------------

    private void rewriteIncomes(List<IncomeModel> models) {
        incomeRepo.deleteAll();
        incomeRepo.flush();
        if (!models.isEmpty()) {
            incomeRepo.saveAll(CashflowEntityMapper.toIncomeEntities(models));
        }
    }

    private void rewriteCharges(List<ChargeModel> models) {
        chargeRepo.deleteAll();
        chargeRepo.flush();
        if (!models.isEmpty()) {
            chargeRepo.saveAll(CashflowEntityMapper.toChargeEntities(models));
        }
    }

    private void rewriteOneOff(List<OneOffExpenseModel> models) {
        oneOffRepo.deleteAll();
        oneOffRepo.flush();
        if (!models.isEmpty()) {
            oneOffRepo.saveAll(CashflowEntityMapper.toOneOffEntities(models));
        }
    }

    private void rewriteTransfers(List<TransferModel> models) {
        transferRepo.deleteAll();
        transferRepo.flush();
        if (!models.isEmpty()) {
            transferRepo.saveAll(CashflowEntityMapper.toTransferEntities(models));
        }
    }

    private void rewriteVariableIncomes(List<VariableIncomeModel> models) {
        variableIncomeRepo.deleteAll();
        variableIncomeRepo.flush();
        if (!models.isEmpty()) {
            variableIncomeRepo.saveAll(CashflowEntityMapper.toVariableIncomeEntities(models));
        }
    }

    private void rewriteVariableOverrides(List<VariableOverrideModel> models) {
        variableOverrideRepo.deleteAll();
        variableOverrideRepo.flush();
        if (!models.isEmpty()) {
            variableOverrideRepo.saveAll(CashflowEntityMapper.toVariableOverrideEntities(models));
        }
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new TreasuryMutatedEvent(mutationKind));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : List.of();
    }

    // -------------------------------------------------------------------------
    // Field update mappers
    // -------------------------------------------------------------------------

    private static IncomeModel applyIncomeField(IncomeModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case LABEL -> new IncomeModel(m.id(), toStringOrEmpty(value), m.monthly(), m.start(), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case MONTHLY -> new IncomeModel(m.id(), m.label(), toBigDecimal(value, BigDecimal.ZERO), m.start(), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case START -> new IncomeModel(m.id(), m.label(), m.monthly(), toStringOrEmpty(value), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case END -> new IncomeModel(m.id(), m.label(), m.monthly(), m.start(), toStringOrEmpty(value), m.growthRate(), m.categoryId(), m.notes());
            case GROWTH_RATE -> new IncomeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), toBigDecimal(value, BigDecimal.ZERO), m.categoryId(), m.notes());
            case CATEGORY_ID -> new IncomeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), m.growthRate(), toStringOrEmpty(value), m.notes());
            case NOTES -> new IncomeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), m.growthRate(), m.categoryId(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("incomes", field.key());
        };
    }

    private static ChargeModel applyChargeField(ChargeModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case LABEL -> new ChargeModel(m.id(), toStringOrEmpty(value), m.monthly(), m.start(), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case MONTHLY -> new ChargeModel(m.id(), m.label(), toBigDecimal(value, BigDecimal.ZERO), m.start(), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case START -> new ChargeModel(m.id(), m.label(), m.monthly(), toStringOrEmpty(value), m.end(), m.growthRate(), m.categoryId(), m.notes());
            case END -> new ChargeModel(m.id(), m.label(), m.monthly(), m.start(), toStringOrEmpty(value), m.growthRate(), m.categoryId(), m.notes());
            case GROWTH_RATE -> new ChargeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), toBigDecimal(value, BigDecimal.ZERO), m.categoryId(), m.notes());
            case CATEGORY_ID -> new ChargeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), m.growthRate(), toStringOrEmpty(value), m.notes());
            case NOTES -> new ChargeModel(m.id(), m.label(), m.monthly(), m.start(), m.end(), m.growthRate(), m.categoryId(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("charges", field.key());
        };
    }

    private static OneOffExpenseModel applyOneOffField(OneOffExpenseModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case LABEL -> new OneOffExpenseModel(m.id(), toStringOrEmpty(value), m.date(), m.amount(), m.notes());
            case DATE -> new OneOffExpenseModel(m.id(), m.label(), toStringOrEmpty(value), m.amount(), m.notes());
            case AMOUNT -> new OneOffExpenseModel(m.id(), m.label(), m.date(), toBigDecimal(value, BigDecimal.ZERO), m.notes());
            case NOTES -> new OneOffExpenseModel(m.id(), m.label(), m.date(), m.amount(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("oneoff", field.key());
        };
    }

    private static TransferModel applyTransferField(TransferModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case PLACEMENT -> new TransferModel(m.id(), toStringOrEmpty(value), m.date(), m.amount(), m.notes());
            case DATE -> new TransferModel(m.id(), m.placement(), toStringOrEmpty(value), m.amount(), m.notes());
            case AMOUNT -> new TransferModel(m.id(), m.placement(), m.date(), toBigDecimal(value, BigDecimal.ZERO), m.notes());
            case NOTES -> new TransferModel(m.id(), m.placement(), m.date(), m.amount(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("transfers", field.key());
        };
    }

    private static VariableIncomeModel applyVariableIncomeField(VariableIncomeModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case LABEL -> new VariableIncomeModel(m.id(), toStringOrEmpty(value), m.refIncomeLabel(), m.rate(), m.startYear(), m.endYear(), m.taxable(), m.type(), m.notes());
            case REF_INCOME_LABEL -> new VariableIncomeModel(m.id(), m.label(), toStringOrEmpty(value), m.rate(), m.startYear(), m.endYear(), m.taxable(), m.type(), m.notes());
            case RATE -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), toBigDecimal(value, new BigDecimal("0.05")), m.startYear(), m.endYear(), m.taxable(), m.type(), m.notes());
            case START_YEAR -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), m.rate(), toInteger(value, 2026), m.endYear(), m.taxable(), m.type(), m.notes());
            case END_YEAR -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), m.rate(), m.startYear(), toInteger(value, 2050), m.taxable(), m.type(), m.notes());
            case TAXABLE -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), m.rate(), m.startYear(), m.endYear(), toStringOrEmpty(value), m.type(), m.notes());
            case TYPE -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), m.rate(), m.startYear(), m.endYear(), m.taxable(), toStringOrEmpty(value), m.notes());
            case NOTES -> new VariableIncomeModel(m.id(), m.label(), m.refIncomeLabel(), m.rate(), m.startYear(), m.endYear(), m.taxable(), m.type(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("variableIncomes", field.key());
        };
    }

    private static VariableOverrideModel applyVariableOverrideField(VariableOverrideModel m, TresorerieLineField field, Object value) {
        return switch (field) {
            case LABEL -> new VariableOverrideModel(m.id(), toStringOrEmpty(value), m.year(), m.amount(), m.taxable(), m.notes());
            case YEAR -> new VariableOverrideModel(m.id(), m.label(), toInteger(value, LocalDate.now().getYear()), m.amount(), m.taxable(), m.notes());
            case AMOUNT -> new VariableOverrideModel(m.id(), m.label(), m.year(), toBigDecimal(value, BigDecimal.ZERO), m.taxable(), m.notes());
            case TAXABLE -> new VariableOverrideModel(m.id(), m.label(), m.year(), m.amount(), toStringOrEmpty(value), m.notes());
            case NOTES -> new VariableOverrideModel(m.id(), m.label(), m.year(), m.amount(), m.taxable(), toStringOrEmpty(value));
            default -> throw new UnknownTresorerieFieldException("variableOverrides", field.key());
        };
    }

    // -------------------------------------------------------------------------
    // Value conversion helpers
    // -------------------------------------------------------------------------

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private static String toStringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toBigDecimal(map.get(key), defaultValue);
    }

    private static Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toInteger(map.get(key), defaultValue);
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return fallback;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            LOG.warn("Valeur numerique decimale illisible, repli utilise : '{}'", value);
            return fallback;
        }
    }

    private static Integer toInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Exception e) {
            LOG.warn("Valeur entiere illisible, repli utilise : '{}'", value);
            return fallback;
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
