package com.moe.myfamilybudget.server.internal.persistence.adapter;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.server.internal.model.LoanModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.port.LoanReader;
import com.moe.myfamilybudget.server.internal.port.LoanWriter;

/**
 * Adaptateur de persistance pour {@link LoanReader} (RF-B00) et {@link LoanWriter} (DB-041).
 * Les ecritures passent encore par le {@code PersistenceManager} (liste {@code "loans"}) jusqu'a la
 * bascule JPA du domaine.
 */
@Component
public class LoanPersistenceAdapter implements LoanReader, LoanWriter {

    private static final String LIST_KEY = "loans";

    private final PersistenceManager persistenceManager;

    public LoanPersistenceAdapter(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public List<LoanModel> getLoans() {
        return persistenceManager.getBudgetData().getEffectiveLoans();
    }

    @Override
    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        return persistenceManager.writeAndGet(m -> m.savePatrimoineRow(LIST_KEY, body));
    }

    @Override
    public void deleteLoanRow(String id) {
        persistenceManager.write(m -> m.deletePatrimoineRow(LIST_KEY, id));
    }
}
