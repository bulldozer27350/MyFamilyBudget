package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moe.myfamilybudget.ServerApplication;

/**
 * VT-320 -- Persistance apres redemarrage Spring. Preuve que la donnee n'existe pas seulement dans le
 * cache memoire : mutation, lecture, arret du contexte Spring (donc du cache {@code BudgetCacheStore} et
 * du pool de connexions), nouveau contexte sur la MEME base, lecture de controle.
 *
 * <p>Deux variantes du meme scenario :
 * <ul>
 *   <li>H2 en fichier (toujours executee, base temporaire dans {@code @TempDir}) ;</li>
 *   <li>PostgreSQL (executee uniquement si {@code MFB_TEST_POSTGRES_URL} est definie, sinon ignoree).
 *       Variables : {@code MFB_TEST_POSTGRES_URL} (ex. {@code jdbc:postgresql://localhost:5432/myfamilybudget}),
 *       {@code MFB_TEST_POSTGRES_USER} et {@code MFB_TEST_POSTGRES_PASSWORD} (defaut {@code myfamilybudget}).
 *       La base cible est ecrasee par l'import : ne pas la pointer sur des donnees a conserver.</li>
 * </ul>
 *
 * <p>Le scenario traverse quatre proprietaires de donnees (Tresorerie, Patrimoine, Parametres + Objectifs,
 * Banque) et un import bancaire avec ventilation : c'est le chemin a risque du redemarrage (lecture
 * paresseuse et objets volumineux, cf. {@code BudgetCacheStore#init}). Les contextes sont lances en HTTP reel
 * sur un port libre : le contrat verifie est celui de l'API, pas celui d'un bean.
 *
 * <p>DB-011 : le meme scenario couvre aussi les ecritures passees par les command services finalises en
 * DB-020 a DB-041 (Retraite, Fiscalite, Banque, Credit, Objectifs, historique de placement), afin que la
 * bascule JPA de chaque domaine (DB-1001 a DB-1061) soit protegee par la meme preuve de redemarrage, sur H2 et
 * sur PostgreSQL, sans suite dupliquee.
 */
@DisplayName("VT-320 -- Persistance apres redemarrage Spring")
class RestartPersistenceTest {

    private static final double EPS = 0.01;
    private static final String NEW_INCOME_LABEL = "Revenu VT-320";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("H2 fichier : mutations -> GET -> redemarrage Spring -> GET identique")
    void h2File_dataSurvivesSpringRestart() throws Exception {
        String url = "jdbc:h2:file:" + tempDir.resolve("vt320").toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE";
        runRestartScenario(List.of(
                "--spring.datasource.url=" + url,
                "--spring.datasource.driver-class-name=org.h2.Driver",
                "--spring.datasource.username=sa",
                "--spring.datasource.password="));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MFB_TEST_POSTGRES_URL", matches = ".+")
    @DisplayName("PostgreSQL : mutations -> GET -> redemarrage Spring -> GET identique")
    void postgres_dataSurvivesSpringRestart() throws Exception {
        String user = envOrDefault("MFB_TEST_POSTGRES_USER", "myfamilybudget");
        String password = envOrDefault("MFB_TEST_POSTGRES_PASSWORD", "myfamilybudget");
        runRestartScenario(List.of(
                "--spring.datasource.url=" + System.getenv("MFB_TEST_POSTGRES_URL"),
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password));
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // =========================================================================
    // Scenario
    // =========================================================================

    private void runRestartScenario(List<String> databaseArgs) throws Exception {
        String incomeId;

        // --- Instance 1 : mutations puis relecture (etapes A et B) ---
        try (ConfigurableApplicationContext first = startApplication(databaseArgs)) {
            Api api = new Api(first);

            api.post("/budget/import", referenceDatasetWithBankImport(), 200);
            assertThat(api.getJson("/tresorerie").path("charges").get(0).path("monthly").asDouble())
                    .as("etat de reference avant mutation").isCloseTo(900.0, within(EPS));

            api.put("/tresorerie/charges/chg_1", "{\"field\":\"monthly\",\"value\":1234.5}", 200);
            JsonNode created = objectMapper.readTree(api.post("/tresorerie/incomes", "{}", 201));
            incomeId = created.path("id").asText();
            assertThat(incomeId).isNotBlank();
            api.put("/tresorerie/incomes/" + incomeId,
                    "{\"field\":\"label\",\"value\":\"" + NEW_INCOME_LABEL + "\"}", 200);
            api.put("/tresorerie/placements/plc_1", "{\"field\":\"balance\",\"value\":12345}", 200);
            api.put("/settings", "{\"settings\":{\"retireAge\":58,\"goalSecureHorizonMonths\":24}}", 200);
            applyDb011Mutations(api);

            assertMutatedState(api, incomeId, "avant redemarrage");
        }

        // --- Instance 2 : nouveau contexte Spring sur la meme base (etape D) ---
        try (ConfigurableApplicationContext second = startApplication(databaseArgs)) {
            assertMutatedState(new Api(second), incomeId, "apres redemarrage");
        }
    }

    /** Memes assertions avant et apres le redemarrage : toute difference est une perte de persistance. */
    private void assertMutatedState(Api api, String incomeId, String moment) throws Exception {
        // Tresorerie : charge modifiee + revenu cree puis renomme
        JsonNode tresorerie = api.getJson("/tresorerie");
        JsonNode charge = byId(tresorerie.path("charges"), "chg_1");
        assertThat(charge).as("charge chg_1 %s", moment).isNotNull();
        assertThat(charge.path("monthly").asDouble()).as("charge chg_1 %s", moment).isCloseTo(1234.5, within(EPS));
        assertThat(tresorerie.path("incomes")).as("revenus %s", moment).hasSize(2);
        JsonNode income = byId(tresorerie.path("incomes"), incomeId);
        assertThat(income).as("revenu cree %s", moment).isNotNull();
        assertThat(income.path("label").asText()).as("libelle du revenu %s", moment).isEqualTo(NEW_INCOME_LABEL);

        // Patrimoine : solde du placement modifie
        JsonNode placement = byId(api.getJson("/patrimoine").path("placements"), "plc_1");
        assertThat(placement).as("placement plc_1 %s", moment).isNotNull();
        assertThat(placement.path("balance").asDouble()).as("solde PEA %s", moment).isCloseTo(12345.0, within(EPS));

        // Parametres (table budget) + Objectifs (table distincte)
        JsonNode settings = api.getJson("/settings");
        assertThat(settings.path("settings").path("retireAge").asInt()).as("age de retraite %s", moment).isEqualTo(58);
        assertThat(settings.path("settings").path("goalSecureHorizonMonths").asInt())
                .as("horizon de securite %s", moment).isEqualTo(24);
        assertThat(settings.path("retireYear").asInt()).as("annee de retraite (settings) %s", moment).isEqualTo(2048);
        assertThat(api.getJson("/overview").path("retireYear").asInt())
                .as("annee de retraite (overview) %s", moment).isEqualTo(2048);

        // Donnees non modifiees apres l'import : retraite (collection paresseuse) et banque (objet volumineux)
        JsonNode budget = api.getJson("/budget");
        assertThat(budget.path("retirement").path("people").get(0).path("name").asText())
                .as("personne retraite %s", moment).isEqualTo("Alice");
        JsonNode transactions = budget.path("bankImport").path("transactions");
        assertThat(transactions).as("transactions bancaires %s", moment).hasSize(2);
        JsonNode split = byId(transactions, "tx_split");
        assertThat(split).as("transaction ventilee %s", moment).isNotNull();
        assertThat(split.path("splits")).as("ventilation %s", moment).hasSize(2);
        assertThat(split.path("amount").asDouble()).as("montant ventile %s", moment).isCloseTo(-200.0, within(EPS));
        assertThat(budget.path("oneoff").get(0).path("amount").asDouble())
                .as("depense ponctuelle %s", moment).isCloseTo(15000.0, within(EPS));

        assertDb011State(api, budget, moment);
    }

    // =========================================================================
    // DB-011 : ecritures des command services Retraite, Fiscalite, Banque, Credit, Objectifs, Patrimoine
    // =========================================================================

    private void applyDb011Mutations(Api api) throws Exception {
        // Retraite (DB-020) : une personne modifiee, une personne ajoutee
        api.put("/retraite", """
                {
                  "people": [
                    { "id": "p_1", "name": "Alice", "birthYear": 1990, "incomeLabel": "Salaire",
                      "trimestresValides": 150, "trimestresDate": "2025-12-31",
                      "salaryHistory": [ { "year": 2024, "salary": 35000 }, { "year": 2025, "salary": 36000 } ],
                      "agircPoints": 2500, "ratioPointsParEuro": 0.0051 },
                    { "id": "p_2", "name": "Bob", "birthYear": 1992, "incomeLabel": "Salaire Bob",
                      "trimestresValides": 90, "trimestresDate": "2025-12-31",
                      "salaryHistory": [], "agircPoints": 1200, "ratioPointsParEuro": 0.0051 }
                  ],
                  "pass2026": 47100, "passGrowthRate": 0.015, "agircPointValue": 1.4386,
                  "agircPointDateGlobal": "2025-01-01", "agircPointGrowthRate": 0.01
                }
                """, 200);

        // Fiscalite (DB-021) : une liste fournie remplace l'existante, les autres restent inchangees
        api.put("/impots",
                "{\"taxChildren\":[{\"id\":\"child_db011\",\"name\":\"Enfant DB-011\",\"birthYear\":2015}]}", 200);

        // Banque (DB-040) : recategorisation d'une transaction de l'import
        api.put("/bank-import/transactions/tx_loyer/category", "{\"categoryId\":\"cat_courses\"}", 200);

        // Credit (DB-041) et Objectifs (DB-041) : ecritures routees vers leurs command services
        api.post("/patrimoine/loans", """
                { "id": "loan_db011", "label": "Pret DB-011", "crd": 150000, "rate": 0.02, "monthly": 900,
                  "insurance": 20, "startDate": "2024-01-01", "endDate": "2044-01-01" }
                """, 200);
        api.post("/patrimoine/objectifs", """
                { "id": "goal_db011", "label": "Objectif DB-011", "targetAmount": 5000, "targetDate": "2027-06-01" }
                """, 200);

        // Patrimoine (DB-030) : point d'historique de valorisation, de meme valeur que le solde deja ecrit
        // (la ligne synchronise son solde sur le dernier releve : le solde attendu reste 12345).
        api.post("/patrimoine/placements/plc_1/historique",
                "{\"date\":\"2026-02-01\",\"value\":12345,\"notes\":\"DB-011\"}", 200);
    }

    private void assertDb011State(Api api, JsonNode budget, String moment) throws Exception {
        JsonNode people = budget.path("retirement").path("people");
        assertThat(people).as("personnes retraite %s", moment).hasSize(2);
        assertThat(byId(people, "p_1").path("trimestresValides").asInt())
                .as("trimestres valides d'Alice %s", moment).isEqualTo(150);
        assertThat(byId(people, "p_2")).as("personne ajoutee %s", moment).isNotNull();
        assertThat(byId(people, "p_2").path("name").asText()).as("nom de la personne ajoutee %s", moment)
                .isEqualTo("Bob");

        JsonNode child = byId(budget.path("taxChildren"), "child_db011");
        assertThat(child).as("enfant fiscal %s", moment).isNotNull();
        assertThat(child.path("name").asText()).as("nom de l'enfant fiscal %s", moment).isEqualTo("Enfant DB-011");

        JsonNode loyer = byId(budget.path("bankImport").path("transactions"), "tx_loyer");
        assertThat(loyer).as("transaction tx_loyer %s", moment).isNotNull();
        assertThat(loyer.path("categoryId").asText()).as("categorie de tx_loyer %s", moment)
                .isEqualTo("cat_courses");

        JsonNode goal = byId(budget.path("objectifs"), "goal_db011");
        assertThat(goal).as("objectif %s", moment).isNotNull();
        assertThat(goal.path("targetAmount").asDouble()).as("montant cible %s", moment).isCloseTo(5000.0, within(EPS));

        JsonNode patrimoine = api.getJson("/patrimoine");
        JsonNode loan = byId(patrimoine.path("loans"), "loan_db011");
        assertThat(loan).as("pret %s", moment).isNotNull();
        assertThat(loan.path("label").asText()).as("libelle du pret %s", moment).isEqualTo("Pret DB-011");
        assertThat(byId(patrimoine.path("placements"), "plc_1").path("history"))
                .as("historique du placement plc_1 %s", moment).hasSize(1);
    }

    private static JsonNode byId(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.path("id").asText())) return node;
        }
        return null;
    }

    // =========================================================================
    // Demarrage d'une instance complete
    // =========================================================================

    /**
     * Demarre l'application complete (HTTP reel, port libre) sur la base indiquee. Les arguments de ligne de
     * commande l'emportent sur {@code src/test/resources/application.yml}, dont le {@code create-drop}
     * effacerait la base a chaque arret : on force {@code update}, comme le profil par defaut et le profil
     * {@code docker}.
     */
    private ConfigurableApplicationContext startApplication(List<String> databaseArgs) {
        List<String> args = new ArrayList<>(databaseArgs);
        args.add("--server.port=0");
        args.add("--spring.jpa.hibernate.ddl-auto=update");
        args.add("--spring.jpa.show-sql=false");
        args.add("--spring.main.headless=true");
        args.add("--myfamilybudget.browser.auto-open=false");
        args.add("--myfamilybudget.market-data.enabled=false");
        return new SpringApplicationBuilder(ServerApplication.class).run(args.toArray(new String[0]));
    }

    // =========================================================================
    // Jeu de donnees
    // =========================================================================

    /**
     * Dataset de reference ({@code mock-budget.json}, fichier partage inchange) complete par un import bancaire
     * avec une transaction ventilee.
     */
    private String referenceDatasetWithBankImport() throws Exception {
        String raw = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8)
                .replace("\uFEFF", "");
        ObjectNode budget = (ObjectNode) objectMapper.readTree(raw);
        budget.set("bankImport", objectMapper.readTree("""
                {
                  "categories": [
                    { "id": "cat_loyer", "label": "Logement", "kind": "D\\u00e9pense", "compressible": "Non" },
                    { "id": "cat_courses", "label": "Alimentation", "kind": "D\\u00e9pense", "compressible": "Oui" }
                  ],
                  "transactions": [
                    { "id": "tx_loyer", "date": "2026-01-05", "label": "Paiement Loyer", "type": "VIR",
                      "amount": -800, "categoryId": "cat_loyer" },
                    { "id": "tx_split", "date": "2026-01-15", "label": "Hypermarche", "type": "CB",
                      "amount": -200, "categoryId": "cat_courses",
                      "splits": [
                        { "id": "s1", "categoryId": "cat_courses", "amount": -120, "label": "Alimentation" },
                        { "id": "s2", "categoryId": "cat_loyer", "amount": -80, "label": "Logement" }
                      ] }
                  ],
                  "matchings": []
                }
                """));
        return objectMapper.writeValueAsString(budget);
    }

    // =========================================================================
    // Client HTTP minimal (JDK uniquement)
    // =========================================================================

    private final class Api {
        private final HttpClient client = HttpClient.newHttpClient();
        private final String baseUrl;

        Api(ConfigurableApplicationContext context) {
            String port = context.getEnvironment().getProperty("local.server.port");
            assertThat(port).as("port HTTP attribue").isNotBlank();
            String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "");
            this.baseUrl = "http://localhost:" + port + contextPath;
        }

        JsonNode getJson(String path) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
            return objectMapper.readTree(send(request, 200));
        }

        String post(String path, String body, int expectedStatus) throws Exception {
            return send(jsonRequest(path).POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    expectedStatus);
        }

        String put(String path, String body, int expectedStatus) throws Exception {
            return send(jsonRequest(path).PUT(BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    expectedStatus);
        }

        private HttpRequest.Builder jsonRequest(String path) {
            return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Content-Type", "application/json");
        }

        private String send(HttpRequest request, int expectedStatus) throws Exception {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertThat(response.statusCode())
                    .as("%s %s => %s", request.method(), request.uri().getPath(), response.body())
                    .isEqualTo(expectedStatus);
            return response.body();
        }
    }
}
