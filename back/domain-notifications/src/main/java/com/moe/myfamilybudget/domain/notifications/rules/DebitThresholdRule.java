package com.moe.myfamilybudget.domain.notifications.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;


import com.moe.myfamilybudget.domain.notifications.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.NotificationRule;

/**
 * Signale toute transaction bancaire de débit (montant négatif) dont la valeur absolue dépasse le
 * seuil configuré, parmi les transactions importées récemment.
 *
 * Fenêtre volontairement large ({@link #RECENT_DAYS_WINDOW} jours) : la déduplication par
 * transaction (clé = {@code debit-threshold:<idTransaction>}, voir
 * {@code NotificationDispatchService}) garantit qu'une même transaction n'est signalée qu'une
 * fois par 24h, quel que soit le nombre de contrôles déclenchés entre-temps.
 */
public class DebitThresholdRule implements NotificationRule<DebitThresholdInput> {

    public static final String KEY = "debit-threshold";

    private static final int RECENT_DAYS_WINDOW = 30;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public List<NotificationMessage> check(DebitThresholdInput input) {
        BigDecimal threshold = input.threshold();
        if (threshold == null || threshold.signum() <= 0) {
            return List.of();
        }
        LocalDate cutoff = LocalDate.now().minusDays(RECENT_DAYS_WINDOW);
        List<NotificationMessage> messages = new ArrayList<>();
        for (DebitThresholdInput.Transaction tx : input.recentTransactions()) {
            BigDecimal amount = tx.amount();
            if (amount == null || amount.signum() >= 0) {
                continue; // pas un débit
            }
            BigDecimal debit = amount.abs();
            if (debit.compareTo(threshold) <= 0) {
                continue;
            }
            LocalDate date = parseDate(tx.date());
            if (date != null && date.isBefore(cutoff)) {
                continue;
            }
            messages.add(new NotificationMessage(KEY, tx.id(), "Débit important",
                    String.format("%s : %s € le %s", blankToDash(tx.label()), debit.toPlainString(),
                            blankToDash(tx.date()))));
        }
        return messages;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            // Format non reconnu (ISO attendu) : la transaction n'est pas filtrée par ancienneté,
            // seulement par le montant.
            return null;
        }
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
