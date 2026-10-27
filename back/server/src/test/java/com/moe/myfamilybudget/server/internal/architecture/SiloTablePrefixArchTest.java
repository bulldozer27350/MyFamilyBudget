package com.moe.myfamilybudget.server.internal.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Garde-fou R-70 (DA-02, voir 23-registre-decisions-actives.md) : "une seule base, tables préfixées par silo,
 * aucune clé étrangère entre silos". L'absence de dépendance Java inter-silos (donc l'impossibilité d'un
 * {@code @ManyToOne}/{@code @JoinColumn} JPA réel vers un autre silo) est déjà vérifiée par
 * {@link SiloPersistenceIsolationArchTest} (une entité ne peut référencer une entité d'un autre silo sans en
 * dépendre au sens Java, et cette dépendance est interdite). Ce test couvre l'autre moitié de DA-02, jusqu'ici
 * non vérifiée (DA-02 : "le préfixe n'est vérifié par aucun test") : le préfixe de table de chaque entité JPA
 * correspond à son silo, et deux silos n'utilisent jamais le même préfixe.
 *
 * <p>Les préfixes retenus sont ceux déjà en base (aucun renommage de table ici : risque assumé n°5 de
 * 21-plan-silotage.md, un renommage de table impose la procédure manuelle tant que {@code ddl-auto: update} est
 * utilisé). Chaque silo a historiquement choisi son propre vocabulaire métier ({@code pension_} pour la retraite,
 * {@code fiscal_} pour les impôts, {@code cashflow_} pour la trésorerie, ...) plutôt que son nom de module Java ;
 * la liste ci-dessous les fige explicitement, silo par silo, pour détecter toute dérive (nouvelle entité sans le
 * bon préfixe, ou préfixe partagé par erreur entre deux silos) sans renommer ce qui existe déjà.
 */
@AnalyzeClasses(packages = "com.moe.myfamilybudget", importOptions = ImportOption.DoNotIncludeTests.class)
class SiloTablePrefixArchTest {

    private static final Pattern SILO_PERSISTENCE_PACKAGE =
            Pattern.compile("com\\.moe\\.myfamilybudget\\.domain\\.([a-z]+)\\.core\\.persistence");

    /** Préfixes de table autorisés pour chaque silo (clé = segment de package après {@code domain.}). */
    private static final Map<String, List<String>> ALLOWED_PREFIXES = Map.ofEntries(
            Map.entry("retirement", List.of("pension_")),
            Map.entry("tax", List.of("fiscal_")),
            Map.entry("wealth", List.of("wealth_")),
            Map.entry("treasury", List.of("cashflow_")),
            Map.entry("bankpointage", List.of("bank_", "enable_banking_")),
            Map.entry("credit", List.of("credit_", "loan_")),
            Map.entry("goals", List.of("goal", "objectifs_")),
            Map.entry("settings", List.of("app_")),
            Map.entry("notifications", List.of("notification_", "push_")),
            Map.entry("market", List.of("market_")));

    @ArchTest
    static void every_silo_entity_table_uses_its_silo_allowed_prefix(JavaClasses classes) {
        for (JavaClass clazz : classes) {
            if (!clazz.isAnnotatedWith(Entity.class)) {
                continue;
            }
            Matcher matcher = SILO_PERSISTENCE_PACKAGE.matcher(clazz.getPackageName());
            if (!matcher.matches()) {
                // Aucune entite JPA hors persistance de silo aujourd'hui ; si une apparaissait, elle devrait
                // d'abord rejoindre un silo (hors perimetre de ce garde-fou, qui ne porte que sur DA-02).
                continue;
            }
            String silo = matcher.group(1);
            List<String> prefixes = ALLOWED_PREFIXES.get(silo);
            assertTrue(prefixes != null,
                    "silo sans liste de prefixes autorises dans ce garde-fou (nouveau silo ?) : " + silo
                            + " (" + clazz.getFullName() + "), a ajouter a ALLOWED_PREFIXES");
            String tableName = clazz.getAnnotationOfType(Table.class).name();
            boolean matches = prefixes.stream().anyMatch(tableName::startsWith);
            assertTrue(matches, "table '" + tableName + "' (" + clazz.getFullName() + ", silo " + silo
                    + ") ne commence par aucun des prefixes attendus " + prefixes + " (DA-02)");
        }
    }

    @ArchTest
    static void no_two_silos_share_an_ambiguous_table_prefix(JavaClasses classes) {
        for (Map.Entry<String, List<String>> siloA : ALLOWED_PREFIXES.entrySet()) {
            for (Map.Entry<String, List<String>> siloB : ALLOWED_PREFIXES.entrySet()) {
                if (siloA.getKey().compareTo(siloB.getKey()) >= 0) {
                    continue;
                }
                for (String prefixA : siloA.getValue()) {
                    for (String prefixB : siloB.getValue()) {
                        assertTrue(!prefixA.startsWith(prefixB) && !prefixB.startsWith(prefixA),
                                "prefixes ambigus entre deux silos (DA-02) : '" + prefixA + "' (" + siloA.getKey()
                                        + ") et '" + prefixB + "' (" + siloB.getKey() + ")");
                    }
                }
            }
        }
    }

    @ArchTest
    static void every_allowed_prefix_is_actually_used(JavaClasses classes) {
        // Garde-fou miroir de every_silo_entity_table_uses_its_silo_allowed_prefix : empeche ALLOWED_PREFIXES de
        // se perimer silencieusement (prefixe retire d'une entite, ou jamais utilise, mais jamais retire d'ici).
        Map<String, Boolean> seen = new HashMap<>();
        ALLOWED_PREFIXES.forEach((silo, prefixes) -> prefixes.forEach(prefix -> seen.put(silo + "::" + prefix, false)));
        for (JavaClass clazz : classes) {
            if (!clazz.isAnnotatedWith(Entity.class)) {
                continue;
            }
            Matcher matcher = SILO_PERSISTENCE_PACKAGE.matcher(clazz.getPackageName());
            if (!matcher.matches()) {
                continue;
            }
            String silo = matcher.group(1);
            List<String> prefixes = ALLOWED_PREFIXES.get(silo);
            if (prefixes == null) {
                continue; // deja signale par every_silo_entity_table_uses_its_silo_allowed_prefix
            }
            String tableName = clazz.getAnnotationOfType(Table.class).name();
            prefixes.stream().filter(tableName::startsWith).findFirst()
                    .ifPresent(prefix -> seen.put(silo + "::" + prefix, true));
        }
        seen.forEach((key, used) -> assertTrue(used, "prefixe autorise jamais utilise par aucune entite (perime ?) : "
                + key + ", a retirer de ALLOWED_PREFIXES ou corriger"));
    }
}
