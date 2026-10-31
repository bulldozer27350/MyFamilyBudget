package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * VT-100 -- Caracterisation backend des endpoints critiques.
 *
 * <p>Filet de securite explicite avant la separation Maven (voir
 * doc/architecture/16-tests.md et 17-backlog-tests-patchs.md). Les valeurs attendues sont
 * celles produites aujourd'hui par les moteurs a partir du dataset de reference
 * {@code mock-budget.json} (identique a {@code tests/e2e/fixtures/budget-familial.json}).
 * Elles caracterisent le comportement actuel : un ecart doit etre explique, pas "corrige".
 *
 * <p>Complementaire de {@link BusinessLogicIntegrationTest} (non duplique) : celui-ci couvre
 * deja les cashflows 2026/2027, la retraite (trimestres, surcote), le PEA 2026, les prets et
 * le scenario avec pointage. Cette classe ajoute :
 * <ul>
 *   <li>Retraite : contrat JSON complet de la projection ;</li>
 *   <li>Fiscalite : bareme par defaut, parts, impot, taux PAS, coherence avec la tresorerie ;</li>
 *   <li>Tresorerie : cashflow 2027/2028 et horizon de simulation ;</li>
 *   <li>Patrimoine : projection PEA 2027/2028 et totaux ;</li>
 *   <li>Overview : coherence croisee avec Retraite, Tresorerie et Patrimoine ;</li>
 *   <li>Analyse : etat sans import bancaire.</li>
 * </ul>
 * Aucun navigateur ; aucune assertion ne depend de la date du jour (voir taxPreview).
 * Perimetre Prets : deja caracterise par {@code testAnalysePrets_*} (dataset sans pret).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("VT-100 -- Caracterisation des endpoints critiques")
class CriticalEndpointsCharacterizationTest {

    private static final double EUR = 0.01;

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
        // Le fichier de reference peut etre enregistre avec un BOM (Windows) : on l'ignore.
        return json.startsWith("\uFEFF") ? json.substring(1) : json;
    }

    // =========================================================================
    // RETRAITE
    // =========================================================================

    @Nested
    @DisplayName("GET /retraite")
    class Retraite {

        @Test
        @DisplayName("projection d'Alice : contrat JSON complet et valeurs de reference")
        void projectionAlice() throws Exception {
            JsonNode root = getJson("/api/v1/retraite");
            assertThat(root.path("retireYear").asInt()).isEqualTo(2054);

            JsonNode person = root.path("retirement").path("people").get(0);
            assertThat(person.path("id").asText()).isEqualTo("p_1");
            assertThat(person.path("trimestresValides").asInt()).isEqualTo(140);
            assertThat(person.path("agircPoints").asDouble()).isEqualTo(2500.0);

            JsonNode p = person.path("projection");
            assertThat(p.path("ageDepart").asInt()).isEqualTo(64);
            assertThat(p.path("trimestresValides").asInt()).isEqualTo(140);
            assertThat(p.path("trimestresEstimesDepart").asInt()).isEqualTo(252);
            assertThat(p.path("trimestresRequis").asInt()).isEqualTo(172);
            assertThat(p.path("manqueTauxPlein").asBoolean()).isFalse();
            // La cle JSON historique porte un accent : elle fait partie du contrat.
            assertThat(p.path("tauxAppliqu\u00e9").asDouble()).isCloseTo(1.5, within(EUR));
            assertThat(p.path("decote").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(p.path("surcote").asDouble()).isCloseTo(1.0, within(EUR));
            assertThat(p.path("SAM").asDouble()).isCloseTo(41902.56, within(EUR));
            assertThat(p.path("majoration").asDouble()).isCloseTo(1.0, within(EUR));
            assertThat(p.path("pensionBaseAnnuelle").asDouble()).isCloseTo(62853.83, within(EUR));
            assertThat(p.path("pointsEstimes").asDouble()).isCloseTo(8398.90, within(EUR));
            assertThat(p.path("valeurPointDepart").asDouble()).isCloseTo(1.9198, within(0.0001));
            assertThat(p.path("pensionComplementaireAnnuelle").asDouble()).isCloseTo(16124.36, within(EUR));
            assertThat(p.path("pensionTotaleAnnuelle").asDouble()).isCloseTo(78978.19, within(EUR));
            assertThat(p.path("pensionTotaleMensuelle").asDouble()).isCloseTo(6581.52, within(EUR));
        }

        @Test
        @DisplayName("pension totale annuelle = 12 x pension totale mensuelle")
        void pensionAnnualIsTwelveMonths() throws Exception {
            JsonNode p = getJson("/api/v1/retraite").path("retirement").path("people").get(0).path("projection");
            assertThat(p.path("pensionTotaleAnnuelle").asDouble())
                    .isCloseTo(12 * p.path("pensionTotaleMensuelle").asDouble(), within(EUR));
            assertThat(p.path("pensionTotaleAnnuelle").asDouble())
                    .isCloseTo(p.path("pensionBaseAnnuelle").asDouble()
                            + p.path("pensionComplementaireAnnuelle").asDouble(), within(EUR));
        }
    }

    // =========================================================================
    // FISCALITE
    // =========================================================================

    @Nested
    @DisplayName("GET /impots")
    class Fiscalite {

        @Test
        @DisplayName("bareme par defaut applique quand le dataset n'en fournit pas")
        void defaultBrackets() throws Exception {
            JsonNode brackets = getJson("/api/v1/impots").path("taxBrackets");
            assertThat(brackets).hasSize(5);
            double[] upTo = {11294, 28797, 82341, 177106};
            double[] rate = {0.0, 0.11, 0.30, 0.41, 0.45};
            for (int i = 0; i < 5; i++) {
                assertThat(brackets.get(i).path("rate").asDouble()).isCloseTo(rate[i], within(1e-9));
                if (i < 4) {
                    assertThat(brackets.get(i).path("upTo").asDouble()).isCloseTo(upTo[i], within(EUR));
                } else {
                    // Derniere tranche : "upTo" est absent du JSON (et non null).
                    assertThat(brackets.get(i).has("upTo")).isFalse();
                }
            }
            assertThat(getJson("/api/v1/impots").path("taxChildren")).isEmpty();
        }

        @Test
        @DisplayName("settings exposes par /impots refletent le dataset")
        void settingsEcho() throws Exception {
            JsonNode s = getJson("/api/v1/impots").path("settings");
            assertThat(s.path("birthYear").asInt()).isEqualTo(1990);
            assertThat(s.path("retireAge").asInt()).isEqualTo(64);
            assertThat(s.path("childExitAge").asInt()).isEqualTo(21);
            assertThat(s.path("taxAbattement").asDouble()).isCloseTo(0.0, within(1e-9));
        }

        @Test
        @DisplayName("taxPreview : fenetre de 6 ans depuis l'annee courante, valeurs 2026-2028")
        void taxPreviewWindowAndValues() throws Exception {
            JsonNode preview = getJson("/api/v1/impots").path("taxPreview");
            assertThat(preview).hasSize(6);
            int currentYear = Year.now().getValue();
            for (int i = 0; i < preview.size(); i++) {
                assertThat(preview.get(i).path("year").asInt()).isEqualTo(currentYear + i);
                assertThat(preview.get(i).path("parts").asDouble()).isEqualTo(2.0);
            }

            // annee -> {revenu imposable, impot prevu, taux PAS}
            double[][] expected = {
                    {2026, 41000.00, 2025.32, 0.0493980488},
                    {2027, 39996.00, 1914.88, 0.0478767877},
                    {2028, 40395.96, 1958.88, 0.0484919779},
            };
            for (JsonNode row : preview) {
                for (double[] e : expected) {
                    if (row.path("year").asInt() == (int) e[0]) {
                        assertThat(row.path("taxableIncome").asDouble()).isCloseTo(e[1], within(EUR));
                        assertThat(row.path("taxForecast").asDouble()).isCloseTo(e[2], within(EUR));
                        assertThat(row.path("taxActual").asDouble()).isCloseTo(e[2], within(EUR));
                        assertThat(row.path("ratePAS").asDouble()).isCloseTo(e[3], within(1e-6));
                        assertThat(row.path("withheld").asDouble()).isCloseTo(e[2], within(EUR));
                    }
                }
            }
        }

        @Test
        @DisplayName("impot 2026 de /impots = impots du cashflow de /tresorerie")
        void taxMatchesTreasury() throws Exception {
            JsonNode cf = findYear(getJson("/api/v1/tresorerie").path("cashflow"), 2026);
            assertThat(cf.path("impots").asDouble()).isCloseTo(2025.32, within(EUR));
            JsonNode cf2027 = findYear(getJson("/api/v1/tresorerie").path("cashflow"), 2027);
            assertThat(cf2027.path("impots").asDouble()).isCloseTo(1914.88, within(EUR));
        }
    }

    // =========================================================================
    // TRESORERIE
    // =========================================================================

    @Nested
    @DisplayName("GET /tresorerie")
    class Tresorerie {

        @Test
        @DisplayName("horizon de simulation : 2026 -> 2075 (naissance 1990 + 85 ans), une ligne par an")
        void simulationHorizon() throws Exception {
            JsonNode cashflow = getJson("/api/v1/tresorerie").path("cashflow");
            assertThat(cashflow).hasSize(50);
            assertThat(cashflow.get(0).path("year").asInt()).isEqualTo(2026);
            assertThat(cashflow.get(49).path("year").asInt()).isEqualTo(2075);
            for (int i = 1; i < cashflow.size(); i++) {
                assertThat(cashflow.get(i).path("year").asInt())
                        .isEqualTo(cashflow.get(i - 1).path("year").asInt() + 1);
            }
        }

        @Test
        @DisplayName("cashflow 2027 et 2028 : revenus, charges, net et solde cumule")
        void cashflow2027And2028() throws Exception {
            JsonNode cashflow = getJson("/api/v1/tresorerie").path("cashflow");

            JsonNode cf2027 = findYear(cashflow, 2027);
            assertThat(cf2027.path("income").asDouble()).isCloseTo(36360.00, within(EUR));
            assertThat(cf2027.path("variableIncome").asDouble()).isCloseTo(3636.00, within(EUR));
            assertThat(cf2027.path("charges").asDouble()).isCloseTo(11016.00, within(EUR));
            assertThat(cf2027.path("savings").asDouble()).isCloseTo(2400.00, within(EUR));
            assertThat(cf2027.path("oneoff").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(cf2027.path("net").asDouble()).isCloseTo(24665.12, within(EUR));
            assertThat(cf2027.path("balance").asDouble()).isCloseTo(40439.80, within(EUR));

            JsonNode cf2028 = findYear(cashflow, 2028);
            assertThat(cf2028.path("income").asDouble()).isCloseTo(36723.60, within(EUR));
            assertThat(cf2028.path("variableIncome").asDouble()).isCloseTo(3672.36, within(EUR));
            assertThat(cf2028.path("charges").asDouble()).isCloseTo(11236.32, within(EUR));
            assertThat(cf2028.path("net").asDouble()).isCloseTo(24800.76, within(EUR));
            assertThat(cf2028.path("balance").asDouble()).isCloseTo(65240.56, within(EUR));
        }

        @Test
        @DisplayName("solde de chaque annee = solde precedent + net (invariant du cumul)")
        void balanceIsCumulativeNet() throws Exception {
            JsonNode cashflow = getJson("/api/v1/tresorerie").path("cashflow");
            for (int i = 1; i < cashflow.size(); i++) {
                double previous = cashflow.get(i - 1).path("balance").asDouble();
                double net = cashflow.get(i).path("net").asDouble();
                assertThat(cashflow.get(i).path("balance").asDouble())
                        .as("balance %d", cashflow.get(i).path("year").asInt())
                        .isCloseTo(previous + net, within(0.05));
            }
        }
    }

    // =========================================================================
    // PATRIMOINE
    // =========================================================================

    @Nested
    @DisplayName("GET /patrimoine")
    class Patrimoine {

        @Test
        @DisplayName("PEA 2027 et 2028 : pessimiste / corrige / optimiste")
        void peaProjection2027And2028() throws Exception {
            JsonNode pea = peaRows(getJson("/api/v1/patrimoine"));

            JsonNode r2027 = findYear(pea, 2027);
            assertThat(r2027.path("pess").asDouble()).isCloseTo(15252.00, within(EUR));
            assertThat(r2027.path("corr").asDouble()).isCloseTo(15712.00, within(EUR));
            assertThat(r2027.path("opti").asDouble()).isCloseTo(16417.00, within(EUR));

            JsonNode r2028 = findYear(pea, 2028);
            assertThat(r2028.path("pess").asDouble()).isCloseTo(17957.04, within(EUR));
            assertThat(r2028.path("corr").asDouble()).isCloseTo(18740.48, within(EUR));
            assertThat(r2028.path("opti").asDouble()).isCloseTo(19966.19, within(EUR));
        }

        @Test
        @DisplayName("PEA 2054 (annee de retraite) : valeurs de reference")
        void peaAtRetirementYear() throws Exception {
            JsonNode r2054 = findYear(peaRows(getJson("/api/v1/patrimoine")), 2054);
            assertThat(r2054.path("pess").asDouble()).isCloseTo(108459.81, within(EUR));
            assertThat(r2054.path("corr").asDouble()).isCloseTo(155905.60, within(EUR));
            assertThat(r2054.path("opti").asDouble()).isCloseTo(278374.24, within(EUR));
        }

        @Test
        @DisplayName("totaux = PEA tant qu'il n'y a qu'un placement")
        void totalsEqualSinglePlacement() throws Exception {
            JsonNode root = getJson("/api/v1/patrimoine");
            JsonNode pea = peaRows(root);
            JsonNode totals = root.path("patrimoine").path("totals");
            for (int year : new int[] {2026, 2027, 2028, 2054}) {
                JsonNode total = findYear(totals, year);
                JsonNode row = findYear(pea, year);
                assertThat(total.path("pess").asDouble()).isCloseTo(row.path("pess").asDouble(), within(EUR));
                assertThat(total.path("corr").asDouble()).isCloseTo(row.path("corr").asDouble(), within(EUR));
                assertThat(total.path("opti").asDouble()).isCloseTo(row.path("opti").asDouble(), within(EUR));
            }
        }

        private JsonNode peaRows(JsonNode root) {
            for (JsonNode pp : root.path("patrimoine").path("perPlacement")) {
                if ("PEA".equals(pp.path("label").asText())) {
                    return pp.path("rows");
                }
            }
            throw new AssertionError("Placement PEA absent de patrimoine.perPlacement");
        }
    }

    // =========================================================================
    // OVERVIEW (coherence croisee)
    // =========================================================================

    @Nested
    @DisplayName("GET /overview")
    class Overview {

        @Test
        @DisplayName("totalPensions = pension mensuelle d'Alice exposee par /retraite")
        void totalPensionsMatchesRetraite() throws Exception {
            double fromRetraite = getJson("/api/v1/retraite")
                    .path("retirement").path("people").get(0)
                    .path("projection").path("pensionTotaleMensuelle").asDouble();
            JsonNode overview = getJson("/api/v1/overview");
            assertThat(overview.path("totalPensions").asDouble()).isCloseTo(fromRetraite, within(EUR));
            assertThat(overview.path("totalPensions").asDouble()).isCloseTo(6581.52, within(EUR));
            assertThat(overview.path("retireYear").asInt()).isEqualTo(2054);
        }

        @Test
        @DisplayName("cashflow de /overview = cashflow de /tresorerie tant que la personne travaille")
        void cashflowMatchesTreasuryBeforeRetirement() throws Exception {
            JsonNode overviewRoot = getJson("/api/v1/overview");
            int retireYear = overviewRoot.path("retireYear").asInt();
            JsonNode overview = overviewRoot.path("cashflow");
            JsonNode treasury = getJson("/api/v1/tresorerie").path("cashflow");
            assertThat(overview).hasSameSizeAs(treasury);
            for (JsonNode t : treasury) {
                int year = t.path("year").asInt();
                assertThat(overview.get(year - 2026).path("year").asInt()).isEqualTo(year);
                if (year >= retireYear) {
                    continue;
                }
                JsonNode o = findYear(overview, year);
                for (String field : new String[] {"income", "variableIncome", "savings", "charges",
                        "oneoff", "impots", "net", "balance"}) {
                    assertThat(o.path(field).asDouble())
                            .as("%s %d", field, year)
                            .isCloseTo(t.path(field).asDouble(), within(EUR));
                }
            }
        }

        @Test
        @DisplayName("des l'annee de retraite, /overview ajoute les pensions au cashflow, pas /tresorerie")
        void cashflowDivergesFromTreasuryAtRetirement() throws Exception {
            JsonNode overviewRoot = getJson("/api/v1/overview");
            int retireYear = overviewRoot.path("retireYear").asInt();
            assertThat(retireYear).isEqualTo(2054);
            double annualPension = getJson("/api/v1/retraite")
                    .path("retirement").path("people").get(0)
                    .path("projection").path("pensionTotaleAnnuelle").asDouble();

            JsonNode o = findYear(overviewRoot.path("cashflow"), retireYear);
            assertThat(o.path("income").asDouble()).isCloseTo(annualPension, within(EUR));
            assertThat(o.path("income").asDouble()).isCloseTo(78978.19, within(EUR));
            assertThat(o.path("variableIncome").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(o.path("charges").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(o.path("impots").asDouble()).isCloseTo(10265.92, within(EUR));
            assertThat(o.path("net").asDouble()).isCloseTo(68712.27, within(EUR));

            JsonNode t = findYear(getJson("/api/v1/tresorerie").path("cashflow"), retireYear);
            assertThat(t.path("income").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(t.path("impots").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(t.path("balance").asDouble()).isCloseTo(725821.82, within(EUR));
        }

        @Test
        @DisplayName("patrimoine de /overview = projection de /patrimoine (PEA 2028)")
        void patrimoineMatchesPatrimoineEndpoint() throws Exception {
            JsonNode overview = getJson("/api/v1/overview").path("patrimoine").path("totals");
            JsonNode total2028 = findYear(overview, 2028);
            assertThat(total2028.path("corr").asDouble()).isCloseTo(18740.48, within(EUR));
            assertThat(total2028.path("pess").asDouble()).isCloseTo(17957.04, within(EUR));
            assertThat(total2028.path("opti").asDouble()).isCloseTo(19966.19, within(EUR));
        }
    }

    // =========================================================================
    // ANALYSE (sans import bancaire)
    // =========================================================================

    @Nested
    @DisplayName("GET /analyse sans transaction bancaire")
    class Analyse {

        @Test
        @DisplayName("aucune depense ni recette reelle ; les lignes budgetaires restent listees")
        void emptyBankImport() throws Exception {
            JsonNode root = getJson("/api/v1/analyse?monthsBack=12");
            assertThat(root.path("currentMonthISO").asText()).matches("\\d{4}-\\d{2}(-\\d{2})?");
            assertThat(root.path("kpis").path("totalExpenses").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(root.path("kpis").path("totalIncome").asDouble()).isCloseTo(0.0, within(EUR));
            assertThat(root.path("data").path("bankImport").path("transactions")).isEmpty();

            boolean chargeSeen = false;
            boolean incomeSeen = false;
            for (JsonNode row : root.path("landingData")) {
                String id = row.path("id").asText();
                if ("chg_1".equals(id)) {
                    chargeSeen = true;
                    assertThat(row.path("reel").asDouble()).isCloseTo(0.0, within(EUR));
                }
                if ("inc_1".equals(id)) {
                    incomeSeen = true;
                    assertThat(row.path("reel").asDouble()).isCloseTo(0.0, within(EUR));
                }
            }
            assertThat(chargeSeen).as("ligne chg_1 dans landingData").isTrue();
            assertThat(incomeSeen).as("ligne inc_1 dans landingData").isTrue();
        }
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

    private static JsonNode findYear(JsonNode array, int year) {
        for (JsonNode node : array) {
            if (node.path("year").asInt() == year) {
                return node;
            }
        }
        throw new AssertionError("Annee " + year + " absente");
    }
}
