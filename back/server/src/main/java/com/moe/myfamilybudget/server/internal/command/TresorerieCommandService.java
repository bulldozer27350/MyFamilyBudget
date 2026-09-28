package com.moe.myfamilybudget.server.internal.command;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

/**
 * Service de commande du domaine Tresorerie (RF-A00).
 * Encapsule les operations d'ecriture sur les lignes de tresorerie
 * (revenus, charges, depenses ponctuelles, revenus variables et ajustements).
 */
@Service
public class TresorerieCommandService {

    private final PersistenceManager persistenceManager;

    public TresorerieCommandService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public Map<String, Object> addTresorerieRow(String listKey, Map<String, Object> body) {
        return persistenceManager.addTresorerieRow(listKey, body);
    }

    public void updateTresorerieRow(String listKey, String id, String field, Object value) {
        persistenceManager.updateTresorerieRow(listKey, id, field, value);
    }

    public void removeTresorerieRow(String listKey, String id) {
        persistenceManager.removeTresorerieRow(listKey, id);
    }

    public void applyTresorerieAjustement(String lineId, String kind, BigDecimal newMonthly) {
        persistenceManager.applyTresorerieAjustement(lineId, kind, newMonthly);
    }
}
