package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideEntity;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketEntity;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalBracketRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildEntity;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalChildRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideEntity;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsEntity;
import com.moe.myfamilybudget.domain.tax.core.persistence.FiscalSettingsRepository;

/**
 * Repositories en memoire pour les tests unitaires du silo Fiscalite (R-42, lot B).
 */
public final class InMemoryTaxRepository {

    private InMemoryTaxRepository() {
    }

    public static FiscalChildRepository createChildRepository() {
        return createPositionRepository(FiscalChildRepository.class, FiscalChildEntity.class, FiscalChildEntity::getPosition);
    }

    public static FiscalBracketRepository createBracketRepository() {
        return createPositionRepository(FiscalBracketRepository.class, FiscalBracketEntity.class, FiscalBracketEntity::getPosition);
    }

    public static FiscalRateOverrideRepository createRateOverrideRepository() {
        return createPositionRepository(FiscalRateOverrideRepository.class, FiscalRateOverrideEntity.class, FiscalRateOverrideEntity::getPosition);
    }

    public static FiscalActualOverrideRepository createActualOverrideRepository() {
        return createPositionRepository(FiscalActualOverrideRepository.class, FiscalActualOverrideEntity.class, FiscalActualOverrideEntity::getPosition);
    }

    public static FiscalSettingsRepository createSettingsRepository() {
        AtomicReference<FiscalSettingsEntity> holder = new AtomicReference<>();
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
                    FiscalSettingsEntity saved = (FiscalSettingsEntity) args[0];
                    holder.set(saved);
                    return saved;
                default:
                    break;
            }
            throw new UnsupportedOperationException("FiscalSettingsRepository en memoire : " + method.getName());
        };
        return (FiscalSettingsRepository) Proxy.newProxyInstance(FiscalSettingsRepository.class.getClassLoader(),
                new Class<?>[] {FiscalSettingsRepository.class}, handler);
    }

    @SuppressWarnings("unchecked")
    private static <R, E> R createPositionRepository(Class<R> repoClass, Class<E> entityClass, java.util.function.ToIntFunction<E> positionExtractor) {
        List<E> rows = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findAllByOrderByPositionAsc":
                    return rows.stream().sorted(Comparator.comparingInt(positionExtractor)).toList();
                case "deleteAll":
                    if (args == null || args.length == 0) {
                        rows.clear();
                        return null;
                    }
                    break;
                case "flush":
                    return null;
                case "saveAll":
                    for (Object row : (Iterable<?>) args[0]) {
                        rows.add((E) row);
                    }
                    return new ArrayList<>(rows);
                case "count":
                    return (long) rows.size();
                case "toString":
                    return repoClass.getSimpleName() + rows;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            throw new UnsupportedOperationException(repoClass.getSimpleName() + " en memoire : " + method.getName());
        };
        return (R) Proxy.newProxyInstance(repoClass.getClassLoader(), new Class<?>[] {repoClass}, handler);
    }
}
