package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowChargeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowOneOffRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowSettingsRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowTransferRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableIncomeRepository;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideEntity;
import com.moe.myfamilybudget.domain.treasury.core.persistence.CashflowVariableOverrideRepository;

/**
 * Repositories en memoire pour les tests unitaires du silo Tresorerie (SILO-216, lot B1).
 */
public final class InMemoryTreasuryRepository {

    private InMemoryTreasuryRepository() {
    }

    public static CashflowIncomeRepository createIncomeRepository() {
        return createPositionRepository(CashflowIncomeRepository.class, CashflowIncomeEntity.class, CashflowIncomeEntity::getPosition);
    }

    public static CashflowChargeRepository createChargeRepository() {
        return createPositionRepository(CashflowChargeRepository.class, CashflowChargeEntity.class, CashflowChargeEntity::getPosition);
    }

    public static CashflowOneOffRepository createOneOffRepository() {
        return createPositionRepository(CashflowOneOffRepository.class, CashflowOneOffEntity.class, CashflowOneOffEntity::getPosition);
    }

    public static CashflowTransferRepository createTransferRepository() {
        return createPositionRepository(CashflowTransferRepository.class, CashflowTransferEntity.class, CashflowTransferEntity::getPosition);
    }

    public static CashflowVariableIncomeRepository createVariableIncomeRepository() {
        return createPositionRepository(CashflowVariableIncomeRepository.class, CashflowVariableIncomeEntity.class, CashflowVariableIncomeEntity::getPosition);
    }

    public static CashflowVariableOverrideRepository createVariableOverrideRepository() {
        return createPositionRepository(CashflowVariableOverrideRepository.class, CashflowVariableOverrideEntity.class, CashflowVariableOverrideEntity::getPosition);
    }

    public static CashflowSettingsRepository createSettingsRepository() {
        AtomicReference<CashflowSettingsEntity> holder = new AtomicReference<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findFirstByOrderByIdAsc":
                    return Optional.ofNullable(holder.get());
                case "deleteAll":
                    holder.set(null);
                    return null;
                case "flush":
                    return null;
                case "save":
                    CashflowSettingsEntity saved = (CashflowSettingsEntity) args[0];
                    holder.set(saved);
                    return saved;
                default:
                    break;
            }
            throw new UnsupportedOperationException("CashflowSettingsRepository en memoire : " + method.getName());
        };
        return (CashflowSettingsRepository) Proxy.newProxyInstance(CashflowSettingsRepository.class.getClassLoader(),
                new Class<?>[] {CashflowSettingsRepository.class}, handler);
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
