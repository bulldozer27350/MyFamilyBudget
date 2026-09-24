package com.moe.myfamilybudget.server.internal.enablebanking;

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
