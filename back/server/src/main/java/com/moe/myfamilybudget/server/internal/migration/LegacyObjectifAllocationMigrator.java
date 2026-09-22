package com.moe.myfamilybudget.server.internal.migration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;

/**
 * Classe de transition, volontairement temporaire : convertit à la lecture les objectifs
 * enregistrés avant l'introduction du multi-comptes (un unique couple {@code sourcePlacementId}/
 * {@code allocatedAmount}) en une allocation unique dans la nouvelle liste {@code allocations}.
 *
 * Ne s'applique QUE si le champ cible ({@code allocations}) est vide ET que {@code
 * sourcePlacementId} est renseigné : un objectif déjà porteur d'allocations (nouveau format, ou
 * déjà migré une fois) n'est jamais retouché, quelle que soit la valeur de
 * {@code sourcePlacementId}/{@code allocatedAmount} qui peut y subsister par ailleurs.
 *
 * À SUPPRIMER dans un patch futur : à l'introduction de cette classe, aucun objectif n'existait
 * encore en base au format mono-compte (voir échange de conception associé), il s'agit donc d'un
 * filet de sécurité et non d'une migration de données réelle. Une fois cette garantie confirmée en
 * production, cette classe et son appel dans EntityModelConverter peuvent être retirés.
 */
public final class LegacyObjectifAllocationMigrator {

    private LegacyObjectifAllocationMigrator() {}

    public static ObjectifModel migrate(ObjectifModel model) {
        if (model == null) {
            return null;
        }

        boolean allocationsVides = model.getEffectiveAllocations().isEmpty();
        boolean sourceLegacyRenseignee = model.sourcePlacementId() != null
                && !model.sourcePlacementId().isBlank();
        if (!allocationsVides || !sourceLegacyRenseignee) {
            return model;
        }

        BigDecimal montant = model.allocatedAmount() != null ? model.allocatedAmount() : BigDecimal.ZERO;
        ObjectifAllocationModel allocationDeCompat = new ObjectifAllocationModel(
                UUID.randomUUID().toString(), model.sourcePlacementId(), montant);

        return new ObjectifModel(model.id(), model.label(), model.targetAmount(), model.allocatedAmount(),
                model.targetDate(), model.sourcePlacementId(), model.notes(), List.of(allocationDeCompat));
    }
}
