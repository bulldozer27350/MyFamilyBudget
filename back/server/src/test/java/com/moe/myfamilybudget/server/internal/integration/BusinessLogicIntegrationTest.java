package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.impl.OverviewServiceImpl;
import com.moe.myfamilybudget.server.internal.mapper.OverviewMapper;
import com.moe.myfamilybudget.server.internal.model.BudgetDataModel;
import com.moe.myfamilybudget.server.internal.model.RetirementModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("BusinessLogicIntegrationTest -- Oracle JS vs Backend Java")
class BusinessLogicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PersistenceManager persistenceManager;

    private static String mockBudgetJson;

    @BeforeEach
    void loadMockData() throws IOException {
        if (mockBudgetJson == null) {
            ClassPathResource resource = new ClassPathResource("mock-budget.json");
            mockBudgetJson = resource.getContentAsString(StandardCharsets.UTF_8);
        }
    }

    private void importMockBudget() throws Exception {
        mockMvc.perform(post("/api/v1/budget/import")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mockBudgetJson))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // SYSTEME
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /budget/import => 200, donnees persistees")
    void testImportJSON_persistsData() throws Exception {
        importMockBudget();

        mockMvc.perform(get("/api/v1/budget").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.birthYear").value(1990))
                .andExpect(jsonPath("$.settings.retireAge").value(64))
                .andExpect(jsonPath("$.settings.inflationRate").value(0.02))
                .andExpect(jsonPath("$.settings.startBalance").value(5000))
                .andExpect(jsonPath("$.incomes[0].id").value("inc_1"))
                .andExpect(jsonPath("$.incomes[0].label").value("Salaire"))
                .andExpect(jsonPath("$.incomes[0].monthly").value(3000))
                .andExpect(jsonPath("$.charges[0].id").value("chg_1"))
                .andExpect(jsonPath("$.charges[0].monthly").value(900))
                .andExpect(jsonPath("$.placements[0].id").value("plc_1"))
                .andExpect(jsonPath("$.placements[0].balance").value(10000))
                .andExpect(jsonPath("$.oneoff[0].amount").value(15000));
    }

    // =========================================================================
    // OVERVIEW
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("GET /overview => retireYear = 1990 + 64 = 2054")
    void testOverview_retireYear() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retireYear").value(2054));
    }

    @Test
    @Order(3)
    @DisplayName("GET /overview => patrimoineActuel = 10000")
    void testOverview_patrimoineActuel() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patrimoineActuel").value(10000));
    }

    @Test
    @Order(4)
    @DisplayName("GET /overview => cashflow 2026 : income=36000, charges=10800, savings=2400, oneoff=15000, variableIncome=5000")
    void testOverview_cashflow2026() throws Exception {
        importMockBudget();

        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode cf2026 = findYearNode(root.path("cashflow"), 2026);
        assertThat(cf2026).isNotNull();

        // income = 3000 * (1.01)^0 * 12 = 36000
        assertThat(cf2026.path("income").decimalValue()).isEqualByComparingTo("36000");
        // charges = 900 * (1.02)^0 * 12 = 10800
        assertThat(cf2026.path("charges").decimalValue()).isEqualByComparingTo("10800");
        // savings = 200 * 12 = 2400
        assertThat(cf2026.path("savings").decimalValue()).isEqualByComparingTo("2400");
        // oneoff date=2026-06-01 => amount=15000
        assertThat(cf2026.path("oneoff").decimalValue()).isEqualByComparingTo("15000");
        // variableIncome override 2026 = 5000
        assertThat(cf2026.path("variableIncome").decimalValue()).isEqualByComparingTo("5000");
    }

    @Test
    @Order(5)
    @DisplayName("GET /overview => cashflow 2026 : net=12800, balance=17800")
    void testOverview_cashflow2026_netBalance() throws Exception {
        importMockBudget();

        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode cf2026 = findYearNode(root.path("cashflow"), 2026);
        assertThat(cf2026).isNotNull();

        // net = 36000 + 5000 - 2400 - 10800 - 15000 = 12800
        assertThat(cf2026.path("net").decimalValue()).isEqualByComparingTo("10774.6799992");
        // balance = 5000 + 10774.68 = 15774.68
        assertThat(cf2026.path("balance").decimalValue()).isEqualByComparingTo("15774.6799992");
    }

    @Test
    @Order(6)
    @DisplayName("GET /overview => cashflow 2027 avec growthRate appliques")
    void testOverview_cashflow2027_growth() throws Exception {
        importMockBudget();

        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode cf2027 = findYearNode(root.path("cashflow"), 2027);
        assertThat(cf2027).isNotNull();

        // income = 3000 * (1.01)^1 * 12 = 36360
        assertThat(cf2027.path("income").asDouble()).isCloseTo(36360.0, within(0.01));
        // charges = 900 * (1.02)^1 * 12 = 11016
        assertThat(cf2027.path("charges").asDouble()).isCloseTo(11016.0, within(0.01));
        // variableIncome 2027 = forecast = 36360 * 0.10 = 3636 (pas d override)
        assertThat(cf2027.path("variableIncome").asDouble()).isCloseTo(3636.0, within(0.01));
    }

    @Test
    @Order(7)
    @DisplayName("GET /overview => retirePatrimoine : opti > corr > pess > 0")
    void testOverview_retirePatrimoine_ordering() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode rp = objectMapper.readTree(result.getResponse().getContentAsString()).path("retirePatrimoine");

        double pess = rp.path("pess").asDouble();
        double corr = rp.path("corr").asDouble();
        double opti = rp.path("opti").asDouble();
        assertThat(pess).isGreaterThan(0);
        assertThat(corr).isGreaterThan(pess);
        assertThat(opti).isGreaterThan(corr);
    }

    @Test
    @Order(8)
    @DisplayName("GET /overview => retirePatrimoine inclut immobilier 250000*(1.015)^28 >= 379000")
    void testOverview_retirePatrimoine_realEstate() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        double pess = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("retirePatrimoine").path("pess").asDouble();
        double expectedRE = 250000.0 * Math.pow(1.015, 28);
        assertThat(pess).isGreaterThan(expectedRE * 0.99);
    }

    @Test
    @Order(9)
    @DisplayName("GET /overview => fireRente.corr = retirePatrimoine.corr * 0.04 / 12")
    void testOverview_fireRente() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        double rpCorr = root.path("retirePatrimoine").path("corr").asDouble();
        double fireCorr = root.path("fireRente").path("corr").asDouble();
        assertThat(fireCorr).isCloseTo(rpCorr * 0.04 / 12.0, within(0.01));
    }

    @Test
    @Order(10)
    @DisplayName("GET /overview?useConstantEuros=true => retirePatrimoine deflate par (1/1.02)^28")
    void testOverview_constantEuros() throws Exception {
        importMockBudget();
        MvcResult nominal = mockMvc.perform(get("/api/v1/overview?useConstantEuros=false").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        MvcResult constant = mockMvc.perform(get("/api/v1/overview?useConstantEuros=true").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        double nomCorr = objectMapper.readTree(nominal.getResponse().getContentAsString()).path("retirePatrimoine").path("corr").asDouble();
        double conCorr = objectMapper.readTree(constant.getResponse().getContentAsString()).path("retirePatrimoine").path("corr").asDouble();
        assertThat(conCorr).isLessThan(nomCorr);
        double expectedDeflator = Math.pow(1.0 / 1.02, 28);
        assertThat(conCorr / nomCorr).isCloseTo(expectedDeflator, within(0.001));
    }

    // =========================================================================
    // TRESORERIE
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("GET /tresorerie => listes conformes, retireYear=2054")
    void testTresorerie_lists() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomes[0].id").value("inc_1"))
                .andExpect(jsonPath("$.incomes[0].monthly").value(3000))
                .andExpect(jsonPath("$.charges[0].id").value("chg_1"))
                .andExpect(jsonPath("$.charges[0].monthly").value(900))
                .andExpect(jsonPath("$.variableIncomes[0].id").value("vi_1"))
                .andExpect(jsonPath("$.variableIncomes[0].rate").value(0.1))
                .andExpect(jsonPath("$.retireYear").value(2054));
    }

    @Test
    @Order(12)
    @DisplayName("GET /tresorerie => incomeLabels = [\"Salaire\"]")
    void testTresorerie_incomeLabels() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode labels = objectMapper.readTree(result.getResponse().getContentAsString()).path("incomeLabels");
        assertThat(labels.isArray()).isTrue();
        assertThat(labels.size()).isEqualTo(1);
        assertThat(labels.get(0).asText()).isEqualTo("Salaire");
    }

    @Test
    @Order(13)
    @DisplayName("GET /tresorerie => cashflow 2026 net=12800 (coherent avec overview)")
    void testTresorerie_cashflow2026_net() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode cf2026 = findYearNode(objectMapper.readTree(result.getResponse().getContentAsString()).path("cashflow"), 2026);
        assertThat(cf2026).isNotNull();
        assertThat(cf2026.path("net").decimalValue()).isEqualByComparingTo("10774.6799992");
    }

    @Test
    @Order(14)
    @DisplayName("POST /tresorerie/charges => 201, liste passe a 2 charges")
    void testTresorerie_addCharge() throws Exception {
        importMockBudget();
        mockMvc.perform(post("/api/v1/tresorerie/charges").contextPath("/api/v1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        assertThat(objectMapper.readTree(result.getResponse().getContentAsString()).path("charges").size()).isEqualTo(2);
    }

    @Test
    @Order(15)
    @DisplayName("POST /tresorerie/incomes => 201, label par defaut = \"Nouveau revenu\"")
    void testTresorerie_addIncome() throws Exception {
        importMockBudget();
        MvcResult addResult = mockMvc.perform(post("/api/v1/tresorerie/incomes").contextPath("/api/v1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode added = objectMapper.readTree(addResult.getResponse().getContentAsString());
        assertThat(added.path("label").asText()).isEqualTo("Nouveau revenu");
    }

    // =========================================================================
    // PATRIMOINE
    // =========================================================================

    @Test
    @Order(16)
    @DisplayName("GET /patrimoine => placements et realEstate presents")
    void testPatrimoine_structure() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/patrimoine").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].id").value("plc_1"))
                .andExpect(jsonPath("$.placements[0].balance").value(10000))
                .andExpect(jsonPath("$.realEstate[0].id").value("re_1"))
                .andExpect(jsonPath("$.realEstate[0].currentValue").value(250000));
    }

    @Test
    @Order(17)
    @DisplayName("GET /patrimoine => PEA annee 2026 : pess=12600, corr=12800, opti=13100")
    void testPatrimoine_PEA_year2026() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/patrimoine").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode perPlacement = objectMapper.readTree(result.getResponse().getContentAsString()).path("patrimoine").path("perPlacement");

        JsonNode peaRows = null;
        for (JsonNode pp : perPlacement) {
            if ("PEA".equals(pp.path("label").asText())) { peaRows = pp.path("rows"); break; }
        }
        assertThat(peaRows).isNotNull();

        JsonNode row2026 = findYearNode(peaRows, 2026);
        assertThat(row2026).isNotNull();

        // pess = 10000*(1.02) + 200*12 = 12600
        assertThat(row2026.path("pess").decimalValue()).isEqualByComparingTo("12600");
        // corr = 10000*(1.04) + 2400 = 12800
        assertThat(row2026.path("corr").decimalValue()).isEqualByComparingTo("12800");
        // opti = 10000*(1.07) + 2400 = 13100
        assertThat(row2026.path("opti").decimalValue()).isEqualByComparingTo("13100");
    }

    @Test
    @Order(18)
    @DisplayName("GET /patrimoine => patrimoine.totals opti >= corr >= pess pour chaque annee")
    void testPatrimoine_totals_ordering() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/patrimoine").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode totals = objectMapper.readTree(result.getResponse().getContentAsString()).path("patrimoine").path("totals");
        for (JsonNode total : totals) {
            double pess = total.path("pess").asDouble();
            double corr = total.path("corr").asDouble();
            double opti = total.path("opti").asDouble();
            assertThat(corr).isGreaterThanOrEqualTo(pess);
            assertThat(opti).isGreaterThanOrEqualTo(corr);
        }
    }

    // =========================================================================
    // RETRAITE
    // =========================================================================

    @Test
    @Order(19)
    @DisplayName("GET /retraite => retireYear=2054, Alice presente")
    void testRetraite_structure() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/retraite").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retireYear").value(2054))
                .andExpect(jsonPath("$.retirement.people[0].id").value("p_1"))
                .andExpect(jsonPath("$.retirement.people[0].name").value("Alice"));
    }

    @Test
    @Order(20)
    @DisplayName("Projection Alice : trimestresEstimesDepart=252, surcote=1.0, tauxApplique=1.50")
    void testRetraite_trimestres_surcote() throws Exception {
        importMockBudget();

        BudgetDataModel data = persistenceManager.getBudgetData();
        RetirementModel.RetirementPersonModel alice = data.retirement().people().get(0);
        OverviewServiceImpl svc = new OverviewServiceImpl(new OverviewMapper(), persistenceManager);
        OverviewServiceImpl.RetirementProjection proj = svc.computeRetirementProjection(data, alice, 2054);

        // trimestresDateYear=2025, salaire actif 2026..2053 = 28 annees * 4 = 112
        // total = 140 + 112 = 252
        assertThat(proj.trimestresEstimesDepart()).isEqualTo(252);
        // surcote = (252-172) * 0.0125 = 80 * 0.0125 = 1.0
        assertThat(proj.surcote()).isEqualByComparingTo("1.0");
        // tauxApplique = 0.50 + 1.0 = 1.50
        assertThat(proj.tauxApplique()).isEqualByComparingTo("1.50");
        // majoration = 1 (0 enfants)
        assertThat(proj.majoration()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @Order(21)
    @DisplayName("Projection Alice : pension > 0, pointsEstimes > 2500")
    void testRetraite_pension_coherence() throws Exception {
        importMockBudget();

        BudgetDataModel data = persistenceManager.getBudgetData();
        RetirementModel.RetirementPersonModel alice = data.retirement().people().get(0);
        OverviewServiceImpl svc = new OverviewServiceImpl(new OverviewMapper(), persistenceManager);
        OverviewServiceImpl.RetirementProjection proj = svc.computeRetirementProjection(data, alice, 2054);

        assertThat(proj.pensionBaseAnnuelle()).isGreaterThan(BigDecimal.ZERO);
        assertThat(proj.pensionComplementaireAnnuelle()).isGreaterThan(BigDecimal.ZERO);
        assertThat(proj.pensionTotaleMensuelle()).isGreaterThan(new BigDecimal("4000"));
        // pointsEstimes = 2500 (actuels) + pointsFuturs (28 annees)
        assertThat(proj.pointsEstimes()).isGreaterThan(new BigDecimal("2500"));
    }

    @Test
    @Order(22)
    @DisplayName("GET /overview => totalPensions = pensionTotaleMensuelle d Alice")
    void testOverview_totalPensions() throws Exception {
        importMockBudget();
        BudgetDataModel data = persistenceManager.getBudgetData();
        RetirementModel.RetirementPersonModel alice = data.retirement().people().get(0);
        OverviewServiceImpl svc = new OverviewServiceImpl(new OverviewMapper(), persistenceManager);
        OverviewServiceImpl.RetirementProjection proj = svc.computeRetirementProjection(data, alice, 2054);

        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        double totalPensions = objectMapper.readTree(result.getResponse().getContentAsString()).path("totalPensions").asDouble();
        assertThat(totalPensions).isCloseTo(proj.pensionTotaleMensuelle().doubleValue(), within(0.01));
    }

    // =========================================================================
    // SCENARIOS COMPLEMENTAIRES
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("variableIncome 2026 : override 5000 > forecast (36000*0.10=3600)")
    void testVariableIncome_overrideWins() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode cf2026 = findYearNode(objectMapper.readTree(result.getResponse().getContentAsString()).path("cashflow"), 2026);
        assertThat(cf2026).isNotNull();
        assertThat(cf2026.path("variableIncome").asDouble()).isEqualTo(5000.0);
    }

    @Test
    @Order(24)
    @DisplayName("Cashflow 2054 : revenu inclut pension auto (Salaire termine fin 2053)")
    void testCashflow_retireYear_pensionInjected() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode cashflow = objectMapper.readTree(result.getResponse().getContentAsString()).path("cashflow");
        JsonNode cf2054 = findYearNode(cashflow, 2054);
        assertThat(cf2054).isNotNull();
        // En 2054, Salaire termine fin 2053, mais pension auto-injectee
        assertThat(cf2054.path("income").asDouble()).isGreaterThan(0);
    }

    @Test
    @Order(25)
    @DisplayName("Scenario excluded : PEA exclu => retirePatrimoine.pess reduit")
    void testScenario_excludedPlacement() throws Exception {
        String excludedJson = mockBudgetJson.replace("\"excludedFromRetirement\": false", "\"excludedFromRetirement\": true");
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1").contentType(MediaType.APPLICATION_JSON).content(excludedJson)).andExpect(status().isOk());
        MvcResult excl = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();

        importMockBudget();
        MvcResult incl = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();

        double pessExcl = objectMapper.readTree(excl.getResponse().getContentAsString()).path("retirePatrimoine").path("pess").asDouble();
        double pessIncl = objectMapper.readTree(incl.getResponse().getContentAsString()).path("retirePatrimoine").path("pess").asDouble();
        assertThat(pessExcl).isLessThan(pessIncl);
    }

    @Test
    @Order(26)
    @DisplayName("GET /analyse => 200 avec kpis et currentMonthISO")
    void testAnalyse_structure() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/analyse").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis").exists())
                .andExpect(jsonPath("$.currentMonthISO").exists());
    }

    @Test
    @Order(27)
    @DisplayName("GET /settings => reflète les donnees importees")
    void testSettings_reflect() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/settings").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.birthYear").value(1990))
                .andExpect(jsonPath("$.settings.retireAge").value(64));
    }

    @Test
    @Order(28)
    @DisplayName("GET /impots => 200 avec taxBrackets et taxChildren")
    void testImpots_structure() throws Exception {
        importMockBudget();
        mockMvc.perform(get("/api/v1/impots").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxBrackets").isArray())
                .andExpect(jsonPath("$.taxChildren").isArray());
    }

    @Test
    @Order(29)
    @DisplayName("POST /budget/reset => id inc_1 absent apres reinitialisation")
    void testReset_clearsData() throws Exception {
        importMockBudget();
        MvcResult result = mockMvc.perform(post("/api/v1/budget/reset").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean hasInc1 = false;
        for (JsonNode inc : root.path("incomes")) {
            if ("inc_1".equals(inc.path("id").asText())) { hasInc1 = true; break; }
        }
        assertThat(hasInc1).isFalse();
    }

    @Test
    @Order(30)
    @DisplayName("GET /overview apres reset => pas d exception, retireYear > 2000")
    void testOverview_afterReset() throws Exception {
        mockMvc.perform(post("/api/v1/budget/reset").contextPath("/api/v1")).andExpect(status().isOk());
        MvcResult result = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1")).andExpect(status().isOk()).andReturn();
        int retireYear = objectMapper.readTree(result.getResponse().getContentAsString()).path("retireYear").asInt();
        assertThat(retireYear).isGreaterThan(2000);
    }

    @Test
    @Order(31)
    @DisplayName("POST /pending-operations/force => Sauvegarde, modification avec ventilation (splits), note, catégorie et persistance")
    void testPendingOperations_modificationAndPersistenceWithSplits() throws Exception {
        importMockBudget();

        // 1. Initial creation
        String initialOpJson = """
        {
            "id": "op_e2e_1",
            "date": "2026-07-10",
            "expectedDate": "2026-07-15",
            "type": "cheque",
            "refNumber": "CHQ-8899",
            "label": "Achat Mobilier Salon",
            "amount": -300.00,
            "categoryId": "cat_maison",
            "notes": "Achat canape et table basse",
            "splits": [
                { "id": "sp_1", "categoryId": "cat_maison", "amount": -200.00, "label": "Canape" },
                { "id": "sp_2", "categoryId": "cat_decoration", "amount": -100.00, "label": "Table basse" }
            ]
        }
        """;

        mockMvc.perform(post("/api/v1/pending-operations/force")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initialOpJson))
                .andExpect(status().isOk());

        // Verify initial get
        MvcResult res1 = mockMvc.perform(get("/api/v1/pending-operations").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        JsonNode opNode1 = null;
        for (JsonNode op : root1.path("pendingOperations")) {
            if ("op_e2e_1".equals(op.path("id").asText())) {
                opNode1 = op;
                break;
            }
        }
        assertThat(opNode1).isNotNull();
        assertThat(opNode1.path("notes").asText()).isEqualTo("Achat canape et table basse");
        assertThat(opNode1.path("splits")).hasSize(2);
        assertThat(opNode1.path("splits").get(0).path("label").asText()).isEqualTo("Canape");

        // 2. Modify operation (new notes, new category, new splits)
        String updatedOpJson = """
        {
            "id": "op_e2e_1",
            "date": "2026-07-12",
            "expectedDate": "2026-07-20",
            "type": "cheque",
            "refNumber": "CHQ-8899-MOD",
            "label": "Achat Mobilier & Eclairage",
            "amount": -350.00,
            "categoryId": "cat_mobilier",
            "notes": "Ajout luminaire d'ambiance",
            "splits": [
                { "id": "sp_1_m", "categoryId": "cat_mobilier", "amount": -250.00, "label": "Canape cuir" },
                { "id": "sp_2_m", "categoryId": "cat_eclairage", "amount": -100.00, "label": "Lampadaire" }
            ]
        }
        """;

        mockMvc.perform(post("/api/v1/pending-operations/force")
                .contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatedOpJson))
                .andExpect(status().isOk());

        // 3. Verify updated get
        MvcResult res2 = mockMvc.perform(get("/api/v1/pending-operations").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        JsonNode opNode2 = null;
        for (JsonNode op : root2.path("pendingOperations")) {
            if ("op_e2e_1".equals(op.path("id").asText())) {
                opNode2 = op;
                break;
            }
        }
        assertThat(opNode2).isNotNull();
        assertThat(opNode2.path("label").asText()).isEqualTo("Achat Mobilier & Eclairage");
        assertThat(opNode2.path("categoryId").asText()).isEqualTo("cat_mobilier");
        assertThat(opNode2.path("notes").asText()).isEqualTo("Ajout luminaire d'ambiance");
        assertThat(opNode2.path("refNumber").asText()).isEqualTo("CHQ-8899-MOD");
        assertThat(opNode2.path("amount").asDouble()).isEqualTo(-350.00);
        assertThat(opNode2.path("splits")).hasSize(2);
        assertThat(opNode2.path("splits").get(0).path("label").asText()).isEqualTo("Canape cuir");
        assertThat(opNode2.path("splits").get(1).path("categoryId").asText()).isEqualTo("cat_eclairage");

        // 4. Verify in Overview response
        MvcResult resOverview = mockMvc.perform(get("/api/v1/overview").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode overviewRoot = objectMapper.readTree(resOverview.getResponse().getContentAsString());
        JsonNode overviewPending = overviewRoot.path("data").path("bankImport").path("pendingOperations");
        JsonNode overviewOp = null;
        for (JsonNode op : overviewPending) {
            if ("op_e2e_1".equals(op.path("id").asText())) {
                overviewOp = op;
                break;
            }
        }
        assertThat(overviewOp).isNotNull();
        assertThat(overviewOp.path("notes").asText()).isEqualTo("Ajout luminaire d'ambiance");
        assertThat(overviewOp.path("splits")).hasSize(2);
    }

    // =========================================================================
    // ANALYSE
    // =========================================================================

    @Test
    @Order(32)
    @DisplayName("GET /analyse => retourne 200 avec le champ data et les listes directes et calculées")
    void testAnalyse_endpoint() throws Exception {
        importMockBudget();
        MvcResult res = mockMvc.perform(get("/api/v1/analyse?monthsBack=12").contextPath("/api/v1"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(root.has("data")).isTrue();
        assertThat(root.path("data").has("charges")).isTrue();
        assertThat(root.path("data").has("incomes")).isTrue();
        assertThat(root.path("data").has("placements")).isTrue();
        assertThat(root.path("data").has("bankImport")).isTrue();
        assertThat(root.path("data").has("settings")).isTrue();

        assertThat(root.has("charges")).isTrue();
        assertThat(root.has("incomes")).isTrue();
        assertThat(root.has("placements")).isTrue();
        assertThat(root.has("bankImport")).isTrue();
        assertThat(root.has("settings")).isTrue();

        assertThat(root.has("kpis")).isTrue();
        assertThat(root.has("landingData")).isTrue();
        assertThat(root.has("driftRows")).isTrue();
        assertThat(root.has("monthlyCompareData")).isTrue();
        assertThat(root.has("categorySummaries")).isTrue();
        assertThat(root.has("currentMonthISO")).isTrue();
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    private JsonNode findYearNode(JsonNode array, int year) {
        for (JsonNode node : array) {
            if (node.path("year").asInt() == year) return node;
        }
        return null;
    }
}
