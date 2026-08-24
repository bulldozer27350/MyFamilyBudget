package com.moe.myfamilybudget.server.internal.model;

import java.util.Collections;
import java.util.List;

public record PendingImportSummaryModel(
        int imported,
        int duplicates,
        int autoCategorized,
        List<BankImportModel.PendingOperationModel> ignoredDuplicates,
        String firstOpDate,
        List<BankImportModel.PendingOperationModel> newOperations,
        List<DuplicateCandidateModel> duplicateCandidates
) {
    public PendingImportSummaryModel(
            int imported,
            int duplicates,
            int autoCategorized,
            List<BankImportModel.PendingOperationModel> ignoredDuplicates,
            String firstOpDate,
            List<BankImportModel.PendingOperationModel> newOperations
    ) {
        this(imported, duplicates, autoCategorized, ignoredDuplicates, firstOpDate, newOperations, Collections.emptyList());
    }

    public PendingImportSummaryModel {
        if (ignoredDuplicates == null) ignoredDuplicates = Collections.emptyList();
        if (newOperations == null) newOperations = Collections.emptyList();
        if (duplicateCandidates == null) duplicateCandidates = Collections.emptyList();
    }
}