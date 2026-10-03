package com.moe.myfamilybudget.domain.notifications.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.notifications.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.domain.notifications.calculation.DebitThresholdInput.Transaction;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;

/** RF-703 : la règle « débit important » est testée avec {@link DebitThresholdInput} seulement. */
class DebitThresholdRuleTest {

    private final DebitThresholdRule rule = new DebitThresholdRule();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static String daysAgo(int days) {
        return LocalDate.now().minusDays(days).toString();
    }

    @Test
    @DisplayName("Un débit au-dessus du seuil est signalé, avec l'id de la transaction pour la déduplication")
    void testDebitAboveThreshold() {
        DebitThresholdInput input = new DebitThresholdInput(bd("500"),
                List.of(new Transaction("t1", daysAgo(2), "Garage", bd("-900"))));

        List<NotificationMessage> messages = rule.check(input);

        assertThat(messages).hasSize(1);
        NotificationMessage message = messages.get(0);
        assertThat(message.ruleKey()).isEqualTo(DebitThresholdRule.KEY);
        assertThat(message.dedupKey()).isEqualTo("debit-threshold:t1");
        assertThat(message.body()).contains("Garage").contains("900");
    }

    @Test
    @DisplayName("Crédits, débits sous le seuil et débit égal au seuil ne sont pas signalés")
    void testNotSignalled() {
        DebitThresholdInput input = new DebitThresholdInput(bd("500"), List.of(
                new Transaction("t1", daysAgo(1), "Salaire", bd("2000")),
                new Transaction("t2", daysAgo(1), "Café", bd("-3")),
                new Transaction("t3", daysAgo(1), "Pile au seuil", bd("-500")),
                new Transaction("t4", daysAgo(1), "Sans montant", null)));

        assertThat(rule.check(input)).isEmpty();
    }

    @Test
    @DisplayName("Un débit plus ancien que la fenêtre récente est ignoré, une date illisible ne filtre pas")
    void testRecentWindow() {
        DebitThresholdInput input = new DebitThresholdInput(bd("100"), List.of(
                new Transaction("old", daysAgo(90), "Ancien", bd("-800")),
                new Transaction("bad", "hier", "Date illisible", bd("-800"))));

        assertThat(rule.check(input)).extracting(NotificationMessage::entityId).containsExactly("bad");
    }

    @Test
    @DisplayName("Sans seuil paramétré ou avec un seuil nul, la règle ne signale rien")
    void testNoThreshold() {
        List<Transaction> txs = List.of(new Transaction("t1", daysAgo(1), "Garage", bd("-900")));

        assertThat(rule.check(new DebitThresholdInput(null, txs))).isEmpty();
        assertThat(rule.check(new DebitThresholdInput(BigDecimal.ZERO, txs))).isEmpty();
    }

    @Test
    @DisplayName("Un libellé ou une date absents sont affichés par un tiret")
    void testBlankLabelAndDate() {
        DebitThresholdInput input = new DebitThresholdInput(bd("100"),
                List.of(new Transaction("t1", null, "", bd("-800"))));

        assertThat(rule.check(input)).singleElement()
                .extracting(NotificationMessage::body).asString().startsWith("- : 800");
    }
}
