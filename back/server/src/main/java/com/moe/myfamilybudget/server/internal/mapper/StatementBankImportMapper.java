package com.moe.myfamilybudget.server.internal.mapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.api.model.BankTransactionSplitDto;
import com.moe.myfamilybudget.server.internal.model.AutoMatchResultModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportSummaryModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.model.OneOffExpenseModel;
import com.moe.myfamilybudget.server.internal.model.PendingImportSummaryModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;

/**
 * Mapper responsible for converting between OpenAPI DTOs / Request Maps
 * and internal domain BankImportModel records.
 */
@Component
public class StatementBankImportMapper {

    public Map<String, Object> toBankImportResponseMap(BankImportModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> res = new HashMap<>();

        res.put("columnMapping", toColumnMappingMap(model.columnMapping()));
        res.put("categories", model.categories() != null ? model.categories().stream().map(this::toCategoryMap).collect(Collectors.toList()) : Collections.emptyList());
        res.put("rules", model.rules() != null ? model.rules().stream().map(this::toRuleMap).collect(Collectors.toList()) : Collections.emptyList());
        res.put("transactions", model.transactions() != null ? model.transactions().stream().map(this::toTransactionMap).collect(Collectors.toList()) : Collections.emptyList());
        res.put("pendingOperations", model.pendingOperations() != null ? model.pendingOperations().stream().map(this::toPendingOperationMap).collect(Collectors.toList()) : Collections.emptyList());

        return res;
    }

    public Map<String, Object> toColumnMappingMap(BankImportModel.BankColumnMappingModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("delimiter", model.delimiter());
        map.put("dateFormat", model.dateFormat());
        map.put("hasHeader", model.hasHeader());
        map.put("dateCol", model.dateCol());
        map.put("labelCol", model.labelCol());
        map.put("typeCol", model.typeCol());
        map.put("amountCol", model.amountCol());
        return map;
    }

    public BankImportModel.BankColumnMappingModel toColumnMappingModel(Map<String, Object> map) {
        if (map == null) return new BankImportModel.BankColumnMappingModel(";", "DD/MM/YYYY", true, null, null, null, null);
        String delimiter = (String) map.getOrDefault("delimiter", ";");
        String dateFormat = (String) map.getOrDefault("dateFormat", "DD/MM/YYYY");
        Boolean hasHeader = map.containsKey("hasHeader") ? (Boolean) map.get("hasHeader") : true;

        Integer dateCol = toInteger(map.get("dateCol"));
        Integer labelCol = toInteger(map.get("labelCol"));
        Integer typeCol = toInteger(map.get("typeCol"));
        Integer amountCol = toInteger(map.get("amountCol"));

        return new BankImportModel.BankColumnMappingModel(delimiter, dateFormat, hasHeader, dateCol, labelCol, typeCol, amountCol);
    }

    public Map<String, Object> toCategoryMap(BankImportModel.CategoryModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.id());
        map.put("label", model.label());
        map.put("kind", model.kind());
        map.put("compressible", model.compressible());
        return map;
    }

    public BankImportModel.CategoryModel toCategoryModel(Map<String, Object> map) {
        if (map == null) return new BankImportModel.CategoryModel(null, "");
        String id = (String) map.get("id");
        String label = (String) map.getOrDefault("label", "");
        String kind = (String) map.getOrDefault("kind", "Dépense");
        String compressible = (String) map.getOrDefault("compressible", "Non");
        return new BankImportModel.CategoryModel(id, label, kind, compressible);
    }

    public Map<String, Object> toRuleMap(BankImportModel.BankImportRuleModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.id());
        map.put("matchText", model.matchText());
        map.put("categoryId", model.categoryId());
        return map;
    }

    public BankImportModel.BankImportRuleModel toRuleModel(Map<String, Object> map) {
        if (map == null) return new BankImportModel.BankImportRuleModel(null, "", "");
        String id = (String) map.get("id");
        String matchText = (String) map.getOrDefault("matchText", "");
        String categoryId = (String) map.getOrDefault("categoryId", "");
        return new BankImportModel.BankImportRuleModel(id, matchText, categoryId);
    }

    public Map<String, Object> toTransactionMap(BankImportModel.BankTransactionModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.id());
        map.put("date", model.date());
        map.put("label", model.label());
        map.put("type", model.type());
        map.put("amount", model.amount());
        map.put("categoryId", model.categoryId());
        if (model.splits() != null && !model.splits().isEmpty()) {
            map.put("splits", model.splits().stream().map(this::toSplitMap).collect(Collectors.toList()));
        } else {
            map.put("splits", Collections.emptyList());
        }
        return map;
    }

    public Map<String, Object> toSplitMap(BankImportModel.BankTransactionSplitModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.id());
        map.put("categoryId", model.categoryId());
        map.put("amount", model.amount());
        map.put("label", model.label());
        return map;
    }

    public BankImportModel.BankTransactionSplitModel toSplitModel(BankTransactionSplitDto item) {
        if (item == null) return new BankImportModel.BankTransactionSplitModel(null, "", BigDecimal.ZERO, "");
        String id = item.getId();
        String categoryId = item.getCategoryId();
        BigDecimal amount = item.getAmount();
        String label = item.getLabel();
        return new BankImportModel.BankTransactionSplitModel(id, categoryId, amount, label);
    }

    public List<BankImportModel.BankTransactionSplitModel> toSplitList(List<BankTransactionSplitDto> body) {
            return body.stream()
                    .map(item -> toSplitModel(item))
                    .collect(Collectors.toList());
    }

//    

    public Map<String, Object> toPendingOperationMap(BankImportModel.PendingOperationModel model) {
        if (model == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", model.id());
        map.put("date", model.date());
        map.put("expectedDate", model.expectedDate());
        map.put("type", model.type());
        map.put("refNumber", model.refNumber());
        map.put("label", model.label());
        map.put("amount", model.amount());
        map.put("categoryId", model.categoryId());
        map.put("status", model.status());
        map.put("linkedTxId", model.linkedTxId());
        map.put("clearedDate", model.clearedDate());
        map.put("notes", model.notes());
        return map;
    }

    public BankImportModel.PendingOperationModel toPendingOperationModel(Map<String, Object> map) {
        if (map == null) return new BankImportModel.PendingOperationModel(null, "", "", "cb", "", "", BigDecimal.ZERO, "", "pending", null, null, "");
        String id = (String) map.get("id");
        String date = (String) map.getOrDefault("date", "");
        String expectedDate = (String) map.getOrDefault("expectedDate", date);
        String type = (String) map.getOrDefault("type", "cb");
        String refNumber = (String) map.getOrDefault("refNumber", "");
        String label = (String) map.getOrDefault("label", "");
        BigDecimal amount = toBigDecimal(map.get("amount"));
        String categoryId = (String) map.getOrDefault("categoryId", "");
        String status = (String) map.getOrDefault("status", "pending");
        String linkedTxId = (String) map.get("linkedTxId");
        String clearedDate = (String) map.get("clearedDate");
        String notes = (String) map.getOrDefault("notes", "");
        return new BankImportModel.PendingOperationModel(id, date, expectedDate, type, refNumber, label, amount, categoryId, status, linkedTxId, clearedDate, notes);
    }

    public Map<String, Object> toImportSummaryMap(BankImportSummaryModel summary) {
        if (summary == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("imported", summary.imported());
        map.put("duplicates", summary.duplicates());
        map.put("autoCategorized", summary.autoCategorized());
        map.put("ignoredDuplicates", summary.ignoredDuplicates() != null ? summary.ignoredDuplicates().stream().map(this::toTransactionMap).collect(Collectors.toList()) : Collections.emptyList());
        return map;
    }

    public Map<String, Object> toPendingImportSummaryMap(PendingImportSummaryModel summary) {
        if (summary == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("imported", summary.imported());
        map.put("duplicates", summary.duplicates());
        map.put("autoCategorized", summary.autoCategorized());
        map.put("ignoredDuplicates", summary.ignoredDuplicates() != null ? summary.ignoredDuplicates().stream().map(this::toPendingOperationMap).collect(Collectors.toList()) : Collections.emptyList());
        map.put("firstOpDate", summary.firstOpDate());
        map.put("duplicateCandidates", summary.duplicateCandidates() != null
                ? summary.duplicateCandidates().stream().map(dc -> Map.of(
                        "incomingOp", toPendingOperationMap(dc.incomingOp()),
                        "matchingManualOps", dc.matchingManualOps() != null
                                ? dc.matchingManualOps().stream().map(this::toPendingOperationMap).collect(Collectors.toList())
                                : Collections.emptyList()
                )).collect(Collectors.toList())
                : Collections.emptyList());
        return map;
    }

    public Map<String, Object> toAutoMatchResultMap(AutoMatchResultModel result) {
        if (result == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("matchCount", result.matchCount());
        map.put("updatedOperations", result.updatedOperations() != null ? result.updatedOperations().stream().map(this::toPendingOperationMap).collect(Collectors.toList()) : Collections.emptyList());
        return map;
    }

    public Map<String, Object> toPendingOperationsResponseMap(BankImportModel bankImport, BudgetDataModel budgetData) {
        if (bankImport == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();

        map.put("pendingOperations", bankImport.pendingOperations() != null
                ? bankImport.pendingOperations().stream().map(this::toPendingOperationMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("transactions", bankImport.transactions() != null
                ? bankImport.transactions().stream().map(this::toTransactionMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("categories", bankImport.categories() != null
                ? bankImport.categories().stream().map(this::toCategoryMap).collect(Collectors.toList())
                : Collections.emptyList());

        map.put("rules", bankImport.rules() != null
                ? bankImport.rules().stream().map(this::toRuleMap).collect(Collectors.toList())
                : Collections.emptyList());

        if (budgetData != null) {
            map.put("charges", budgetData.getEffectiveCharges() != null
                    ? budgetData.getEffectiveCharges().stream().map(this::toChargeMap).collect(Collectors.toList())
                    : Collections.emptyList());

            map.put("incomes", budgetData.getEffectiveIncomes() != null
                    ? budgetData.getEffectiveIncomes().stream().map(this::toIncomeMap).collect(Collectors.toList())
                    : Collections.emptyList());

            map.put("oneoff", budgetData.getEffectiveOneoff() != null
                    ? budgetData.getEffectiveOneoff().stream().map(this::toOneOffMap).collect(Collectors.toList())
                    : Collections.emptyList());

            map.put("settings", budgetData.getEffectiveSettings() != null
                    ? toSettingsMap(budgetData.getEffectiveSettings())
                    : Collections.emptyMap());
        } else {
            map.put("charges", Collections.emptyList());
            map.put("incomes", Collections.emptyList());
            map.put("oneoff", Collections.emptyList());
            map.put("settings", Collections.emptyMap());
        }

        return map;
    }

    public Map<String, Object> toChargeMap(ChargeModel c) {
        if (c == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.id());
        map.put("label", c.label());
        map.put("monthly", c.monthly());
        map.put("start", c.start());
        map.put("end", c.end());
        map.put("growthRate", c.growthRate());
        map.put("categoryId", c.categoryId());
        map.put("notes", c.notes());
        return map;
    }

    public Map<String, Object> toIncomeMap(IncomeModel i) {
        if (i == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", i.id());
        map.put("label", i.label());
        map.put("monthly", i.monthly());
        map.put("start", i.start());
        map.put("end", i.end());
        map.put("growthRate", i.growthRate());
        map.put("categoryId", i.categoryId());
        map.put("notes", i.notes());
        return map;
    }

    public Map<String, Object> toOneOffMap(OneOffExpenseModel o) {
        if (o == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("id", o.id());
        map.put("label", o.label());
        map.put("date", o.date());
        map.put("amount", o.amount());
        map.put("notes", o.notes());
        return map;
    }

    public Map<String, Object> toSettingsMap(SettingsModel s) {
        if (s == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("birthYear", s.birthYear());
        map.put("retireAge", s.retireAge());
        map.put("simulateUntilAge", s.simulateUntilAge());
        map.put("inflationRate", s.inflationRate());
        map.put("pivotDate", s.pivotDate());
        map.put("pivotMode", s.pivotMode());
        map.put("startBalance", s.startBalance());
        map.put("childExitAge", s.childExitAge());
        map.put("taxAbattement", s.taxAbattement());
        map.put("pass2026", s.pass2026());
        map.put("passGrowthRate", s.passGrowthRate());
        return map;
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        try {
            return new BigDecimal(obj.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
