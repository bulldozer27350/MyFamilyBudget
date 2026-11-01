package com.moe.myfamilybudget.domain.goals.core.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.goals.model.GoalsMutatedEvent;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalSnapshotWriter;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;

/**
 * Adaptateur JPA du silo Objectifs (SILO-212, lot B1) : implemente {@link GoalReader}, {@link GoalWriter} et
 * {@link GoalSnapshotWriter} directement sur les tables {@code goal} et {@code goal_allocation}, sans passer
 * par le cache global ni par le {@code PersistenceManager}.
 *
 * <p>Les ecritures ne s'appellent que dans une transaction deja ouverte ({@code TransactionRunner}) et apres la
 * prise du verrou du silo ({@code MutationSilo.GOALS}) par l'appelant : l'adaptateur ne porte ni
 * {@code @Transactional} ni verrou. Chaque ecriture remplace le contenu des tables par la liste resultante
 * (suppression, {@code flush}, insertion), comme l'ancienne synchronisation du modele global : le
 * {@code flush} est indispensable, Hibernate executant les insertions avant les suppressions, ce qui violerait
 * la cle primaire metier lors du remplacement d'un objectif existant. Les positions sont ainsi toujours
 * contigues.
 *
 * <p>Parite avec l'ancienne mutation generique ({@code savePatrimoineRow("objectifs", ...)}) : memes valeurs par
 * defaut, meme identifiant genere, meme ligne renvoyee. Le controle d'integrite Objectifs -> Patrimoine reste
 * porte par {@code GoalCommandService} (SILO-240).
 *
 * <p>Apres chaque ecriture, un {@link GoalsMutatedEvent} est publie : l'ecoute apres commit declenche le
 * controle automatique des notifications, comme le faisait {@code BudgetMutatedEvent}.
 */
@Component
public class JpaGoalStore implements GoalReader, GoalWriter, GoalSnapshotWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JpaGoalStore.class);

    private final GoalRepository goalRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaGoalStore(GoalRepository goalRepository, ApplicationEventPublisher eventPublisher) {
        this.goalRepository = goalRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<ObjectifModel> getGoals() {
        return GoalEntityMapper.toModels(goalRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        String givenId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String uid = (givenId != null && !givenId.trim().isEmpty())
                ? givenId
                : ("pat_" + UUID.randomUUID().toString().substring(0, 8));

        String label = getString(body, "label", "Nouvel objectif");
        BigDecimal targetAmount = getBigDecimal(body, "targetAmount", BigDecimal.ZERO);
        String targetDate = getString(body, "targetDate", "2027-01-01");
        String notes = getString(body, "notes", "");
        List<ObjectifAllocationModel> allocations = getAllocations(body);

        ObjectifModel model = new ObjectifModel(uid, label, targetAmount, null, targetDate, "", notes, allocations);

        List<ObjectifModel> list = new ArrayList<>();
        boolean found = false;
        for (ObjectifModel existing : getGoals()) {
            if (Objects.equals(existing.id(), uid)) {
                list.add(model);
                found = true;
            } else {
                list.add(existing);
            }
        }
        if (!found) {
            list.add(model);
        }
        rewrite(list);
        publishMutated("saveGoalRow");

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);
        resultRow.put("label", label);
        resultRow.put("targetAmount", targetAmount);
        resultRow.put("targetDate", targetDate);
        resultRow.put("notes", notes);
        resultRow.put("allocations", allocations);
        return resultRow;
    }

    @Override
    public void deleteGoalRow(String id) {
        List<ObjectifModel> remaining = getGoals().stream()
                .filter(goal -> !Objects.equals(goal.id(), id))
                .toList();
        rewrite(remaining);
        publishMutated("deleteGoalRow");
    }

    /** SILO-119 (lot B1) : import des objectifs ; une liste {@code null} est lue comme vide. */
    @Override
    public void replace(List<ObjectifModel> goals) {
        rewrite(goals == null ? List.of() : goals);
        publishMutated("replace");
    }

    /** SILO-119 (lot B1) : suppression de tous les objectifs. */
    @Override
    public void reset() {
        rewrite(List.of());
        publishMutated("reset");
    }

    /**
     * Remplace le contenu des tables par {@code goals}. Un objectif sans identifiant ne peut etre adresse par
     * aucune API : il n'est pas copie dans les tables (avertissement journalise).
     */
    private void rewrite(List<ObjectifModel> goals) {
        goalRepository.deleteAll();
        goalRepository.flush();
        if (goals.isEmpty()) {
            return;
        }
        List<ObjectifModel> identified = goals.stream()
                .filter(goal -> goal != null && goal.id() != null)
                .toList();
        if (identified.size() != goals.size()) {
            LOG.warn("{} objectif(s) sans identifiant ignore(s) lors de l'ecriture des tables Objectifs",
                    goals.size() - identified.size());
        }
        goalRepository.saveAll(GoalEntityMapper.toEntities(identified));
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new GoalsMutatedEvent(mutationKind));
    }

    /**
     * Lit le champ {@code allocations} du corps d'un objectif (tableau JSON {@code [{id, placementId, amount}]}).
     * Un identifiant d'allocation absent ou vide (nouvelle ligne saisie cote tiroir) est genere ici.
     */
    private List<ObjectifAllocationModel> getAllocations(Map<String, Object> body) {
        Object raw = body != null ? body.get("allocations") : null;
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<ObjectifAllocationModel> allocations = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> entry)) {
                continue;
            }
            Object rawId = entry.get("id");
            String allocationId = rawId != null ? String.valueOf(rawId) : null;
            if (allocationId == null || allocationId.isBlank()) {
                allocationId = UUID.randomUUID().toString();
            }
            Object rawPlacement = entry.get("placementId");
            String placementId = rawPlacement != null ? String.valueOf(rawPlacement) : "";
            Object rawAmount = entry.get("amount");
            BigDecimal amount = rawAmount != null ? toBigDecimal(rawAmount, BigDecimal.ZERO) : BigDecimal.ZERO;
            allocations.add(new ObjectifAllocationModel(allocationId, placementId, amount));
        }
        return allocations;
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toBigDecimal(map.get(key), defaultValue);
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return fallback;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            LOG.warn("Valeur numerique decimale illisible, valeur par defaut '{}' utilisee : '{}'", fallback, value, e);
            return fallback;
        }
    }
}
