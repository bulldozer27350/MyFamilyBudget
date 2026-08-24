package com.moe.myfamilybudget.server.internal.model;

import java.util.Collections;
import java.util.List;

public record DuplicateCandidateModel(
        BankImportModel.PendingOperationModel incomingOp,
        List<BankImportModel.PendingOperationModel> matchingManualOps
) {
    public DuplicateCandidateModel {
        if (matchingManualOps == null) {
            matchingManualOps = Collections.emptyList();
        }
    }
}