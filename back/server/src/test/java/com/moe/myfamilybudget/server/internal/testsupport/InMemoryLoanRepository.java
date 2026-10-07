package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanEntity;
import com.moe.myfamilybudget.domain.credit.core.persistence.CreditLoanRepository;

/**
 * {@link CreditLoanRepository} en memoire pour les tests unitaires du silo Credit (SILO-214, lot B).
 *
 * <p>Ne gere que les operations utilisees par {@code JpaLoanStore} ({@code findAllByOrderByPositionAsc},
 * {@code deleteAll}, {@code flush}, {@code saveAll}, {@code count}) ; toute autre operation leve une
 * {@link UnsupportedOperationException}, pour qu'un usage non prevu soit visible dans le test.
 */
public final class InMemoryLoanRepository {

    private InMemoryLoanRepository() {
    }

    public static CreditLoanRepository create() {
        List<CreditLoanEntity> rows = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findAllByOrderByPositionAsc":
                    return rows.stream().sorted(Comparator.comparingInt(CreditLoanEntity::getPosition)).toList();
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
                        rows.add((CreditLoanEntity) row);
                    }
                    return new ArrayList<>(rows);
                case "count":
                    return (long) rows.size();
                case "toString":
                    return "InMemoryLoanRepository" + rows;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            throw new UnsupportedOperationException("CreditLoanRepository en memoire : " + method.getName());
        };
        return (CreditLoanRepository) Proxy.newProxyInstance(CreditLoanRepository.class.getClassLoader(),
                new Class<?>[] {CreditLoanRepository.class}, handler);
    }
}
