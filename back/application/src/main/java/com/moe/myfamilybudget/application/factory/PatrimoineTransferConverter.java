package com.moe.myfamilybudget.application.factory;

import java.util.List;

import com.moe.myfamilybudget.domain.treasury.model.TransferModel;
import com.moe.myfamilybudget.domain.wealth.model.PatrimoineTransferModel;

/**
 * SILO-131 : traduction des virements entre le type propre au silo Patrimoine
 * ({@link PatrimoineTransferModel}) et le type de Trésorerie ({@link TransferModel}, SILO-140) consommé
 * par la façade et les autres services d'application. Une liste {@code null} reste {@code null}.
 */
public final class PatrimoineTransferConverter {

    private PatrimoineTransferConverter() {}

    public static List<TransferModel> toBudget(List<PatrimoineTransferModel> transfers) {
        if (transfers == null) {
            return null;
        }
        return transfers.stream()
                .map(t -> t == null ? null
                        : new TransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()))
                .toList();
    }

    public static List<PatrimoineTransferModel> toWealth(List<TransferModel> transfers) {
        if (transfers == null) {
            return null;
        }
        return transfers.stream()
                .map(t -> t == null ? null
                        : new PatrimoineTransferModel(t.id(), t.placement(), t.date(), t.amount(), t.notes()))
                .toList();
    }
}
