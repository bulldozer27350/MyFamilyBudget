package com.moe.myfamilybudget.server.internal.factory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.moe.myfamilybudget.server.internal.calculation.BalanceFloorInput;
import com.moe.myfamilybudget.server.internal.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.server.internal.calculation.ObjectifReachableInput;
import com.moe.myfamilybudget.server.internal.calculation.PlacementBalanceSnapshot;
import com.moe.myfamilybudget.server.internal.model.BankImportModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.BankImportModel.PendingOperationModel;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.server.internal.model.PlacementModel;

/**
 * Construit les trois entrées de notification (RF-702, voir
 * doc/architecture/09-domaine-objectifs-notifications.md) : {@link DebitThresholdInput},
 * {@link BalanceFloorInput} et {@link ObjectifReachableInput}.
 *
 * <p>Une méthode par règle, volontairement : aucune entrée d'évaluation commune n'est produite.
 * C'est ici, et non plus dans les règles, que {@code BudgetDataModel} est traduit en records
 * minimaux. Les seuils sont fournis par l'appelant (paramètres de notification), la factory ne
 * dépend donc pas du package {@code notification}.
 */
public final class NotificationInputFactory {

    private NotificationInputFactory() {
    }

    /** @param threshold seuil de débit configuré ({@code null} si non paramétré) */
    public static DebitThresholdInput debitThreshold(BudgetDataModel data, BigDecimal threshold) {
        Objects.requireNonNull(data, "data");
        List<DebitThresholdInput.Transaction> transactions = new ArrayList<>();
        BankImportModel bankImport = data.bankImport();
        if (bankImport != null && bankImport.transactions() != null) {
            for (BankTransactionModel tx : bankImport.transactions()) {
                transactions.add(new DebitThresholdInput.Transaction(tx.id(), tx.date(), tx.label(), tx.amount()));
            }
        }
        return new DebitThresholdInput(threshold, transactions);
    }

    /** @param floor seuil plancher configuré ({@code null} si non paramétré) */
    public static BalanceFloorInput balanceFloor(BudgetDataModel data, BigDecimal floor) {
        Objects.requireNonNull(data, "data");
        List<BalanceFloorInput.AccountTransactionAmount> imported = new ArrayList<>();
        List<BalanceFloorInput.PendingAmount> pending = new ArrayList<>();
        BankImportModel bankImport = data.bankImport();
        if (bankImport != null) {
            if (bankImport.transactions() != null) {
                for (BankTransactionModel tx : bankImport.transactions()) {
                    imported.add(new BalanceFloorInput.AccountTransactionAmount(tx.amount()));
                }
            }
            if (bankImport.pendingOperations() != null) {
                for (PendingOperationModel op : bankImport.pendingOperations()) {
                    pending.add(new BalanceFloorInput.PendingAmount(op.status(), op.amount()));
                }
            }
        }
        return new BalanceFloorInput(floor, data.getEffectiveSettings().getEffectiveStartBalance(), imported, pending);
    }

    public static ObjectifReachableInput objectifReachable(BudgetDataModel data) {
        Objects.requireNonNull(data, "data");
        List<ObjectifReachableInput.GoalCoverage> goals = new ArrayList<>();
        for (ObjectifModel objectif : data.getEffectiveObjectifs()) {
            List<ObjectifReachableInput.Allocation> allocations = new ArrayList<>();
            for (ObjectifAllocationModel allocation : objectif.getEffectiveAllocations()) {
                allocations.add(new ObjectifReachableInput.Allocation(
                        allocation.placementId(), allocation.getEffectiveAmount()));
            }
            goals.add(new ObjectifReachableInput.GoalCoverage(
                    objectif.id(), objectif.label(), objectif.getEffectiveTargetAmount(), allocations));
        }
        List<PlacementBalanceSnapshot> balances = new ArrayList<>();
        for (PlacementModel placement : data.getEffectivePlacements()) {
            balances.add(new PlacementBalanceSnapshot(placement.id(), placement.balance()));
        }
        return new ObjectifReachableInput(goals, balances);
    }
}
