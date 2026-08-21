package com.moe.myfamilybudget.server.internal.mapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.moe.myfamilybudget.server.internal.model.AutoMatchResultModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportSummaryModel;
import com.moe.myfamilybudget.server.internal.model.PendingImportSummaryModel;

/**
 * Mapper responsible for converting between OpenAPI DTOs / Request Maps
 * and internal domain BankImportModel records.
 */
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
        return map;
    }

    public BankImportModel.BankTransactionModel toTransactionModel(Map<String, Object> map) {
        if (map == null) return new BankImportModel.BankTransactionModel(null, "", "", BigDecimal.ZERO);
        String id = (String) map.get("id");
        String date = (String) map.getOrDefault("date", "");
        String label = (String) map.getOrDefault("label", "");
        String type = (String) map.getOrDefault("type", "");
        BigDecimal amount = toBigDecimal(map.get("amount"));
        String categoryId = (String) map.getOrDefault("categoryId", "");
        return new BankImportModel.BankTransactionModel(id, date, label, type, amount, categoryId);
    }

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
        return map;
    }

    public Map<String, Object> toAutoMatchResultMap(AutoMatchResultModel result) {
        if (result == null) return Collections.emptyMap();
        Map<String, Object> map = new HashMap<>();
        map.put("matchCount", result.matchCount());
        map.put("updatedOperations", result.updatedOperations() != null ? result.updatedOperations().stream().map(this::toPendingOperationMap).collect(Collectors.toList()) : Collections.emptyList());
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
