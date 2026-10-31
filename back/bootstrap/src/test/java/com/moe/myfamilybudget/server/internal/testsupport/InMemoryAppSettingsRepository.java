package com.moe.myfamilybudget.server.internal.testsupport;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsEntity;
import com.moe.myfamilybudget.domain.settings.core.persistence.AppSettingsRepository;

/**
 * {@link AppSettingsRepository} en memoire pour les tests unitaires du silo Parametres (R-50).
 *
 * <p>Ne gere que les operations utilisees par {@code JpaAppSettingsStore} ({@code findFirstByOrderByIdAsc},
 * {@code save}) et par les tests ({@code count}, {@code deleteAll}) ; toute autre operation leve une
 * {@link UnsupportedOperationException}, pour qu'un usage non prevu soit visible dans le test.
 */
public final class InMemoryAppSettingsRepository {

    private InMemoryAppSettingsRepository() {
    }

    public static AppSettingsRepository create() {
        List<AppSettingsEntity> rows = new ArrayList<>();
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "findFirstByOrderByIdAsc":
                    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
                case "save":
                    AppSettingsEntity entity = (AppSettingsEntity) args[0];
                    if (!rows.contains(entity)) {
                        rows.add(entity);
                    }
                    return entity;
                case "count":
                    return (long) rows.size();
                case "deleteAll":
                    if (args == null || args.length == 0) {
                        rows.clear();
                        return null;
                    }
                    break;
                case "toString":
                    return "InMemoryAppSettingsRepository" + rows;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
            throw new UnsupportedOperationException("AppSettingsRepository en memoire : " + method.getName());
        };
        return (AppSettingsRepository) Proxy.newProxyInstance(AppSettingsRepository.class.getClassLoader(),
                new Class<?>[] {AppSettingsRepository.class}, handler);
    }
}
