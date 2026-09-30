package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * VT-110 -- Scenario backend transversal Retraite -> Fiscalite -> Tresorerie -> Overview.
 *
 * <p>Valide le graphe de calcul complet a partir du dataset de reference {@code mock-budget.json}
 * et detecte une double implementation de la projection retraite : la pension calculee par
 * {@code /retraite} doit etre exactement celle qui alimente le cashflow, les impots et les KPI de
 * {@code /overview}. Les valeurs figees caracterisent le comportement actuel des moteurs
 * (voir doc/architecture/16-tests.md et 17-backlog-tests-patchs.md).
 *
 * <p>Complementaire de {@link CriticalEndpointsCharacterizationTest} (VT-100), qui verifie chaque
 * endpoint isolement : ici, chaque assertion relie au moins deux endpoints ou deux etapes du
 * graphe. Aucune assertion ne depend de la date du jour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("VT-110 -- Scenario Retraite -> Fiscalite -> Tresorerie -> Overview")
class RetirementToOverviewScenarioTest {

    private static final double EUR = 0.01;
    private static final int FIRST_YEAR = 2026;
    private static final int RETIRE_YEAR = 2054;
    private static final int LAST_YEAR = 2075;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String referenceBudgetJson;

    @BeforeEach
    void resetToReferenceDataset() throws Exception {
        if (referenceBudgetJson == null) {
            referenceBudgetJson = readReferenceDataset();
        }
        mockMvc.perform(post("/api/v1/budget/import")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(referenceBudgetJson))
                .andExpect(status().isOk());
    }

    private static String readReferenceDataset() throws IOException {
        String json = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        return json.startsWith("\uFEFF") ? json.substring(1) : json;
    }

    // =========================================================================
    // ANNEE DE RETRAITE ET PENSION
    // =========================================================================

    @Test
    @DisplayName("annee de retraite identique dans /retraite, /tresorerie et /overview (naissance + age de depart)")
    void retireYearIsConsistentAcrossEndpoints() throws Exception {
        int expected = 1990 + 64;
        assertThat(expected).isEqualTo(RETIRE_YEAR);
        assertThat(getJson("/api/v1/retraite").path("retireYear").asInt()).isEqualTo(expected);
        assertThat(getJson("/api/v1/tresorerie").path("retireYear").asInt()).isEqualTo(expected);
        assertThat(getJson("/api/v1/overview").path("retireYear").asInt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("une seule projection retraite : la pension de /retraite alimente totalPensions et le cashflow de /overview")
    void singleRetirementProjectionFeedsOverview() throws Exception {
        JsonNode projection = pensionProjection();
        double monthly = projection.path("pensionTotaleMensuelle").asDouble();
        double annual = projection.path("pensionTotaleAnnuelle").asDouble();
        assertThat(monthly).isCloseTo(6581.52, within(EUR));
        assertThat(annual).isCloseTo(12 * monthly, within(EUR));

        JsonNode overview = getJson("/api/v1/overview");
        assertThat(overview.path("totalPensions").asDouble()).isCloseTo(monthly, within(EUR));
        JsonNode retireRow = findYear(overview.path("cashflow"), RETIRE_YEAR);
        assertThat(retireRow.path("income").asDouble()).isCloseTo(annual, within(EUR));
        assertThat(retireRow.path("income").asDouble() / 12).isCloseTo(monthly, within(EUR));
    }

    @Test
    @DisplayName("pension du cashflow /overview constante de la retraite a l'horizon (pas d'indexation modelisee)")
    void overviewPensionIsFlatAfterRetirement() throws Exception {
        double annual = pensionProjection().path("pensionTotaleAnnuelle").asDouble();
        JsonNode cashflow = getJson("/api/v1/overview").path("cashflow");
        for (int year = RETIRE_YEAR; year <= LAST_YEAR; year++) {
            JsonNode row = findYear(cashflow, year);
            assertThat(row.path("income").asDouble()).as("income %d", year).isCloseTo(annual, within(EUR));
            assertThat(row.path("variableIncome").asDouble()).as("variableIncome %d", year).isCloseTo(0.0, within(EUR));
            assertThat(row.path("savings").asDouble()).as("savings %d", year).isCloseTo(0.0, within(EUR));
            assertThat(row.path("charges").asDouble()).as("charges %d", year).isCloseTo(0.0, within(EUR));
        }
    }

    // =========================================================================
    // FISCALITE
    // =========================================================================

    @Test
    @DisplayName("impots : /overview = /tresorerie = /impots avant la retraite (2026-2028)")
    void taxIsConsistentBeforeRetirement() throws Exception {
        JsonNode overview = getJson("/api/v1/overview").path("cashflow");
        JsonNode treasury = getJson("/api/v1/tresorerie").path("cashflow");
        double[] expected = {2025.32, 1914.88, 1958.88};
        for (int i = 0; i < expected.length; i++) {
            int year = FIRST_YEAR + i;
            assertThat(findYear(overview, year).path("impots").asDouble()).as("overview %d", year)
                    .isCloseTo(expected[i], within(EUR));
            assertThat(findYear(treasury, year).path("impots").asDouble()).as("tresorerie %d", year)
                    .isCloseTo(expected[i], within(EUR));
        }
        // /impots expose la fenetre glissante depuis l'annee courante : on ne controle que les annees presentes.
        for (JsonNode row : getJson("/api/v1/impots").path("taxPreview")) {
            int year = row.path("year").asInt();
            if (year < RETIRE_YEAR) {
                assertThat(row.path("taxForecast").asDouble()).as("taxPreview %d", year)
                        .isCloseTo(findYear(overview, year).path("impots").asDouble(), within(EUR));
            }
        }
    }

    @Test
    @DisplayName("impots sur pension : imposes des l'annee de retraite dans /overview, croissants ensuite")
    void retirementIncomeIsTaxedInOverview() throws Exception {
        JsonNode overview = getJson("/api/v1/overview").path("cashflow");
        assertThat(findYear(overview, RETIRE_YEAR - 1).path("impots").asDouble()).isCloseTo(3213.88, within(EUR));
        assertThat(findYear(overview, RETIRE_YEAR).path("impots").asDouble()).isCloseTo(10265.92, within(EUR));
        assertThat(findYear(overview, RETIRE_YEAR + 1).path("impots").asDouble()).isCloseTo(10739.78, within(EUR));
        for (int year = RETIRE_YEAR + 1; year <= LAST_YEAR; year++) {
            assertThat(findYear(overview, year).path("impots").asDouble())
                    .as("impots %d > impots %d", year, year - 1)
                    .isGreaterThan(findYear(overview, year - 1).path("impots").asDouble());
        }
    }

    // =========================================================================
    // TRESORERIE
    // =========================================================================

    @Test
    @DisplayName("identite du cashflow : net = revenus + variables - epargne - charges - exceptionnels - impots (les deux endpoints)")
    void netIdentityHoldsEveryYear() throws Exception {
        for (String url : new String[] {"/api/v1/tresorerie", "/api/v1/overview"}) {
            JsonNode cashflow = getJson(url).path("cashflow");
            assertThat(cashflow).as(url).hasSize(LAST_YEAR - FIRST_YEAR + 1);
            for (JsonNode row : cashflow) {
                double expectedNet = row.path("income").asDouble()
                        + row.path("variableIncome").asDouble()
                        - row.path("savings").asDouble()
                        - row.path("charges").asDouble()
                        - row.path("oneoff").asDouble()
                        - row.path("impots").asDouble();
                assertThat(row.path("net").asDouble())
                        .as("%s net %d", url, row.path("year").asInt())
                        .isCloseTo(expectedNet, within(EUR));
            }
        }
    }

    @Test
    @DisplayName("/overview et /tresorerie : cashflow identique avant la retraite, ecart uniquement du aux pensions apres")
    void treasuryAndOverviewDivergeOnlyByPensions() throws Exception {
        JsonNode overview = getJson("/api/v1/overview").path("cashflow");
        JsonNode treasury = getJson("/api/v1/tresorerie").path("cashflow");
        double annual = pensionProjection().path("pensionTotaleAnnuelle").asDouble();

        for (int year = FIRST_YEAR; year < RETIRE_YEAR; year++) {
            assertThat(findYear(overview, year).path("net").asDouble()).as("net %d", year)
                    .isCloseTo(findYear(treasury, year).path("net").asDouble(), within(EUR));
            assertThat(findYear(overview, year).path("balance").asDouble()).as("balance %d", year)
                    .isCloseTo(findYear(treasury, year).path("balance").asDouble(), within(EUR));
        }
        // Comportement actuel : /tresorerie ne projette aucun revenu apres la retraite (solde fige).
        double frozenBalance = findYear(treasury, RETIRE_YEAR - 1).path("balance").asDouble();
        for (int year = RETIRE_YEAR; year <= LAST_YEAR; year++) {
            JsonNode t = findYear(treasury, year);
            assertThat(t.path("income").asDouble()).as("tresorerie income %d", year).isCloseTo(0.0, within(EUR));
            assertThat(t.path("balance").asDouble()).as("tresorerie balance %d", year)
                    .isCloseTo(frozenBalance, within(EUR));
        }
        // /overview : les pensions nettes d'impots alimentent le solde cumule.
        JsonNode retireRow = findYear(overview, RETIRE_YEAR);
        assertThat(retireRow.path("net").asDouble()).isCloseTo(annual - 10265.92, within(EUR));
        assertThat(retireRow.path("balance").asDouble()).isCloseTo(794534.09, within(EUR));
        assertThat(findYear(overview, LAST_YEAR).path("balance").asDouble()).isCloseTo(2111940.50, within(EUR));
    }

    // =========================================================================
    // KPI OVERVIEW
    // =========================================================================

    @Test
    @DisplayName("KPI de depart : patrimoineActuel, fluxNetActuel = net de la premiere annee, retireCharges nul")
    void currentKpis() throws Exception {
        JsonNode overview = getJson("/api/v1/overview");
        assertThat(overview.path("patrimoineActuel").asDouble()).isCloseTo(10000.0, within(EUR));
        assertThat(overview.path("pivotBalance").asDouble()).isCloseTo(5000.0, within(EUR));
        assertThat(overview.path("fluxNetActuel").asDouble()).isCloseTo(10774.68, within(EUR));
        assertThat(overview.path("fluxNetActuel").asDouble())
                .isCloseTo(findYear(overview.path("cashflow"), FIRST_YEAR).path("net").asDouble(), within(EUR));
        assertThat(overview.path("retireCharges").asDouble()).isCloseTo(0.0, within(EUR));
    }

    @Test
    @DisplayName("patrimoine a la retraite = placements 2054 (/overview.patrimoine) + immobilier reevalue a 1,5 %/an")
    void retirePatrimoineIsFinancialPlusRealEstate() throws Exception {
        JsonNode overview = getJson("/api/v1/overview");
        JsonNode total = findYear(overview.path("patrimoine").path("totals"), RETIRE_YEAR);
        double realEstate = 250000 * Math.pow(1.015, RETIRE_YEAR - 2026);
        assertThat(realEstate).isCloseTo(379305.55, within(EUR));

        JsonNode retire = overview.path("retirePatrimoine");
        assertThat(retire.path("pess").asDouble()).isCloseTo(total.path("pess").asDouble() + realEstate, within(EUR));
        assertThat(retire.path("corr").asDouble()).isCloseTo(total.path("corr").asDouble() + realEstate, within(EUR));
        assertThat(retire.path("opti").asDouble()).isCloseTo(total.path("opti").asDouble() + realEstate, within(EUR));
        assertThat(retire.path("pess").asDouble()).isCloseTo(487765.35, within(EUR));
        assertThat(retire.path("corr").asDouble()).isCloseTo(535211.15, within(EUR));
        assertThat(retire.path("opti").asDouble()).isCloseTo(657679.79, within(EUR));
    }

    @Test
    @DisplayName("regle des 4 % : rente mensuelle = patrimoine / 300 (avec et sans immobilier)")
    void fourPercentRuleRelatesPatrimoineToRente() throws Exception {
        JsonNode overview = getJson("/api/v1/overview");
        JsonNode retire = overview.path("retirePatrimoine");
        JsonNode fire = overview.path("fireRente");
        JsonNode financialOnly = overview.path("financialOnlyRente");
        JsonNode total = findYear(overview.path("patrimoine").path("totals"), RETIRE_YEAR);

        for (String scenario : new String[] {"pess", "corr", "opti"}) {
            assertThat(fire.path(scenario).asDouble()).as("fireRente %s", scenario)
                    .isCloseTo(retire.path(scenario).asDouble() / 300, within(EUR));
            assertThat(financialOnly.path(scenario).asDouble()).as("financialOnlyRente %s", scenario)
                    .isCloseTo(total.path(scenario).asDouble() / 300, within(EUR));
        }
        assertThat(fire.path("corr").asDouble()).isCloseTo(1784.04, within(EUR));
        assertThat(financialOnly.path("corr").asDouble()).isCloseTo(519.69, within(EUR));
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private JsonNode pensionProjection() throws Exception {
        return getJson("/api/v1/retraite").path("retirement").path("people").get(0).path("projection");
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mockMvc.perform(get(url).contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    private static JsonNode findYear(JsonNode array, int year) {
        for (JsonNode node : array) {
            if (node.path("year").asInt() == year) {
                return node;
            }
        }
        throw new AssertionError("Annee " + year + " absente");
    }
}
