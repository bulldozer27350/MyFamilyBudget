package com.moe.myfamilybudget.server.internal.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnableBankingAccountsTest {

    @Test
    @DisplayName("parse() lit plusieurs comptes séparés par des points-virgules")
    void parseMultipleAccounts() {
        List<EnableBankingAccounts.Account> accounts = EnableBankingAccounts.parse(
                "Compte ****1|684554ff-ed8c-45ec-a2a3-3dd8208aec96;Compte ****2|5bf2f797-ab5e-4909-97d4-29acae8e0e70");

        assertEquals(2, accounts.size());
        assertEquals(new EnableBankingAccounts.Account("Compte ****1", "684554ff-ed8c-45ec-a2a3-3dd8208aec96"),
                accounts.get(0));
        assertEquals(new EnableBankingAccounts.Account("Compte ****2", "5bf2f797-ab5e-4909-97d4-29acae8e0e70"),
                accounts.get(1));
    }

    @Test
    @DisplayName("parse() ignore les espaces superflus et les entrées vides")
    void parseTrimsAndSkipsBlankEntries() {
        List<EnableBankingAccounts.Account> accounts = EnableBankingAccounts.parse("  Compte A | uid-a ;;  ");

        assertEquals(1, accounts.size());
        assertEquals(new EnableBankingAccounts.Account("Compte A", "uid-a"), accounts.get(0));
    }

    @Test
    @DisplayName("parse() renvoie une liste vide pour null ou une chaîne vide")
    void parseEmptyOrNull() {
        assertTrue(EnableBankingAccounts.parse(null).isEmpty());
        assertTrue(EnableBankingAccounts.parse("").isEmpty());
        assertTrue(EnableBankingAccounts.parse("   ").isEmpty());
    }

    @Test
    @DisplayName("parse() lève une exception explicite sur une entrée sans séparateur '|'")
    void parseInvalidEntry() {
        EnableBankingException e = assertThrows(EnableBankingException.class,
                () -> EnableBankingAccounts.parse("Compte sans uid"));
        assertTrue(e.getMessage().contains("Compte sans uid"));
    }
}
