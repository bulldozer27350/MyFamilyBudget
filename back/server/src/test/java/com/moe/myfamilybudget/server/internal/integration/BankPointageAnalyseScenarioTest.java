package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * VT-120 -- Scenario backend Banque -> Pointage -> Analyse.
 *
 * <p>Fige la coherence entre l'import bancaire (transactions, categories, ventilations, operations
 * en cours), le pointage ({@code GET /pointage}, {@code PUT /pointage/matchings/{monthISO}}) et
 * l'analyse ({@code GET /analyse}). Les valeurs attendues caracterisent le comportement actuel de
 * {@code AnalyseCalculator} (voir doc/architecture/07-domaine-banque-pointage.md et
 * 08-domaine-analyse.md).
 *
 * <p>Le dataset est construit relativement au mois courant (M0) et au mois precedent (M1) car
 * l'analyse se base sur la date du jour. Les lignes budgetaires ont un debut ancien, une croissance
 * nulle et une inflation nulle : leurs montants mensuels sont donc constants quelle que soit la
 * date d'execution. Seul un passage de minuit a un changement de mois pendant le test pourrait le
 * perturber.
 *
 * <pre>
 * Budget mensuel : Loyer 800, Courses 400 (charges), Salaire 2500 (revenu), Livret 200 (epargne)
 * M0 : tx_1 loyer -800 | tx_2 supermarche -150 | tx_3 salaire +2500 | tx_4 cinema -40 | tx_5 divers -25
 *      operation en cours : Courses Drive -80 (ligne Courses)
 * M1 : tx_6 loyer -800 | tx_7 supermarche -350 | tx_8 salaire +2500
 *      tx_9 hypermarche -200 ventile : -120 alimentation (s1) / -80 loisirs (s2)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("VT-120 -- Scenario Banque -> Pointage -> Analyse")
class BankPointageAnalyseScenarioTest {

    private static final double EUR = 0.01;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String m0;
    private String m1;

    @BeforeEach
    void importBankScenario() throws Exception {
        YearMonth current = YearMonth.now();
        m0 = current.toString();
        m1 = current.minusMonths(1).toString();
        mockMvc.perform(post("/api/v1/budget/import")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson(m0, m1)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // BANQUE -> POINTAGE
    // =========================================================================

    @Test
    @DisplayName("GET /pointage restitue transactions, ventilations, categories, pointages et lignes budgetaires")
    void pointageExposesImportedBankData() throws Exception {
        JsonNode root = getJson("/api/v1/pointage");

        assertThat(root.path("transactions")).hasSize(9);
        assertThat(root.path("categories")).hasSize(4);
        assertThat(root.path("matchings")).hasSize(2);
        assertThat(root.path("charges")).hasSize(2);
        assertThat(root.path("incomes")).hasSize(1);
        assertThat(root.path("placements")).hasSize(1);

        JsonNode loyer = findById(root.path("transactions"), "tx_1");
        assertThat(loyer.path("amount").asDouble()).isCloseTo(-800.0, within(EUR));
        assertThat(loyer.path("categoryId").asText()).isEqualTo("cat_loyer");
        assertThat(loyer.path("type").asText()).isEqualTo("VIR");
        assertThat(loyer.path("date").asText()).isEqualTo(m0 + "-05");

        JsonNode split = findById(root.path("transactions"), "tx_9");
        assertThat(split.path("splits")).hasSize(2);
        assertThat(split.path("splits").get(0).path("id").asText()).isEqualTo("s1");
        assertThat(split.path("splits").get(0).path("categoryId").asText()).isEqualTo("cat_courses");
        assertThat(split.path("splits").get(0).path("amount").asDouble()).isCloseTo(-120.0, within(EUR));
        assertThat(split.path("splits").get(1).path("categoryId").asText()).isEqualTo("cat_loisirs");
        assertThat(split.path("splits").get(1).path("amount").asDouble()).isCloseTo(-80.0, within(EUR));

        JsonNode matchingM1 = findMatching(root.path("matchings"), m1);
        JsonNode coursesLink = findLink(matchingM1, "chg_courses");
        assertThat(coursesLink.path("txIds")).extracting(JsonNode::asText).containsExactly("tx_7", "tx_9#s1");
    }

    // =========================================================================
    // ANALYSE : KPI ET CATEGORIES
    // =========================================================================

    @Test
    @DisplayName("KPI : depenses, revenus, non categorisees et compressibles sur 12 mois (ventilations incluses)")
    void analyseKpis() throws Exception {
        JsonNode kpis = getJson("/api/v1/analyse?monthsBack=12").path("kpis");
        // 1015 (M0) + 1350 (M1, dont 200 ventiles)
        assertThat(kpis.path("totalExpenses").asDouble()).isCloseTo(2365.00, within(EUR));
        assertThat(kpis.path("totalIncome").asDouble()).isCloseTo(5000.00, within(EUR));
        assertThat(kpis.path("nbMonths").asInt()).isEqualTo(12);
        assertThat(kpis.path("uncategorizedCount").asInt()).isEqualTo(1);
        // Categories compressibles : Alimentation (620) + Loisirs (120)
        assertThat(kpis.path("compressibleTotal").asDouble()).isCloseTo(740.00, within(EUR));
    }

    @Test
    @DisplayName("categorySummaries : cumul par categorie, ventilations repartissees, tri decroissant")
    void analyseCategorySummaries() throws Exception {
        JsonNode summaries = getJson("/api/v1/analyse?monthsBack=12").path("categorySummaries");
        assertThat(summaries).hasSize(4);

        String[] labels = {"Logement", "Alimentation", "Loisirs", "Non cat\u00e9goris\u00e9"};
        double[] amounts = {1600.00, 620.00, 120.00, 25.00};
        for (int i = 0; i < labels.length; i++) {
            assertThat(summaries.get(i).path("label").asText()).as("label #%d", i).isEqualTo(labels[i]);
            assertThat(summaries.get(i).path("amount").asDouble()).as("amount %s", labels[i])
                    .isCloseTo(amounts[i], within(EUR));
        }
        assertThat(summaries.get(0).path("color").asText()).isEqualTo("#A8503C");
        assertThat(summaries.get(3).path("color").asText()).isEqualTo("#6B7278");

        double total = 0;
        for (JsonNode s : summaries) {
            total += s.path("amount").asDouble();
        }
        assertThat(total).isCloseTo(getJson("/api/v1/analyse?monthsBack=12").path("kpis")
                .path("totalExpenses").asDouble(), within(EUR));
    }

    // =========================================================================
    // ANALYSE : MOIS COURANT, HISTORIQUE, DERIVES
    // =========================================================================

    @Test
    @DisplayName("landingData du mois courant : budgete vs reel pointe, operation en cours incluse")
    void analyseLandingCurrentMonth() throws Exception {
        JsonNode root = getJson("/api/v1/analyse?monthsBack=12");
        assertThat(root.path("currentMonthISO").asText()).isEqualTo(m0);

        JsonNode landing = root.path("landingData");
        assertThat(landing).hasSize(4);
        // Tri par montant budgete decroissant.
        assertThat(landing).extracting(row -> row.path("id").asText())
                .containsExactly("inc_salaire", "chg_loyer", "chg_courses", "plc_1");

        JsonNode salaire = findById(landing, "inc_salaire");
        assertThat(salaire.path("kind").asText()).isEqualTo("revenu");
        assertThat(salaire.path("budgeted").asDouble()).isCloseTo(2500.0, within(EUR));
        assertThat(salaire.path("reel").asDouble()).isCloseTo(2500.0, within(EUR));
        assertThat(salaire.path("status").asText()).isEqualTo("match");

        JsonNode loyer = findById(landing, "chg_loyer");
        assertThat(loyer.path("kind").asText()).isEqualTo("charge");
        assertThat(loyer.path("reel").asDouble()).isCloseTo(800.0, within(EUR));
        assertThat(loyer.path("pct").asDouble()).isCloseTo(100.0, within(EUR));
        assertThat(loyer.path("status").asText()).isEqualTo("match");

        // Courses : 150 pointes + 80 en cours = 230 sur 400 budgetes.
        JsonNode courses = findById(landing, "chg_courses");
        assertThat(courses.path("budgeted").asDouble()).isCloseTo(400.0, within(EUR));
        assertThat(courses.path("reel").asDouble()).isCloseTo(230.0, within(EUR));
        assertThat(courses.path("pendingContrib").asDouble()).isCloseTo(80.0, within(EUR));
        assertThat(courses.path("hasPendingContrib").asBoolean()).isTrue();
        assertThat(courses.path("pct").asDouble()).isCloseTo(57.5, within(EUR));
        assertThat(courses.path("status").asText()).isEqualTo("economy");

        // Epargne non pointee : statut en attente, aucun reel.
        JsonNode epargne = findById(landing, "plc_1");
        assertThat(epargne.path("kind").asText()).isEqualTo("placement");
        assertThat(epargne.path("budgeted").asDouble()).isCloseTo(200.0, within(EUR));
        assertThat(epargne.path("reel").asDouble()).isCloseTo(0.0, within(EUR));
        assertThat(epargne.path("status").asText()).isEqualTo("pending");
        assertThat(epargne.path("hasPendingContrib").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("monthlyCompareData : un point par mois, M1 (ventilation pointee) et M0 (operation en cours)")
    void analyseMonthlyCompare() throws Exception {
        JsonNode compare = getJson("/api/v1/analyse?monthsBack=12").path("monthlyCompareData");
        assertThat(compare).hasSize(12);
        assertThat(compare.get(11).path("monthISO").asText()).isEqualTo(m0);
        assertThat(compare.get(10).path("monthISO").asText()).isEqualTo(m1);

        JsonNode current = findByMonth(compare, m0);
        assertThat(current.path("budgeted").asDouble()).isCloseTo(3900.0, within(EUR));
        // 800 loyer + 150 courses + 2500 salaire + 80 operation en cours
        assertThat(current.path("reel").asDouble()).isCloseTo(3530.0, within(EUR));
        assertThat(current.path("hasPointing").asBoolean()).isTrue();

        JsonNode previous = findByMonth(compare, m1);
        assertThat(previous.path("budgeted").asDouble()).isCloseTo(3900.0, within(EUR));
        // 800 loyer + (350 + 120 de la ventilation s1) courses + 2500 salaire
        assertThat(previous.path("reel").asDouble()).isCloseTo(3770.0, within(EUR));
        assertThat(previous.path("hasPointing").asBoolean()).isTrue();

        JsonNode untouched = compare.get(0);
        assertThat(untouched.path("reel").asDouble()).isCloseTo(0.0, within(EUR));
        assertThat(untouched.path("hasPointing").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("driftRows : moyennes 3 et 12 mois sur les mois pointes, ecart au budget et statut")
    void analyseDriftRows() throws Exception {
        JsonNode drift = getJson("/api/v1/analyse?monthsBack=12").path("driftRows");
        assertThat(drift).hasSize(4);

        JsonNode loyer = findById(drift, "chg_loyer");
        assertThat(loyer.path("avg3m").asDouble()).isCloseTo(800.0, within(EUR));
        assertThat(loyer.path("avg12m").asDouble()).isCloseTo(800.0, within(EUR));
        assertThat(loyer.path("ecart").asDouble()).isCloseTo(0.0, within(EUR));
        assertThat(loyer.path("months").asInt()).isEqualTo(2);
        assertThat(loyer.path("status").asText()).isEqualTo("match");

        // Courses : M0 = 150, M1 = 350 + 120 = 470 -> moyenne 310 pour 400 budgetes.
        JsonNode courses = findById(drift, "chg_courses");
        assertThat(courses.path("budgeted").asDouble()).isCloseTo(400.0, within(EUR));
        assertThat(courses.path("avg3m").asDouble()).isCloseTo(310.0, within(EUR));
        assertThat(courses.path("avg12m").asDouble()).isCloseTo(310.0, within(EUR));
        assertThat(courses.path("ecart").asDouble()).isCloseTo(-90.0, within(EUR));
        assertThat(courses.path("ecartPct").asDouble()).isCloseTo(-22.5, within(EUR));
        assertThat(courses.path("months").asInt()).isEqualTo(2);
        assertThat(courses.path("status").asText()).isEqualTo("economy");

        JsonNode salaire = findById(drift, "inc_salaire");
        assertThat(salaire.path("avg3m").asDouble()).isCloseTo(2500.0, within(EUR));
        assertThat(salaire.path("status").asText()).isEqualTo("match");

        // Aucune ligne pointee pour l'epargne : pas de moyenne (champs absents ou nuls dans le JSON).
        JsonNode epargne = findById(drift, "plc_1");
        assertThat(isAbsentOrNull(epargne, "avg3m")).isTrue();
        assertThat(isAbsentOrNull(epargne, "avg12m")).isTrue();
        assertThat(isAbsentOrNull(epargne, "ecart")).isTrue();
        assertThat(epargne.path("months").asInt()).isZero();
        assertThat(epargne.path("status").asText()).isEqualTo("pending");
    }

    // =========================================================================
    // POINTAGE -> ANALYSE
    // =========================================================================

    @Test
    @DisplayName("PUT /pointage/matchings/{mois} : le nouveau pointage est relu par /pointage et propage dans /analyse")
    void updatedMatchingPropagatesToAnalyse() throws Exception {
        String body = """
                { "links": [
                  { "budgetLineId": "chg_loyer",   "txIds": ["tx_1"] },
                  { "budgetLineId": "chg_courses", "txIds": ["tx_2", "tx_4"] },
                  { "budgetLineId": "inc_salaire", "txIds": ["tx_3"] }
                ] }
                """;
        mockMvc.perform(put("/api/v1/pointage/matchings/" + m0)
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());

        // GET /pointage : M0 remplace, M1 inchange.
        JsonNode matchings = getJson("/api/v1/pointage").path("matchings");
        assertThat(matchings).hasSize(2);
        assertThat(findLink(findMatching(matchings, m0), "chg_courses").path("txIds"))
                .extracting(JsonNode::asText).containsExactly("tx_2", "tx_4");
        assertThat(findLink(findMatching(matchings, m1), "chg_courses").path("txIds"))
                .extracting(JsonNode::asText).containsExactly("tx_7", "tx_9#s1");

        JsonNode analyse = getJson("/api/v1/analyse?monthsBack=12");

        // Courses M0 : 150 + 40 pointes + 80 en cours = 270.
        JsonNode courses = findById(analyse.path("landingData"), "chg_courses");
        assertThat(courses.path("reel").asDouble()).isCloseTo(270.0, within(EUR));
        assertThat(courses.path("pct").asDouble()).isCloseTo(67.5, within(EUR));
        assertThat(courses.path("status").asText()).isEqualTo("economy");

        assertThat(findByMonth(analyse.path("monthlyCompareData"), m0).path("reel").asDouble())
                .isCloseTo(3570.0, within(EUR));

        // Derive : M0 = 190, M1 = 470 -> moyenne 330.
        JsonNode drift = findById(analyse.path("driftRows"), "chg_courses");
        assertThat(drift.path("avg3m").asDouble()).isCloseTo(330.0, within(EUR));
        assertThat(drift.path("ecart").asDouble()).isCloseTo(-70.0, within(EUR));
        assertThat(drift.path("ecartPct").asDouble()).isCloseTo(-17.5, within(EUR));

        // Les KPI et categories dependent des transactions, pas du pointage : inchanges.
        assertThat(analyse.path("kpis").path("totalExpenses").asDouble()).isCloseTo(2365.00, within(EUR));
        assertThat(analyse.path("kpis").path("totalIncome").asDouble()).isCloseTo(5000.00, within(EUR));
    }

    // =========================================================================
    // FENETRE D'ANALYSE
    // =========================================================================

    @Test
    @DisplayName("monthsBack : nombre de mois et de points de comparaison (3 mois, toute la periode)")
    void analyseMonthsBackWindow() throws Exception {
        JsonNode threeMonths = getJson("/api/v1/analyse?monthsBack=3");
        assertThat(threeMonths.path("kpis").path("nbMonths").asInt()).isEqualTo(3);
        assertThat(threeMonths.path("monthlyCompareData")).hasSize(3);
        assertThat(threeMonths.path("kpis").path("totalExpenses").asDouble()).isCloseTo(2365.00, within(EUR));

        JsonNode allPeriod = getJson("/api/v1/analyse?monthsBack=0");
        assertThat(allPeriod.path("kpis").path("nbMonths").asInt()).isEqualTo(1);
        assertThat(allPeriod.path("monthlyCompareData")).hasSize(12);
        assertThat(allPeriod.path("kpis").path("totalExpenses").asDouble()).isCloseTo(2365.00, within(EUR));
        assertThat(allPeriod.path("kpis").path("totalIncome").asDouble()).isCloseTo(5000.00, within(EUR));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(get(url).contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static JsonNode findById(JsonNode array, String id) {
        for (JsonNode node : array) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Element " + id + " absent");
    }

    private static boolean isAbsentOrNull(JsonNode node, String field) {
        return !node.has(field) || node.get(field).isNull();
    }

    private static JsonNode findByMonth(JsonNode array, String monthISO) {
        for (JsonNode node : array) {
            if (monthISO.equals(node.path("monthISO").asText())) {
                return node;
            }
        }
        throw new AssertionError("Mois " + monthISO + " absent");
    }

    private static JsonNode findMatching(JsonNode matchings, String monthISO) {
        for (JsonNode node : matchings) {
            if (monthISO.equals(node.path("month").asText())) {
                return node;
            }
        }
        throw new AssertionError("Pointage du mois " + monthISO + " absent");
    }

    private static JsonNode findLink(JsonNode matching, String budgetLineId) {
        for (JsonNode link : matching.path("links")) {
            if (budgetLineId.equals(link.path("budgetLineId").asText())) {
                return link;
            }
        }
        throw new AssertionError("Lien " + budgetLineId + " absent");
    }

    /** Dataset : %1$s = mois courant (M0), %2$s = mois precedent (M1). */
    private static String scenarioJson(String m0, String m1) {
        return """
                {
                  "settings": {
                    "birthYear": 1990, "retireAge": 64, "simulateUntilAge": 85,
                    "inflationRate": 0, "pivotDate": "2026-01-01", "pivotMode": "manual",
                    "startBalance": 0, "childExitAge": 21, "taxAbattement": 0,
                    "pass2026": 47100, "passGrowthRate": 0.015
                  },
                  "charges": [
                    { "id": "chg_loyer", "label": "Loyer", "monthly": 800, "start": "2000-01-01", "end": "2100-12-31", "growthRate": 0, "categoryId": "cat_loyer" },
                    { "id": "chg_courses", "label": "Courses", "monthly": 400, "start": "2000-01-01", "end": "2100-12-31", "growthRate": 0, "categoryId": "cat_courses" }
                  ],
                  "incomes": [
                    { "id": "inc_salaire", "label": "Salaire", "monthly": 2500, "start": "2000-01-01", "end": "2100-12-31", "growthRate": 0, "categoryId": "cat_salaire" }
                  ],
                  "placements": [
                    { "id": "plc_1", "label": "Livret", "category": "Livrets", "balance": 1000, "balanceDate": "2026-01-01",
                      "monthly": 200, "monthlyFrom": "2000-01-01", "monthlyUntil": "2100-12-31",
                      "ratePess": 0.01, "rateCorr": 0.02, "rateOpti": 0.03, "excludedFromRetirement": false, "notes": "" }
                  ],
                  "realEstate": [], "taxChildren": [], "taxBrackets": [], "taxRateOverrides": [],
                  "taxActualOverrides": [], "oneoff": [], "transfers": [],
                  "variableIncomes": [], "variableOverrides": [], "assetCategories": [],
                  "bankImport": {
                    "categories": [
                      { "id": "cat_loyer", "label": "Logement", "kind": "D\u00e9pense", "compressible": "Non" },
                      { "id": "cat_courses", "label": "Alimentation", "kind": "D\u00e9pense", "compressible": "Oui" },
                      { "id": "cat_loisirs", "label": "Loisirs", "kind": "D\u00e9pense", "compressible": "Oui" },
                      { "id": "cat_salaire", "label": "Revenus", "kind": "Revenu", "compressible": "Non" }
                    ],
                    "transactions": [
                      { "id": "tx_1", "date": "%1$s-05", "label": "Paiement Loyer", "type": "VIR", "amount": -800, "categoryId": "cat_loyer" },
                      { "id": "tx_2", "date": "%1$s-10", "label": "Supermarche", "type": "CB", "amount": -150, "categoryId": "cat_courses" },
                      { "id": "tx_3", "date": "%1$s-28", "label": "Virement Employeur", "type": "VIR", "amount": 2500, "categoryId": "cat_salaire" },
                      { "id": "tx_4", "date": "%1$s-12", "label": "Cinema", "type": "CB", "amount": -40, "categoryId": "cat_loisirs" },
                      { "id": "tx_5", "date": "%1$s-15", "label": "Divers", "type": "CB", "amount": -25 },
                      { "id": "tx_6", "date": "%2$s-05", "label": "Paiement Loyer", "type": "VIR", "amount": -800, "categoryId": "cat_loyer" },
                      { "id": "tx_7", "date": "%2$s-10", "label": "Supermarche", "type": "CB", "amount": -350, "categoryId": "cat_courses" },
                      { "id": "tx_8", "date": "%2$s-28", "label": "Virement Employeur", "type": "VIR", "amount": 2500, "categoryId": "cat_salaire" },
                      { "id": "tx_9", "date": "%2$s-15", "label": "Hypermarche", "type": "CB", "amount": -200, "categoryId": "cat_courses",
                        "splits": [
                          { "id": "s1", "categoryId": "cat_courses", "amount": -120, "label": "Alimentation" },
                          { "id": "s2", "categoryId": "cat_loisirs", "amount": -80, "label": "Loisirs" }
                        ] }
                    ],
                    "matchings": [
                      { "month": "%1$s", "links": [
                        { "budgetLineId": "chg_loyer", "txIds": ["tx_1"] },
                        { "budgetLineId": "chg_courses", "txIds": ["tx_2"] },
                        { "budgetLineId": "inc_salaire", "txIds": ["tx_3"] }
                      ]},
                      { "month": "%2$s", "links": [
                        { "budgetLineId": "chg_loyer", "txIds": ["tx_6"] },
                        { "budgetLineId": "chg_courses", "txIds": ["tx_7", "tx_9#s1"] },
                        { "budgetLineId": "inc_salaire", "txIds": ["tx_8"] }
                      ]}
                    ],
                    "pendingOperations": [
                      { "id": "pop_1", "date": "%1$s-20", "label": "Courses Drive", "amount": -80, "type": "cb", "categoryId": "cat_courses", "status": "pending", "budgetLineId": "chg_courses" }
                    ]
                  }
                }
                """.formatted(m0, m1);
    }
}
