package com.moe.myfamilybudget.server.internal.notification.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.BalanceFloorInput;
import com.moe.myfamilybudget.server.internal.calculation.BalanceFloorInput.AccountTransactionAmount;
import com.moe.myfamilybudget.server.internal.calculation.BalanceFloorInput.PendingAmount;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;

/** RF-703 : la règle « solde sous le seuil » est testée avec {@link BalanceFloorInput} seulement. */
class BalanceFloorRuleTest {

    private final BalanceFloorRule rule = new BalanceFloorRule();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Un solde sous le seuil produit un message unique, clé de déduplication globale")
    void testBelowFloor() {
        BalanceFloorInput input = new BalanceFloorInput(bd("1000"), bd("1200"),
                List.of(new AccountTransactionAmount(bd("-300"))), List.of());

        List<NotificationMessage> messages = rule.check(input);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).dedupKey()).isEqualTo("balance-floor");
        assertThat(messages.get(0).body()).contains("900").contains("1000");
    }

    @Test
    @DisplayName("Un solde égal ou supérieur au seuil ne produit aucun message")
    void testAtOrAboveFloor() {
        BalanceFloorInput atFloor = new BalanceFloorInput(bd("1000"), bd("1000"), List.of(), List.of());
        BalanceFloorInput above = new BalanceFloorInput(bd("1000"), bd("1500"), List.of(), List.of());

        assertThat(rule.check(atFloor)).isEmpty();
        assertThat(rule.check(above)).isEmpty();
    }

    @Test
    @DisplayName("Seules les opérations en attente comptent : une opération 'cleared' est déjà dans les transactions")
    void testOnlyPendingOperationsCount() {
        BalanceFloorInput input = new BalanceFloorInput(bd("1000"), bd("1200"),
                List.of(new AccountTransactionAmount(bd("-100"))),
                List.of(new PendingAmount("pending", bd("-150")),
                        new PendingAmount("PENDING", bd("-50")),
                        new PendingAmount("cleared", bd("-100"))));

        // 1200 - 100 - 150 - 50 = 900 (< 1000) ; avec l'opération 'cleared' on serait à 800
        assertThat(rule.check(input)).singleElement()
                .extracting(NotificationMessage::body).asString().contains("900");
    }

    @Test
    @DisplayName("Les montants absents sont ignorés")
    void testNullAmountsIgnored() {
        BalanceFloorInput input = new BalanceFloorInput(bd("1000"), bd("1500"),
                List.of(new AccountTransactionAmount(null)), List.of(new PendingAmount("pending", null)));

        assertThat(rule.check(input)).isEmpty();
    }

    @Test
    @DisplayName("Sans seuil paramétré, la règle ne signale rien")
    void testNoFloor() {
        BalanceFloorInput input = new BalanceFloorInput(null, bd("-5000"), List.of(), List.of());

        assertThat(rule.check(input)).isEmpty();
    }
}
