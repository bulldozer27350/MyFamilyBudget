package com.moe.myfamilybudget.domain.notifications.port;

import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import java.util.List;

/**
 * Port de persistance des abonnements Web Push. Implémenté hors du silo (adaptateur JPA du composition root,
 * déplacé dans la persistance du silo Notifications par SILO-217).
 */
public interface PushSubscriptionStore {

    List<PushSubscriptionModel> findAll();

    boolean existsByEndpoint(String endpoint);

    void save(PushSubscriptionModel subscription);

    void deleteById(String id);

    void deleteByEndpoint(String endpoint);
}
