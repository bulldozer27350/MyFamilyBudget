package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryEntity;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthCategoryRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementEntity;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthPlacementRepository;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateEntity;
import com.moe.myfamilybudget.domain.wealth.core.persistence.WealthRealEstateRepository;

/**
 * Repositories en memoire pour les tests unitaires du silo Patrimoine (SILO-215, lot B / R-30).
 * Meme principe que {@link InMemoryTreasuryRepository} (SILO-216, lot B1).
 */
public final class InMemoryWealthRepository {

    private InMemoryWealthRepository() {
    }

    public static WealthPlacementRepository createPlacementRepository() {
        return createPositionRepository(WealthPlacementRepository.class, WealthPlacementEntity.class,
                WealthPlacementEntity::getPosition);
    }

    public static WealthRealEstateRepository createRealEstateRepository() {
        return createPositionRepository(WealthRealEstateRepository.class, WealthRealEstateEntity.class,
                WealthRealEstateEntity::getPosition);
    }

    public static WealthCategoryRepository createCategoryRepository() {
        return createPositionRepository(WealthCategoryRepository.class, WealthCategoryEntity.class,
                WealthCategoryEntity::getPosition);
    }

    @SuppressWarnings("unchecked")
    private static <R, E> R createPositionRepository(Class<R> repoClass, Class<E> entityClass,
                                                      java.util.function.ToIntFunction<E> positionExtractor) {
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
