package com.moe.myfamilybudget.server.internal.enablebanking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lit la liste des comptes Enable Banking depuis une propriété texte simple, pour éviter une
 * liste YAML indexée peu pratique à passer par variable d'environnement dans un fichier
 * {@code .env} Docker.
 *
 * Format attendu (propriété {@code myfamilybudget.enable-banking.accounts}, variable
 * d'environnement {@code MYFAMILYBUDGET_ENABLE_BANKING_ACCOUNTS}) :
 * <pre>{@code libellé 1|uid-1;libellé 2|uid-2;...}</pre>
 * par exemple : {@code Compte ****1|684554ff-ed8c-45ec-a2a3-3dd8208aec96;Compte ****2|5bf2f797-...}
 */
final class EnableBankingAccounts {

    record Account(String label, String uid) {
    }

    private EnableBankingAccounts() {
    }

    static List<Account> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<Account> accounts = new ArrayList<>();
        for (String entry : raw.split(";")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separatorIndex = trimmed.indexOf('|');
            if (separatorIndex <= 0 || separatorIndex == trimmed.length() - 1) {
                throw new EnableBankingException(
                        "Entrée de compte Enable Banking invalide (attendu \"libellé|uid\") : " + trimmed);
            }
            String label = trimmed.substring(0, separatorIndex).trim();
            String uid = trimmed.substring(separatorIndex + 1).trim();
            accounts.add(new Account(label, uid));
        }
        return accounts;
    }
}
