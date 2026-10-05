package com.moe.myfamilybudget.application.command;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.port.TransactionRunner;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.goals.port.GoalWriter;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * Service de commande du domaine Objectifs (RF-A00, DB-041).
 * Unique point d'ecriture des objectifs : valide la commande puis delegue au port {@link GoalWriter}. Ne
 * depend plus de {@link PatrimoineCommandService} ni de {@code PersistenceManager}.
 *
 * <p>SILO-240 (lot B1) : la regle d'integrite Objectifs -> Patrimoine (une allocation ne depasse pas le solde du
 * compte, deduction faite des allocations des autres objectifs, {@link GoalAllocationRule}) est portee ici, plus
 * dans les mutations generiques. Elle s'execute dans la meme transaction que l'ecriture
 * ({@link TransactionRunner}), apres la prise des verrous des silos Patrimoine et Objectifs
 * ({@link SiloMutationLock}) : les lectures du controle et l'ecriture voient le meme etat.
 *
 * <p>SILO-212 (lot B1) : les objectifs s'ecrivent directement dans leurs tables ({@code JpaGoalStore}), sans
 * passer par le modele global. Une ecriture n'a donc plus d'autre garantie de serialisation et d'atomicite que
 * celles prises ici : la suppression s'execute, comme la sauvegarde, dans une transaction du
 * {@link TransactionRunner} apres la prise du verrou du silo Objectifs.
 *
 * <p>L'identifiant issu de l'URL n'est jamais {@code null} cote REST : un {@code null} est une erreur de
 * programmation, refusee avant toute ecriture ({@link IllegalArgumentException}). Un corps {@code null}
 * reste accepte pour {@link #saveGoalRow} (creation d'un objectif par defaut, contrat historique de l'API).
 */
@Service
public class GoalCommandService {

    /** Silos lus et ecrits par la sauvegarde d'un objectif (l'ordre de prise des verrous est celui de l'enum). */
    private static final Set<MutationSilo> SAVE_SILOS = EnumSet.of(MutationSilo.WEALTH, MutationSilo.GOALS);

    /** Silo ecrit par la suppression d'un objectif. */
    private static final Set<MutationSilo> DELETE_SILOS = EnumSet.of(MutationSilo.GOALS);

    private final GoalWriter goalWriter;
    private final GoalReader goalReader;
    private final PatrimoineReader patrimoineReader;
    private final SiloMutationLock siloMutationLock;
    private final TransactionRunner transactionRunner;

    public GoalCommandService(GoalWriter goalWriter,
                              GoalReader goalReader,
                              PatrimoineReader patrimoineReader,
                              SiloMutationLock siloMutationLock,
                              TransactionRunner transactionRunner) {
        this.goalWriter = goalWriter;
        this.goalReader = goalReader;
        this.patrimoineReader = patrimoineReader;
        this.siloMutationLock = siloMutationLock;
        this.transactionRunner = transactionRunner;
    }

    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(SAVE_SILOS);
            List<ObjectifAllocationModel> requested = GoalAllocationRule.allocationsOf(body);
            if (!requested.isEmpty()) {
                GoalAllocationRule.validate(GoalAllocationRule.goalIdOf(body), requested,
                        patrimoineReader.getPlacements(), goalReader.getGoals());
            }
            return goalWriter.saveGoalRow(body);
        });
    }

    public void deleteGoalRow(String id) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant de l'objectif est obligatoire");
        }
        transactionRunner.inTransaction(() -> {
            siloMutationLock.lockForCurrentTransaction(DELETE_SILOS);
            goalWriter.deleteGoalRow(id);
            return null;
        });
    }
}
