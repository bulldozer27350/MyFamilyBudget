package com.moe.myfamilybudget.domain.bankpointage.model;

import java.util.List;

public record EnableBankingSyncResult(List<AccountResult> accounts) {

    public record AccountResult(
            String label,
            int imported,
            int duplicates,
            int autoCategorized,
            /** {@code null} si la synchronisation de ce compte a réussi. */
            String error
    ) {
    }
}
