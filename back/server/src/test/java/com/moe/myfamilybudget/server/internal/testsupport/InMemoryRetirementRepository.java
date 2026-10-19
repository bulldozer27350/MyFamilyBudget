package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanEntity;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionPlanRepository;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsEntity;
import com.moe.myfamilybudget.domain.retirement.core.persistence.PensionSettingsRepository;

/**
 * Repositories en memoire pour les tests unitaires du silo Retraite (R-40, lot B). Meme gabarit que
 * {@link InMemoryTaxRepository#createSettingsRepository()} : {@code pension_plan} et {@code pension_settings}
 * n'ont jamais plus d'une ligne.
 */
public final class InMemoryRetirementRepository {

    private InMemoryRetirementRepository() {
    }

    public static PensionPlanRepository createPlanRepository() {
        AtomicReference<PensionPlanEntity> holder = new AtomicReference<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findFirstByOrderByIdAsc":
                    return Optional.ofNullable(holder.get());
                case "deleteAll":
                    holder.set(null);
                    return null;
                case "flush":
                    return null;
                case "count":
                    return holder.get() != null ? 1L : 0L;
                case "save":
                    PensionPlanEntity savedPlan = (PensionPlanEntity) args[0];
                    holder.set(savedPlan);
                    return savedPlan;
                default:
                    break;
            }
            throw new UnsupportedOperationException("PensionPlanRepository en memoire : " + method.getName());
        };
        return (PensionPlanRepository) Proxy.newProxyInstance(PensionPlanRepository.class.getClassLoader(),
                new Class<?>[] {PensionPlanRepository.class}, handler);
    }

    public static PensionSettingsRepository createSettingsRepository() {
        AtomicReference<PensionSettingsEntity> holder = new AtomicReference<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findFirstByOrderByIdAsc":
                    return Optional.ofNullable(holder.get());
                case "deleteAll":
                    holder.set(null);
                    return null;
                case "flush":
                    return null;
                case "count":
                    return holder.get() != null ? 1L : 0L;
                case "save":
                    PensionSettingsEntity savedSettings = (PensionSettingsEntity) args[0];
                    holder.set(savedSettings);
                    return savedSettings;
                default:
                    break;
            }
            throw new UnsupportedOperationException("PensionSettingsRepository en memoire : " + method.getName());
        };
        return (PensionSettingsRepository) Proxy.newProxyInstance(PensionSettingsRepository.class.getClassLoader(),
                new Class<?>[] {PensionSettingsRepository.class}, handler);
    }
}
