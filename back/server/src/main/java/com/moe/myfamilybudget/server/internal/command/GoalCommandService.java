package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Service de commande du domaine Objectifs (RF-A00).
 * Delegue vers {@link PatrimoineCommandService} en fixant le listKey {@code "objectifs"}.
 */
@Service
public class GoalCommandService {

    private static final String LIST_KEY = "objectifs";

    private final PatrimoineCommandService patrimoineCommandService;

    public GoalCommandService(PatrimoineCommandService patrimoineCommandService) {
        this.patrimoineCommandService = patrimoineCommandService;
    }

    public Map<String, Object> saveGoalRow(Map<String, Object> body) {
        return patrimoineCommandService.savePatrimoineRow(LIST_KEY, body);
    }

    public void deleteGoalRow(String id) {
        patrimoineCommandService.deletePatrimoineRow(LIST_KEY, id);
    }
}
