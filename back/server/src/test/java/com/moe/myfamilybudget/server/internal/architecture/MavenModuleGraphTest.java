package com.moe.myfamilybudget.server.internal.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moe.myfamilybudget.server.internal.architecture.MavenModuleGraphRules.Edge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Garde-fou SILO-003 (voir doc/architecture/21-plan-silotage.md, section 5) : le graphe des dépendances Maven
 * internes respecte {@link MavenModuleGraphRules}. Les écarts existants sont une liste fermée et décroissante
 * ({@link MavenModuleGraphExceptions}). Le graphe est lu dans les {@code pom.xml} du reactor ({@code back/pom.xml}).
 */
class MavenModuleGraphTest {

    private static final String INTERNAL_GROUP_ID = "com.moe.myfamilybudget";
    private static final Pattern REMOVAL_PATCH = Pattern.compile("SILO-\\d{3}");

    @Test
    void graph_has_no_cycle_and_no_forbidden_edge_outside_the_closed_exception_list() throws Exception {
        Map<String, Set<String>> graph = loadGraphOrFail();
        assertEquals(List.of(), MavenModuleGraphRules.findCycle(graph), "le graphe Maven doit être acyclique");

        Set<Edge> tolerated = toleratedEdges();
        List<String> violations = new ArrayList<>();
        MavenModuleGraphRules.forbiddenEdges(graph).forEach((edge, reason) -> {
            if (!tolerated.contains(edge)) {
                violations.add(edge.from() + " -> " + edge.to() + " (" + reason + ")");
            }
        });
        assertEquals(List.of(), violations, "dépendances Maven interdites (section 5 du plan 21)");
    }

    @Test
    void exception_list_only_shrinks_and_every_entry_is_a_real_forbidden_edge() throws Exception {
        Map<String, Set<String>> graph = loadGraphOrFail();
        assertTrue(MavenModuleGraphExceptions.ENTRIES.size() <= MavenModuleGraphExceptions.FROZEN_SIZE,
                "MavenModuleGraphExceptions ne peut que décroître (gel du " + MavenModuleGraphExceptions.FROZEN_ON
                        + ", maximum " + MavenModuleGraphExceptions.FROZEN_SIZE + ")");

        Set<Edge> forbidden = MavenModuleGraphRules.forbiddenEdges(graph).keySet();
        Set<Edge> seen = new HashSet<>();
        for (MavenModuleGraphExceptions.Tolerated entry : MavenModuleGraphExceptions.ENTRIES) {
            assertTrue(seen.add(entry.edge()), "dérogation en double : " + entry.edge());
            assertTrue(entry.removedBy() != null && REMOVAL_PATCH.matcher(entry.removedBy()).matches(),
                    "dérogation sans patch de suppression SILO-xxx : " + entry.edge());
            assertTrue(forbidden.contains(entry.edge()),
                    "dérogation périmée (l'arête n'existe plus ou n'est plus interdite) : " + entry.edge()
                            + " ; supprimer la ligne et abaisser FROZEN_SIZE");
        }
    }

    @Test
    void rules_flag_a_silo_depending_on_another_silo() {
        Map<String, Set<String>> graph = Map.of(
                "retirement-core", Set.of("tax-api"),
                "tax-api", Set.of());
        assertEquals(Set.of(new Edge("retirement-core", "tax-api")),
                MavenModuleGraphRules.forbiddenEdges(graph).keySet());
    }

    @Test
    void rules_accept_a_core_depending_on_its_own_api_and_on_infra_jpa() {
        Map<String, Set<String>> graph = Map.of(
                "tax-core", Set.of("tax-api", "infra-jpa"),
                "tax-api", Set.of(),
                "infra-jpa", Set.of());
        assertTrue(MavenModuleGraphRules.forbiddenEdges(graph).isEmpty());
    }

    @Test
    void rules_flag_application_or_web_depending_on_a_core_or_on_infrastructure() {
        Map<String, Set<String>> graph = Map.of(
                "application-core", Set.of("tax-core", "tax-api"),
                "web", Set.of("infra-jpa", "application-api"),
                "application", Set.of("persistence"));
        assertEquals(Set.of(
                new Edge("application-core", "tax-core"),
                new Edge("web", "infra-jpa"),
                new Edge("application", "persistence")),
                MavenModuleGraphRules.forbiddenEdges(graph).keySet());
    }

    @Test
    void rules_accept_application_depending_only_on_silo_apis() {
        Map<String, Set<String>> graph = Map.of(
                "application", Set.of("tax-api", "market-api", "transition-snapshot", "api"),
                "web", Set.of("application-api", "api"),
                "tax-api", Set.of(),
                "market-api", Set.of(),
                "transition-snapshot", Set.of(),
                "application-api", Set.of(),
                "api", Set.of());
        assertTrue(MavenModuleGraphRules.forbiddenEdges(graph).isEmpty());
    }

    @Test
    void rules_flag_application_depending_on_a_silo_module_that_is_not_an_api() {
        Map<String, Set<String>> graph = Map.of(
                "application", Set.of("domain-tax", "market-core", "tax-api"),
                "domain-tax", Set.of(),
                "market-core", Set.of(),
                "tax-api", Set.of());
        assertEquals(Set.of(
                new Edge("application", "domain-tax"),
                new Edge("application", "market-core")),
                MavenModuleGraphRules.forbiddenEdges(graph).keySet());
    }

    @Test
    void rules_flag_a_silo_depending_on_orchestration_or_snapshot() {
        Map<String, Set<String>> graph = Map.of(
                "domain-tax", Set.of("application", "transition-snapshot"),
                "tax-api", Set.of("infra-jpa"));
        assertEquals(Set.of(
                new Edge("domain-tax", "application"),
                new Edge("domain-tax", "transition-snapshot"),
                new Edge("tax-api", "infra-jpa")),
                MavenModuleGraphRules.forbiddenEdges(graph).keySet());
    }

    @Test
    void rules_detect_a_cycle() {
        Map<String, Set<String>> cyclic = Map.of(
                "a", Set.of("b"),
                "b", Set.of("c"),
                "c", Set.of("a"));
        assertFalse(MavenModuleGraphRules.findCycle(cyclic).isEmpty());
        Map<String, Set<String>> acyclic = Map.of(
                "a", Set.of("b", "c"),
                "b", Set.of("c"),
                "c", Set.of());
        assertTrue(MavenModuleGraphRules.findCycle(acyclic).isEmpty());
    }

    private static Set<Edge> toleratedEdges() {
        Set<Edge> edges = new HashSet<>();
        for (MavenModuleGraphExceptions.Tolerated entry : MavenModuleGraphExceptions.ENTRIES) {
            edges.add(entry.edge());
        }
        return edges;
    }

    private static Map<String, Set<String>> loadGraphOrFail() throws Exception {
        Path backRoot = locateBackRoot();
        assertTrue(backRoot != null, "back/pom.xml introuvable depuis " + Path.of("").toAbsolutePath());
        return loadGraph(backRoot);
    }

    private static Path locateBackRoot() {
        Path current = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                current.resolve("..").normalize(),
                current,
                current.resolve("back"));
        for (Path candidate : candidates) {
            Path pom = candidate.resolve("pom.xml");
            if (Files.isRegularFile(pom) && Files.isDirectory(candidate.resolve("server"))
                    && Files.isRegularFile(candidate.resolve("server").resolve("pom.xml"))) {
                return candidate;
            }
        }
        return null;
    }

    private static Map<String, Set<String>> loadGraph(Path backRoot) throws Exception {
        Element parent = parse(backRoot.resolve("pom.xml")).getDocumentElement();
        Element modules = child(parent, "modules");
        List<String> directories = new ArrayList<>();
        for (Element module : children(modules, "module")) {
            directories.add(module.getTextContent().trim());
        }

        Map<String, Set<String>> declared = new TreeMap<>();
        for (String directory : directories) {
            Element project = parse(backRoot.resolve(directory).resolve("pom.xml")).getDocumentElement();
            String artifactId = child(project, "artifactId").getTextContent().trim();
            Set<String> internal = new TreeSet<>();
            Element dependencies = child(project, "dependencies");
            if (dependencies != null) {
                for (Element dependency : children(dependencies, "dependency")) {
                    Element group = child(dependency, "groupId");
                    Element artifact = child(dependency, "artifactId");
                    if (group != null && artifact != null
                            && INTERNAL_GROUP_ID.equals(group.getTextContent().trim())) {
                        internal.add(artifact.getTextContent().trim());
                    }
                }
            }
            declared.put(artifactId, internal);
        }

        Map<String, Set<String>> graph = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : declared.entrySet()) {
            Set<String> inReactor = new TreeSet<>();
            for (String dependency : entry.getValue()) {
                if (declared.containsKey(dependency)) {
                    inReactor.add(dependency);
                }
            }
            graph.put(entry.getKey(), inReactor);
        }
        return graph;
    }

    private static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && element.getTagName().equals(name)) {
                result.add(element);
            }
        }
        return result;
    }
}
