package com.moe.myfamilybudget.server.internal.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou R-92 (doc/architecture/24-backlog-reste-a-faire.md, DA-01 dans
 * doc/architecture/23-registre-decisions-actives.md) : {@code application} et {@code web} déclarent un
 * {@code module-info.java}. Celui d'{@code application} n'exporte que les packages effectivement consommés
 * par {@code web} ({@code command}, {@code error}, {@code factory}, {@code mapper}, {@code overview},
 * {@code settings}, {@code snapshot}, {@code usecase}) ; {@code model}, {@code rate} et {@code notification}
 * restent internes (utilisés uniquement dans {@code application} et par {@code bootstrap}, non modulaire,
 * qui ne passe pas par le module-path et n'est donc pas concerné par cette encapsulation).
 *
 * <p>Comme {@link MavenModuleGraphTest}, ce garde-fou lit directement les fichiers source plutôt que de
 * compiler en mode module : DA-01 précise que la garantie réelle vient du graphe Maven (SILO-003) et
 * d'ArchUnit, {@code module-info} étant ignoré sur le classpath du jar exécutable (risque 4,
 * doc/architecture/21-plan-silotage.md, section 9).
 */
class ModuleInfoVisibilityArchTest {

    private static final List<String> APPLICATION_EXPORTED_PACKAGES = List.of(
            "command", "error", "factory", "mapper", "overview", "settings", "snapshot", "usecase");

    private static final List<String> APPLICATION_INTERNAL_PACKAGES = List.of("model", "rate", "notification");

    @Test
    void application_module_info_exports_exactly_the_packages_consumed_by_web() throws Exception {
        String source = readModuleInfo("application");
        assertTrue(source.contains("module com.moe.myfamilybudget.application {"),
                "nom de module inattendu dans application/src/main/java/module-info.java");

        for (String exported : APPLICATION_EXPORTED_PACKAGES) {
            assertTrue(source.contains("exports com.moe.myfamilybudget.application." + exported + ";"),
                    "package consomme par web non exporte par application : " + exported);
        }
        for (String internal : APPLICATION_INTERNAL_PACKAGES) {
            assertFalse(source.contains("exports com.moe.myfamilybudget.application." + internal + ";"),
                    "package interne expose par erreur par application : " + internal);
        }
    }

    @Test
    void web_module_info_is_present_and_declares_the_expected_module_name() throws Exception {
        String source = readModuleInfo("web");
        assertTrue(source.contains("module com.moe.myfamilybudget.web {"),
                "nom de module inattendu dans web/src/main/java/module-info.java");
    }

    private static String readModuleInfo(String module) throws Exception {
        Path backRoot = locateBackRoot();
        assertTrue(backRoot != null, "back/pom.xml introuvable depuis " + Path.of("").toAbsolutePath());
        Path moduleInfo = backRoot.resolve(module).resolve("src/main/java/module-info.java");
        assertTrue(Files.isRegularFile(moduleInfo), "module-info.java manquant : " + moduleInfo);
        return Files.readString(moduleInfo);
    }

    private static Path locateBackRoot() {
        Path current = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                current.resolve("..").normalize(),
                current,
                current.resolve("back"));
        for (Path candidate : candidates) {
            Path pom = candidate.resolve("pom.xml");
            if (Files.isRegularFile(pom) && Files.isDirectory(candidate.resolve("bootstrap"))
                    && Files.isRegularFile(candidate.resolve("bootstrap").resolve("pom.xml"))) {
                return candidate;
            }
        }
        return null;
    }
}
