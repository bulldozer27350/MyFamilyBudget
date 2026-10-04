package com.moe.myfamilybudget.application.factory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.moe.myfamilybudget.domain.notifications.calculation.BalanceFloorInput;
import com.moe.myfamilybudget.domain.notifications.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.domain.notifications.calculation.ObjectifReachableInput;
import com.moe.myfamilybudget.domain.notifications.calculation.PlacementBalanceSnapshot;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.PendingOperationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Construit les trois entrées de notification (RF-702, voir
 * doc/architecture/09-domaine-objectifs-notifications.md) : {@link DebitThresholdInput},
 * {@link BalanceFloorInput} et {@link ObjectifReachableInput}.
 *
 * <p>Une méthode par règle, volontairement : aucune entrée d'évaluation commune n'est produite.
 * C'est ici, et non plus dans les règles, que les modèles du budget sont traduits en records
 * minimaux. Depuis NOTIF-010, chaque méthode reçoit uniquement les fragments dont la règle a
 * besoin (import bancaire, solde de départ, objectifs, placements), lus par l'appelant via les
 * ports de lecture : la factory ne connaît plus {@code BudgetDataModel}. Les seuils sont fournis
 * par l'appelant (paramètres de notification), la factory ne dépend donc pas du package
 * {@code notification}.
 */
public final class NotificationInputFactory {

    private NotificationInputFactory() {
    }

    /**
     * @param bankImport import bancaire courant ({@code null} accepté : aucune transaction)
     * @param threshold seuil de débit configuré ({@code null} si non paramétré)
     */
    public static DebitThresholdInput debitThreshold(BankImportModel bankImport, BigDecimal threshold) {
        List<DebitThresholdInput.Transaction> transactions = new ArrayList<>();
        if (bankImport != null && bankImport.transactions() != null) {
            for (BankTransactionModel tx : bankImport.transactions()) {
                transactions.add(new DebitThresholdInput.Transaction(tx.id(), tx.date(), tx.label(), tx.amount()));
            }
        }
        return new DebitThresholdInput(threshold, transactions);
    }

    /**
     * @param bankImport     import bancaire courant ({@code null} accepté : aucune transaction)
     * @param openingBalance solde de départ des paramètres ({@code null} traité comme zéro)
     * @param floor          seuil plancher configuré ({@code null} si non paramétré)
     */
    public static BalanceFloorInput balanceFloor(BankImportModel bankImport, BigDecimal openingBalance,
            BigDecimal floor) {
        List<BalanceFloorInput.AccountTransactionAmount> imported = new ArrayList<>();
        List<BalanceFloorInput.PendingAmount> pending = new ArrayList<>();
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
        return new BalanceFloorInput(floor, openingBalance != null ? openingBalance : BigDecimal.ZERO,
                imported, pending);
    }

    /**
     * @param objectifs  objectifs courants ({@code null} accepté : aucun objectif)
     * @param placements placements courants, pour leur solde ({@code null} accepté : aucun placement)
     */
    public static ObjectifReachableInput objectifReachable(List<ObjectifModel> objectifs,
            List<PlacementModel> placements) {
        List<ObjectifReachableInput.GoalCoverage> goals = new ArrayList<>();
        for (ObjectifModel objectif : objectifs != null ? objectifs : List.<ObjectifModel>of()) {
            List<ObjectifReachableInput.Allocation> allocations = new ArrayList<>();
            for (ObjectifAllocationModel allocation : objectif.getEffectiveAllocations()) {
                allocations.add(new ObjectifReachableInput.Allocation(
                        allocation.placementId(), allocation.getEffectiveAmount()));
            }
            goals.add(new ObjectifReachableInput.GoalCoverage(
                    objectif.id(), objectif.label(), objectif.getEffectiveTargetAmount(), allocations));
        }
        List<PlacementBalanceSnapshot> balances = new ArrayList<>();
        for (PlacementModel placement : placements != null ? placements : List.<PlacementModel>of()) {
            balances.add(new PlacementBalanceSnapshot(placement.id(), placement.balance()));
        }
        return new ObjectifReachableInput(goals, balances);
    }
}
