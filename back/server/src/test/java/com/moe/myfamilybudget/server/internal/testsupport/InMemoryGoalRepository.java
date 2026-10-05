package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntity;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;

/**
 * {@link GoalRepository} en memoire pour les tests unitaires du silo Objectifs (SILO-212, lot B1).
 *
 * <p>Ne gere que les operations utilisees par {@code JpaGoalStore} ({@code findAllByOrderByPositionAsc},
 * {@code deleteAll}, {@code flush}, {@code saveAll}, {@code count}) ; toute autre operation leve une
 * {@link UnsupportedOperationException}, pour qu'un usage non prevu soit visible dans le test.
 */
public final class InMemoryGoalRepository {

    private InMemoryGoalRepository() {
    }

    public static GoalRepository create() {
        List<GoalEntity> rows = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findAllByOrderByPositionAsc":
                    return rows.stream().sorted(Comparator.comparingInt(GoalEntity::getPosition)).toList();
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
                        rows.add((GoalEntity) row);
                    }
                    return new ArrayList<>(rows);
                case "count":
                    return (long) rows.size();
                case "toString":
                    return "InMemoryGoalRepository" + rows;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            throw new UnsupportedOperationException("GoalRepository en memoire : " + method.getName());
        };
        return (GoalRepository) Proxy.newProxyInstance(GoalRepository.class.getClassLoader(),
                new Class<?>[] {GoalRepository.class}, handler);
    }
}
