package com.moe.myfamilybudget.infra.jpa;

import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;

/**
 * Stratégie de nommage physique commune aux silos (SILO-200).
 *
 * <p>Elle reprend exactement le comportement par défaut de Spring Boot (camelCase vers snake_case, minuscules) :
 * aucun nom de table ni de colonne ne change. Le préfixe de silo ({@code cashflow_}, {@code wealth_},
 * {@code fiscal_}, {@code pension_}, {@code goal_}, {@code credit_}) reste porté par le {@code @Table(name = ...)}
 * de chaque entité (décision D4 : une base, des tables préfixées par silo, aucune clé étrangère entre silos).
 *
 * <p>Cette classe est le point d'extension unique si une règle de nommage commune devient nécessaire ; tant
 * qu'elle ne surcharge rien, la base existante n'est pas touchée (aucune migration, {@code ddl-auto: update}).
 * Elle ne référence aucun type métier.
 */
public class SiloPhysicalNamingStrategy extends CamelCaseToUnderscoresNamingStrategy {
}
