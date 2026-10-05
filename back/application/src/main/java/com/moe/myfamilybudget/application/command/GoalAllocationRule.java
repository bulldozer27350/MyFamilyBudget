package com.moe.myfamilybudget.application.command;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;

/**
 * Règle d'intégrité inter-silos Objectifs → Patrimoine (SILO-240, lot B1) : la somme des allocations d'un
 * compte, tous objectifs confondus, ne dépasse jamais son solde.
 *
 * <p>Règle portée telle quelle depuis {@code BudgetMutationService.validateObjectifAllocations}, sans changement
 * de comportement : même contrôle, même message, même {@link IllegalArgumentException} (HTTP 400). Elle ne lit
 * plus le modèle global : l'appelant ({@link GoalCommandService}) fournit les placements (lecteur Patrimoine) et
 * les objectifs (lecteur Objectifs), lus dans la même transaction que l'écriture.
 *
 * <p>Classe pure, sans état ni dépendance vers un autre module que les API des deux silos.
 */
public final class GoalAllocationRule {

    private static final Logger LOG = LoggerFactory.getLogger(GoalAllocationRule.class);

    private GoalAllocationRule() {}

    /**
     * Identifiant de l'objectif visé par le corps de requête, ou {@code null} s'il est absent ou vide (nouvel
     * objectif : aucun objectif existant ne lui correspond).
     */
    public static String goalIdOf(Map<String, Object> body) {
        if (body == null || body.get("id") == null) {
            return null;
        }
        String id = String.valueOf(body.get("id"));
        return id.trim().isEmpty() ? null : id;
    }

    /**
     * Lit le champ {@code allocations} du corps de requête d'un objectif (tableau JSON
     * {@code [{id, placementId, amount}, ...]}). Mêmes conversions que l'écriture : compte absent → chaîne vide,
     * montant absent ou illisible → zéro. L'identifiant d'allocation n'intervient pas dans le contrôle et reste
     * tel que fourni (éventuellement {@code null}).
     */
    @SuppressWarnings("unchecked")
    public static List<ObjectifAllocationModel> allocationsOf(Map<String, Object> body) {
        Object raw = body != null ? body.get("allocations") : null;
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }

        List<ObjectifAllocationModel> allocations = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> entry = (Map<String, Object>) item;
            Object id = entry.get("id");
            Object placementId = entry.get("placementId");
            allocations.add(new ObjectifAllocationModel(
                    id != null ? String.valueOf(id) : null,
                    placementId != null ? String.valueOf(placementId) : "",
                    toBigDecimal(entry.get("amount"))));
        }
        return allocations;
    }

    /**
     * Bloque la sauvegarde si les allocations demandées dépassent le solde d'un compte, une fois déduites les
     * allocations déjà réservées par les AUTRES objectifs (celles de l'objectif en cours de sauvegarde sont
     * intégralement remplacées par {@code requested}, elles ne comptent donc pas dans le « déjà alloué
     * ailleurs »).
     *
     * @param goalId    identifiant de l'objectif sauvegardé ({@code null} pour un nouvel objectif)
     * @param requested allocations demandées pour cet objectif
     * @param placements placements courants du silo Patrimoine
     * @param goals     objectifs courants du silo Objectifs
     * @throws IllegalArgumentException si un compte n'a pas un solde suffisant
     */
    public static void validate(String goalId, List<ObjectifAllocationModel> requested,
                                List<PlacementModel> placements, List<ObjectifModel> goals) {
        if (requested == null || requested.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> soldeParCompte = new HashMap<>();
        Map<String, String> libelleParCompte = new HashMap<>();
        for (PlacementModel p : placements) {
            soldeParCompte.put(p.id(), p.getEffectiveBalance());
            libelleParCompte.put(p.id(), p.label());
        }

        Map<String, BigDecimal> dejaAlloueAilleurs = new HashMap<>();
        for (ObjectifModel o : goals) {
            if (Objects.equals(o.id(), goalId)) {
                continue;
            }
            for (ObjectifAllocationModel a : allocationsSeenFor(o)) {
                dejaAlloueAilleurs.merge(a.placementId(), a.getEffectiveAmount(), BigDecimal::add);
            }
        }

        Map<String, BigDecimal> demandeParCompte = new HashMap<>();
        for (ObjectifAllocationModel a : requested) {
            demandeParCompte.merge(a.placementId(), a.getEffectiveAmount(), BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> entry : demandeParCompte.entrySet()) {
            String placementId = entry.getKey();
            BigDecimal demande = entry.getValue();
            BigDecimal solde = soldeParCompte.getOrDefault(placementId, BigDecimal.ZERO);
            BigDecimal dejaAlloue = dejaAlloueAilleurs.getOrDefault(placementId, BigDecimal.ZERO);
            BigDecimal disponible = solde.subtract(dejaAlloue);
            if (demande.compareTo(disponible) > 0) {
                String libelle = libelleParCompte.getOrDefault(placementId, placementId);
                throw new IllegalArgumentException("Le compte '" + libelle
                        + "' n'a pas un solde suffisant pour cette allocation : disponible "
                        + disponible + " €, montant demandé " + demande + " €.");
            }
        }
    }

    /**
     * Allocations d'un autre objectif telles que le modèle global les voyait : un objectif enregistré avant le
     * multi-comptes (aucune allocation, {@code sourcePlacementId} renseigné) compte pour une allocation unique
     * ({@code sourcePlacementId}, {@code allocatedAmount}). Transitoire, comme {@code
     * LegacyObjectifAllocationMigrator} dont c'est l'équivalent en lecture : à retirer avec lui.
     */
    private static List<ObjectifAllocationModel> allocationsSeenFor(ObjectifModel goal) {
        List<ObjectifAllocationModel> allocations = goal.getEffectiveAllocations();
        boolean legacySource = goal.sourcePlacementId() != null && !goal.sourcePlacementId().isBlank();
        if (!allocations.isEmpty() || !legacySource) {
            return allocations;
        }
        BigDecimal amount = goal.allocatedAmount() != null ? goal.allocatedAmount() : BigDecimal.ZERO;
        return List.of(new ObjectifAllocationModel(null, goal.sourcePlacementId(), amount));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            String s = String.valueOf(value).trim().replace(",", ".");
            if (s.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(s);
        } catch (Exception e) {
            LOG.warn("Valeur numérique décimale illisible, valeur par défaut '{}' utilisée : '{}'",
                    BigDecimal.ZERO, value, e);
            return BigDecimal.ZERO;
        }
    }
}
