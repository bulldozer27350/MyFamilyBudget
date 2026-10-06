package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentEntity;
import com.moe.myfamilybudget.domain.bankpointage.core.persistence.BankImportDocumentRepository;

/**
 * {@link BankImportDocumentRepository} en memoire pour les tests unitaires du silo Banque/Pointage
 * (SILO-213, lot B).
 *
 * <p>Ne gere que les operations utilisees par {@code JpaBankStore} ({@code findFirstByOrderByIdAsc},
 * {@code deleteAll}, {@code flush}, {@code save}, {@code count}) ; toute autre operation leve une
 * {@link UnsupportedOperationException}, pour qu'un usage non prevu soit visible dans le test. Les
 * identifiants sont attribues a l'insertion, comme le fait la base (generation par identite).
 */
public final class InMemoryBankDocumentRepository {

    private InMemoryBankDocumentRepository() {
    }

    public static BankImportDocumentRepository create() {
        List<BankImportDocumentEntity> rows = new ArrayList<>();
        long[] sequence = {0L};
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findFirstByOrderByIdAsc":
                    return rows.stream().min(Comparator.comparingLong(BankImportDocumentEntity::getId));
                case "deleteAll":
                    if (args == null || args.length == 0) {
                        rows.clear();
                        return null;
                    }
                    break;
                case "flush":
                    return null;
                case "save":
                    BankImportDocumentEntity entity = (BankImportDocumentEntity) args[0];
                    if (entity.getId() == null) {
                        entity.setId(++sequence[0]);
                        rows.add(entity);
                    }
                    return entity;
                case "count":
                    return (long) rows.size();
                case "toString":
                    return "InMemoryBankDocumentRepository" + rows;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            throw new UnsupportedOperationException("BankImportDocumentRepository en memoire : " + method.getName());
        };
        return (BankImportDocumentRepository) Proxy.newProxyInstance(
                BankImportDocumentRepository.class.getClassLoader(),
                new Class<?>[] {BankImportDocumentRepository.class}, handler);
    }
}
