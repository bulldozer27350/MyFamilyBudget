package com.moe.myfamilybudget.server.internal.architecture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Règles de graphe Maven (SILO-003, voir doc/architecture/21-plan-silotage.md, section 5).
 *
 * <p>Le graphe est un {@code Map<module, modules internes dont il dépend>}. Les noms de module suivent la
 * convention cible : {@code domain-xxx} (silo actuel), {@code xxx-api} et {@code xxx-core} (silo après
 * séparation), {@code application}, {@code application-api}, {@code application-core}, {@code web},
 * {@code bootstrap}, {@code infra-jpa}, plus les modules de transition {@code persistence}, {@code server},
 * {@code transition-snapshot} et {@code api} (OpenAPI généré).
 *
 * <p>Règles :
 * <ol>
 *   <li>(A) un silo ne dépend d'aucun autre silo (D1) ;</li>
 *   <li>(B) un silo ne dépend ni de l'orchestration ({@code application*}, {@code web}, {@code bootstrap}), ni de
 *       {@code persistence}, {@code server}, {@code transition-snapshot}, {@code api} ; il ne dépend de
 *       {@code infra-jpa} que s'il s'agit d'un {@code *-core} ;</li>
 *   <li>(C) {@code application*} et {@code web} ne dépendent d'aucun {@code *-core} de silo, ni de
 *       {@code infra-jpa}, {@code persistence}, {@code server} ;</li>
 *   <li>(D) le graphe est acyclique.</li>
 * </ol>
 */
final class MavenModuleGraphRules {

    /** Arête orientée : {@code from} dépend de {@code to}. */
    record Edge(String from, String to) {}

    private static final Set<String> NOT_ALLOWED_FOR_SILOS =
            Set.of("persistence", "server", "transition-snapshot", "api");

    private MavenModuleGraphRules() {}

    static boolean isApplicationSide(String module) {
        return module.equals("application") || module.startsWith("application-") || module.equals("web");
    }

    static boolean isSilo(String module) {
        if (isApplicationSide(module)) {
            return false;
        }
        return module.startsWith("domain-") || module.endsWith("-core") || module.endsWith("-api");
    }

    static String siloKey(String module) {
        String key = module;
        if (key.startsWith("domain-")) {
            key = key.substring("domain-".length());
        }
        if (key.endsWith("-core")) {
            key = key.substring(0, key.length() - "-core".length());
        } else if (key.endsWith("-api")) {
            key = key.substring(0, key.length() - "-api".length());
        }
        return key;
    }

    /** Arêtes interdites par les règles A, B et C, avec leur motif. Les dérogations ne sont pas appliquées ici. */
    static Map<Edge, String> forbiddenEdges(Map<String, Set<String>> graph) {
        Map<Edge, String> result = new TreeMap<>(
                Comparator.comparing(Edge::from).thenComparing(Edge::to));
        for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                Edge edge = new Edge(from, to);
                if (isSilo(from)) {
                    if (isSilo(to) && !siloKey(from).equals(siloKey(to))) {
                        result.put(edge, "A : un silo ne dépend pas d'un autre silo");
                    } else if (isApplicationSide(to) || NOT_ALLOWED_FOR_SILOS.contains(to)
                            || (to.equals("infra-jpa") && !from.endsWith("-core"))) {
                        result.put(edge, "B : un silo ne dépend pas de l'orchestration, de la persistance de transition "
                                + "ou du snapshot");
                    }
                } else if (isApplicationSide(from)) {
                    boolean siloCore = isSilo(to) && to.endsWith("-core");
                    if (siloCore || to.equals("infra-jpa") || to.equals("persistence") || to.equals("server")) {
                        result.put(edge, "C : application et web ne dépendent que des API des silos");
                    }
                }
            }
        }
        return result;
    }

    /** Un cycle trouvé (liste de modules, le premier est répété à la fin), ou liste vide. */
    static List<String> findCycle(Map<String, Set<String>> graph) {
        Set<String> done = new HashSet<>();
        for (String start : new TreeSet<>(graph.keySet())) {
            List<String> cycle = visit(start, graph, done, new ArrayList<>());
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> visit(String node, Map<String, Set<String>> graph, Set<String> done,
            List<String> path) {
        int index = path.indexOf(node);
        if (index >= 0) {
            List<String> cycle = new ArrayList<>(path.subList(index, path.size()));
            cycle.add(node);
            return cycle;
        }
        if (!done.add(node)) {
            return List.of();
        }
        path.add(node);
        for (String next : new TreeSet<>(graph.getOrDefault(node, Set.of()))) {
            List<String> cycle = visit(next, graph, done, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        return List.of();
    }
}
