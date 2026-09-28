package com.moe.myfamilybudget.server.internal.command;

import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Service de commande du domaine Prets/Emprunts (RF-A00).
 * Delegue vers {@link PatrimoineCommandService} en fixant le listKey {@code "loans"}.
 */
@Service
public class LoanCommandService {

    private static final String LIST_KEY = "loans";

    private final PatrimoineCommandService patrimoineCommandService;

    public LoanCommandService(PatrimoineCommandService patrimoineCommandService) {
        this.patrimoineCommandService = patrimoineCommandService;
    }

    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return patrimoineCommandService.savePatrimoineRow(LIST_KEY, body);
    }

    public void deleteLoanRow(String id) {
        patrimoineCommandService.deletePatrimoineRow(LIST_KEY, id);
    }
}
