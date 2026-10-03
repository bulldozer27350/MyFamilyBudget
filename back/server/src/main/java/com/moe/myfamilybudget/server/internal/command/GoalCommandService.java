package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.domain.goals.port.GoalWriter;

/**
 * Service de commande du domaine Objectifs (RF-A00, DB-041).
 * Unique point d'ecriture des objectifs : valide la commande puis delegue au port {@link GoalWriter}. Ne
 * depend plus de {@link PatrimoineCommandService} ni de {@code PersistenceManager}.
 *
 * <p>L'identifiant issu de l'URL n'est jamais {@code null} cote REST : un {@code null} est une erreur de
 * programmation, refusee avant toute ecriture ({@link IllegalArgumentException}). Un corps {@code null}
 * reste accepte pour {@link #saveGoalRow} (creation d'un objectif par defaut, contrat historique de l'API).
 */
@Service
public class GoalCommandService {

    private final GoalWriter goalWriter;

    public GoalCommandService(GoalWriter goalWriter) {
        this.goalWriter = goalWriter;
    }

    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return goalWriter.saveGoalRow(body);
    }

    public void deleteGoalRow(String id) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant de l'objectif est obligatoire");
        }
        goalWriter.deleteGoalRow(id);
    }
}
