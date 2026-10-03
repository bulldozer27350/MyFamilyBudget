package com.moe.myfamilybudget.server.internal.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.calculation.BalanceFloorInput;
import com.moe.myfamilybudget.server.internal.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifReachableInput;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.PendingOperationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;
import com.moe.myfamilybudget.server.internal.notification.rules.BalanceFloorRule;
import com.moe.myfamilybudget.server.internal.notification.rules.DebitThresholdRule;
import com.moe.myfamilybudget.server.internal.notification.rules.ObjectifReachableRule;

/**
 * RF-702 : vérifie l'assemblage des trois entrées de notification et leur évaluation par les
 * règles (comportement repris à l'identique de l'ancien {@code NotificationContext}).
 */
class NotificationInputFactoryTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static PlacementModel placement(String id, String balance) {
        return new PlacementModel(id, "Placement " + id, "cat", bd(balance), "2026-01", BigDecimal.ZERO,
                null, null, null, null, null, null, "");
    }

    private static PendingOperationModel pendingOp(String id, String status, String amount) {
        return new PendingOperationModel(id, "2026-09-01", null, null, null, "op", bd(amount), null, status,
                null, null, null, null, null);
    }

    @Test
    @DisplayName("debitThreshold() reprend les transactions importées et le seuil fournis")
    void testDebitThresholdAssembly() {
        String today = LocalDate.now().toString();
        BankImportModel bank = new BankImportModel(
                List.of(new BankTransactionModel("t1", today, "Garage", bd("-900")),
                        new BankTransactionModel("t2", today, "Café", bd("-3")),
                        new BankTransactionModel("t3", today, "Salaire", bd("2000"))),
                List.of(), List.of());

        DebitThresholdInput input = NotificationInputFactory.debitThreshold(bank, bd("500"));

        assertThat(input.threshold()).isEqualByComparingTo("500");
        assertThat(input.recentTransactions()).hasSize(3);
        List<NotificationMessage> messages = new DebitThresholdRule().check(input);
        assertThat(messages).extracting(NotificationMessage::entityId).containsExactly("t1");
    }

    @Test
    @DisplayName("debitThreshold() sans import bancaire : entrée vide, aucune alerte")
    void testDebitThresholdWithoutBankImport() {
        DebitThresholdInput input = NotificationInputFactory.debitThreshold(null, bd("500"));

        assertThat(input.recentTransactions()).isEmpty();
        assertThat(new DebitThresholdRule().check(input)).isEmpty();
    }

    @Test
    @DisplayName("balanceFloor() : solde de départ + transactions importées + opérations 'pending' seulement")
    void testBalanceFloorAssembly() {
        BankImportModel bank = new BankImportModel(
                new BankImportModel.BankColumnMappingModel(null, null, null, null, null, null, null),
                List.of(), List.of(),
                List.of(new BankTransactionModel("t1", "2026-09-01", "Loyer", bd("-300"))),
                List.of(pendingOp("o1", "pending", "-100"), pendingOp("o2", "cleared", "-300")),
                List.of());

        BalanceFloorInput input = NotificationInputFactory.balanceFloor(bank, null, bd("1000"));

        assertThat(input.floor()).isEqualByComparingTo("1000");
        assertThat(input.openingBalance()).isEqualByComparingTo("0");
        assertThat(input.importedTransactions()).hasSize(1);
        assertThat(input.pendingOperations()).hasSize(2);
        // 0 - 300 - 100 = -400 (l'opération 'cleared' est déjà comptée via la transaction importée)
        List<NotificationMessage> messages = new BalanceFloorRule().check(input);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).body()).contains("-400");
    }

    @Test
    @DisplayName("balanceFloor() : le solde de départ fourni est repris (zéro s'il est absent)")
    void testBalanceFloorOpeningBalance() {
        assertThat(NotificationInputFactory.balanceFloor(null, bd("250"), bd("1000")).openingBalance())
                .isEqualByComparingTo("250");
        assertThat(NotificationInputFactory.balanceFloor(null, null, bd("1000")).openingBalance())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("balanceFloor() sans seuil configuré : aucune alerte")
    void testBalanceFloorWithoutFloor() {
        BalanceFloorInput input = NotificationInputFactory.balanceFloor(null, null, null);

        assertThat(new BalanceFloorRule().check(input)).isEmpty();
    }

    @Test
    @DisplayName("objectifReachable() : couverture plafonnée au solde de chaque placement")
    void testObjectifReachableAssembly() {
        ObjectifModel covered = new ObjectifModel("g1", "Vacances", bd("1000"), null, null, null, null,
                List.of(new ObjectifAllocationModel("a1", "p1", bd("600")),
                        new ObjectifAllocationModel("a2", "p2", bd("400"))));
        ObjectifModel notCovered = new ObjectifModel("g2", "Voiture", bd("1000"), null, null, null, null,
                List.of(new ObjectifAllocationModel("a3", "p2", bd("900"))));
        ObjectifReachableInput input = NotificationInputFactory.objectifReachable(
                List.of(covered, notCovered), List.of(placement("p1", "700"), placement("p2", "500")));

        assertThat(input.goals()).hasSize(2);
        assertThat(input.placementBalances()).hasSize(2);
        List<NotificationMessage> messages = new ObjectifReachableRule().check(input);
        // g1 : min(600, 700) + min(400, 500) = 1000 >= 1000 ; g2 : min(900, 500) = 500 < 1000
        assertThat(messages).extracting(NotificationMessage::entityId).containsExactly("g1");
    }

    @Test
    @DisplayName("objectifReachable() sans objectif : entrée vide, aucune alerte")
    void testObjectifReachableWithoutGoals() {
        ObjectifReachableInput input = NotificationInputFactory.objectifReachable(null, null);

        assertThat(input.goals()).isEmpty();
        assertThat(new ObjectifReachableRule().check(input)).isEmpty();
    }
}
