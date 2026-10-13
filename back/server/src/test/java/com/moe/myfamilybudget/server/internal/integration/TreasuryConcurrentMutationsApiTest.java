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
import com.moe.myfamilybudget.domain.treasury.model.IncomeModel;
import com.moe.myfamilybudget.domain.treasury.port.BudgetReader;

/**
 * R-22 (24-backlog-reste-a-faire.md, Tresorerie lot B3 ; ref. 21 : SILO-216 B2) -- Ecritures concurrentes sur
 * le silo Tresorerie, de bout en bout (HTTP, Spring, H2). Depuis le lot B1 (R-20), les revenus s'ecrivent
 * directement dans {@code cashflow_income} ({@code JpaTreasuryStore}) : la serialisation des ecritures repose
 * sur le seul verrou de silo ({@code MutationSilo.TREASURY}) pris par {@code TresorerieCommandService}. Chaque
 * test relit la base sans passer par un cache. Le round-trip H2 est couvert par
 * {@code PersistenceAdaptersJpaRoundTripTest} (DB-1061) ; le redemarrage (H2 et PostgreSQL) est couvert par
 * {@code RestartPersistenceTest} (charge {@code chg_1} et revenu cree/renomme).
 *
 * <p>Comportement documente : une creation concurrente de revenus distincts ne perd aucune ligne ; deux mises a
 * jour simultanees de la meme ligne laissent une seule ligne, avec l'une des deux valeurs (pas de melange) ;
 * une mise a jour contre une suppression de la meme ligne aboutit toujours a l'absence de la ligne ({@code
 * updateTresorerieRow} ne recree pas une ligne supprimee, contrairement a {@code saveLoanRow} pour le Credit) ;
 * un import contre une creation aboutit a l'un des deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference ({@code mock-budget.json}) porte un revenu ({@code inc_1}), present apres chaque import.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo216treasury;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("R-22 (SILO-216 lot B2) -- Mutations concurrentes sur la tresorerie")
class TreasuryConcurrentMutationsApiTest {

    private static final String REFERENCE_INCOME_ID = "inc_1";

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
        assertThat(ids(budgetReader.getIncomes())).as("l'import ne porte que le revenu de reference")
                .containsExactly(REFERENCE_INCOME_ID);
    }

    @Test
    @DisplayName("Creations simultanees : tous les revenus sont conserves, API = base")
    void concurrentCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        expected.add(REFERENCE_INCOME_ID);
        for (int i = 0; i < calls; i++) {
            String id = "inc_c" + i;
            expected.add(id);
            requests.add(addIncome(incomeBody(id, "Revenu " + i, 1000 + i)));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));

        assertThat(ids(budgetReader.getIncomes())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Meme revenu modifie deux fois en meme temps : une seule ligne, l'une des deux valeurs")
    void concurrentEditsOfSameIncomeLeaveOneRow() throws Exception {
        callConcurrently(List.of(addIncome(incomeBody("inc_x", "Initial", 1000)))).forEach(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(201));

        List<MvcResult> results = callConcurrently(List.of(
                updateIncomeLabel("inc_x", "Version 1"),
                updateIncomeLabel("inc_x", "Version 2")));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<IncomeModel> stored = budgetReader.getIncomes().stream()
                .filter(income -> "inc_x".equals(income.id())).toList();
        assertThat(stored).singleElement().satisfies(income ->
                assertThat(income.label()).isIn("Version 1", "Version 2"));
        assertThat(idsServedByApi()).contains("inc_x");
    }

    @RepeatedTest(3)
    @DisplayName("Mise a jour contre suppression du meme revenu : la ligne est toujours absente ensuite")
    void updateRacingWithDeleteAlwaysEndsRemoved() throws Exception {
        callConcurrently(List.of(addIncome(incomeBody("inc_y", "Initial", 1000)))).forEach(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(201));

        List<MvcResult> results = callConcurrently(List.of(
                updateIncomeLabel("inc_y", "Revise"),
                deleteIncome("inc_y")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(204);
        // updateTresorerieRow ne recree pas une ligne absente : que la suppression passe avant ou apres la
        // mise a jour, le revenu inc_y n'existe plus a la fin (DA-09 : lecture-modification-ecriture sous verrou).
        assertThat(ids(budgetReader.getIncomes())).doesNotContain("inc_y");
        assertThat(idsServedByApi()).doesNotContain("inc_y");
    }

    @RepeatedTest(3)
    @DisplayName("Import contre creation d'un revenu : l'un des deux ordres sequentiels")
    void importRacingWithCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                addIncome(incomeBody("inc_race", "Concurrent", 1000))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(201);
        // creation puis import : l'import remet les revenus a l'etat de reference (inc_1 seul) ;
        // import puis creation : inc_1 et inc_race sont tous les deux presents.
        List<String> stored = ids(budgetReader.getIncomes());
        assertThat(stored).isIn(List.of(REFERENCE_INCOME_ID), List.of(REFERENCE_INCOME_ID, "inc_race"));
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(stored);
    }

    private Callable<MvcResult> addIncome(String json) {
        return () -> mockMvc.perform(post("/api/v1/tresorerie/incomes").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> updateIncomeLabel(String id, String label) {
        String body = "{\"field\":\"label\",\"value\":\"" + label + "\"}";
        return () -> mockMvc.perform(put("/api/v1/tresorerie/incomes/" + id).contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private Callable<MvcResult> deleteIncome(String id) {
        return () -> mockMvc.perform(delete("/api/v1/tresorerie/incomes/" + id).contextPath("/api/v1"))
                .andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String incomeBody(String id, String label, int monthly) {
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"monthly\":" + monthly
                + ",\"start\":\"2026-01-01\",\"end\":\"2050-12-31\",\"growthRate\":0,"
                + "\"categoryId\":\"\",\"notes\":\"\"}";
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

    /** Identifiants des revenus tels que l'API les sert (tresorerie). */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/tresorerie").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode income : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("incomes")) {
            served.add(income.path("id").asText());
        }
        return served;
    }

    private static List<String> ids(List<IncomeModel> incomes) {
        return incomes.stream().map(IncomeModel::id).toList();
    }
}
