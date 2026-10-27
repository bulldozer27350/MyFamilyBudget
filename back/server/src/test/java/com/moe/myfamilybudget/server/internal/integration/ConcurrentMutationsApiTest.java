package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.treasury.model.ChargeModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;

/**
 * VT-350 -- Deux appels simultanes sur la meme ressource, de bout en bout (HTTP, Spring, H2).
 * Les appels partent en meme temps ; chaque test relit ensuite les charges telles que les sert
 * {@link BudgetReader} (silo Tresorerie, {@code JpaTreasuryStore}) pour prouver l'absence de
 * corruption.
 *
 * <p>R-61-20 : depuis R-20, les lignes de tresorerie ne vivent plus que dans les tables
 * {@code cashflow_*}, lues directement a chaque appel (pas de cache separe de la base) -- la
 * double lecture memoire/base de la version precedente de ce test (via {@code PersistenceManager})
 * n'avait donc plus d'objet : {@code PersistenceManager.getBudgetData()} ne portait plus les
 * charges depuis R-20 (ecrasees par {@code treasuryStore} a chaque lecture) et n'etait jamais mis
 * a jour par la production (aucun appelant reel, voir {@code 26-sequencage-r61-suppression-hub.md}).
 * Chaque test compare desormais l'etat lu via {@link BudgetReader} a celui que sert l'API HTTP, ce
 * qui couvre le meme risque (coherence stockage/API) sans repasser par le hub.
 *
 * <p>Comportement documente : les ecritures sont serialisees, aucune n'est perdue quand elles portent sur des
 * champs ou des lignes differentes ; sur un meme champ la derniere ecriture gagne (l'ordre n'est pas
 * deterministe, la valeur finale est toujours l'une des deux) ; mettre a jour une ligne que l'autre appel
 * supprime est un no-op silencieux (200) ; un import concurrent d'une creation aboutit toujours a l'un des
 * deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vt350;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("VT-350 -- Mutations concurrentes via l'API")
class ConcurrentMutationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetReader budgetReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(chargeIds()).containsExactly("chg_1");
    }

    @Test
    @DisplayName("Creations simultanees : toutes les lignes sont conservees, identifiants distincts, base = API")
    void concurrentCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            requests.add(createCharge("Charge " + i));
        }

        List<MvcResult> results = callConcurrently(requests);

        List<String> createdIds = new ArrayList<>();
        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            createdIds.add(objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText());
        }
        assertThat(createdIds).hasSize(calls).doesNotHaveDuplicates();

        List<String> expected = new ArrayList<>(createdIds);
        expected.add("chg_1");
        assertThat(chargeIds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @DisplayName("Champs differents de la meme ligne : les deux modifications sont appliquees")
    void updatesOfDifferentFieldsOfSameRowAreBothApplied() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                updateCharge("chg_1", "label", "\"Loyer revise\""),
                updateCharge("chg_1", "monthly", "950")));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        ChargeModel charge = charge("chg_1");
        assertThat(charge.label()).isEqualTo("Loyer revise");
        assertThat(charge.monthly()).isEqualByComparingTo("950");
    }

    @RepeatedTest(3)
    @DisplayName("Meme champ de la meme ligne : l'une des deux valeurs gagne, base = API")
    void updatesOfSameFieldEndWithOneOfTheValues() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                updateCharge("chg_1", "monthly", "950"),
                updateCharge("chg_1", "monthly", "1000")));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        ChargeModel stored = charge("chg_1");
        assertThat(stored.monthly().intValue()).isIn(950, 1000);
        assertThat(idsServedByApi()).contains("chg_1");
    }

    @Test
    @DisplayName("Modification et suppression de la meme ligne : la ligne disparait dans tous les cas")
    void updateRacingWithDeleteLeavesTheRowDeleted() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                updateCharge("chg_1", "monthly", "950"),
                deleteCharge("chg_1")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(204);
        assertThat(chargeIds()).isEmpty();
        assertThat(idsServedByApi()).isEmpty();
    }

    @RepeatedTest(3)
    @DisplayName("Import contre creation : l'un des deux ordres sequentiels, base = API")
    void importRacingWithCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                createCharge("Charge concurrente")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(201);
        String createdId = objectMapper.readTree(results.get(1).getResponse().getContentAsString()).path("id").asText();

        // creation puis import : l'import ecrase la creation ; import puis creation : les deux sont presentes.
        List<String> stored = chargeIds();
        assertThat(stored).isIn(List.of("chg_1"), List.of("chg_1", createdId));
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(stored);
    }

    private Callable<MvcResult> createCharge(String label) {
        return () -> mockMvc.perform(post("/api/v1/tresorerie/charges").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"" + label + "\",\"monthly\":10}")).andReturn();
    }

    private Callable<MvcResult> updateCharge(String id, String field, String jsonValue) {
        return () -> mockMvc.perform(put("/api/v1/tresorerie/charges/" + id).contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"field\":\"" + field + "\",\"value\":" + jsonValue + "}")).andReturn();
    }

    private Callable<MvcResult> deleteCharge(String id) {
        return () -> mockMvc.perform(delete("/api/v1/tresorerie/charges/" + id).contextPath("/api/v1")).andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    /** Lance tous les appels au meme instant ; echoue si l'un d'eux leve une exception ou ne se termine pas. */
    private static List<MvcResult> callConcurrently(List<Callable<MvcResult>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : calls) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("tous les threads sont prets").isTrue();
            go.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Identifiants des charges telles que portees par le silo Tresorerie. */
    private List<String> chargeIds() {
        return budgetReader.getCharges().stream().map(ChargeModel::id).toList();
    }

    private ChargeModel charge(String id) {
        return budgetReader.getCharges().stream().filter(c -> id.equals(c.id())).findFirst().orElseThrow();
    }

    /** Identifiants des charges tels que l'API les sert (tresorerie). */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode charge : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("charges")) {
            served.add(charge.path("id").asText());
        }
        return served;
    }
}
