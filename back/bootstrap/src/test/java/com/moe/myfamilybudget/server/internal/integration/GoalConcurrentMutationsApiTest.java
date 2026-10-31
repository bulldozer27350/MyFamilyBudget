package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntity;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalEntityMapper;
import com.moe.myfamilybudget.domain.goals.core.persistence.GoalRepository;
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;

/**
 * SILO-212 (lot B2) -- Ecritures concurrentes sur le silo Objectifs, de bout en bout (HTTP, Spring, H2).
 * Depuis le lot B1, les objectifs s'ecrivent directement dans {@code goal} et {@code goal_allocation}
 * ({@code JpaGoalStore}) : la serialisation des ecritures repose sur le seul verrou de silo
 * ({@code MutationSilo.GOALS}, avec {@code MutationSilo.WEALTH} pour la sauvegarde) pris par
 * {@code GoalCommandService}. Chaque test relit la base sans passer par un cache.
 *
 * <p>Comportement documente : une sauvegarde concurrente d'objectifs distincts ne perd aucune ligne ; la regle
 * de sur-allocation (Objectifs -> Patrimoine) est evaluee sous verrou, donc deux objectifs ne peuvent pas
 * reserver ensemble plus que le solde d'un compte ; deux sauvegardes du meme objectif laissent une seule ligne
 * (derniere ecriture gagnante) ; une sauvegarde contre une suppression, ou contre un import, aboutit a l'un des
 * deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference porte un seul placement, {@code plc_1} (solde 10 000), et aucun objectif.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo212goals;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("SILO-212 lot B2 -- Mutations concurrentes sur les objectifs")
class GoalConcurrentMutationsApiTest {

    private static final String PLACEMENT = "plc_1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(readDatabase()).as("l'import remet les objectifs a zero").isEmpty();
    }

    @Test
    @DisplayName("Creations simultanees : tous les objectifs sont conserves, positions contigues, API = base")
    void concurrentCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            String id = "goal_c" + i;
            expected.add(id);
            requests.add(saveGoal(goalBody(id, "Objectif " + i, 1000, "")));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(ids(readDatabase())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(positionsInDatabase()).isEqualTo(contiguousPositions(calls));
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Sur-allocation concurrente d'un meme compte : une seule des deux sauvegardes est acceptee")
    void concurrentOverAllocationOfSamePlacementAcceptsOnlyOne() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveGoal(goalBody("goal_a", "A", 6000, allocation("al_a", PLACEMENT, 6000))),
                saveGoal(goalBody("goal_b", "B", 6000, allocation("al_b", PLACEMENT, 6000)))));

        List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).as("une acceptation, un refus").containsExactly(200, 400);
        MvcResult refused = results.stream().filter(result -> result.getResponse().getStatus() == 400)
                .findFirst().orElseThrow();
        assertThat(refused.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("solde suffisant");

        String acceptedId = results.get(0).getResponse().getStatus() == 200 ? "goal_a" : "goal_b";
        List<ObjectifModel> stored = readDatabase();
        assertThat(ids(stored)).as("l'objectif refuse n'est pas ecrit").containsExactly(acceptedId);
        assertThat(allocatedOn(stored, PLACEMENT)).isEqualByComparingTo("6000");
    }

    @RepeatedTest(3)
    @DisplayName("Allocations concurrentes dont la somme tient dans le solde : les deux sont acceptees")
    void concurrentAllocationsWithinBalanceAreBothKept() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveGoal(goalBody("goal_a", "A", 6000, allocation("al_a", PLACEMENT, 6000))),
                saveGoal(goalBody("goal_b", "B", 4000, allocation("al_b", PLACEMENT, 4000)))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<ObjectifModel> stored = readDatabase();
        assertThat(ids(stored)).containsExactlyInAnyOrder("goal_a", "goal_b");
        assertThat(allocatedOn(stored, PLACEMENT)).isEqualByComparingTo("10000");
    }

    @RepeatedTest(3)
    @DisplayName("Meme objectif edite deux fois en meme temps : une seule ligne, l'une des deux valeurs")
    void concurrentEditsOfSameGoalLeaveOneRow() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveGoal(goalBody("goal_x", "Version 1", 1000, allocation("al_1", PLACEMENT, 100))),
                saveGoal(goalBody("goal_x", "Version 2", 2000, allocation("al_2", PLACEMENT, 200)))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<ObjectifModel> stored = readDatabase();
        assertThat(stored).singleElement().satisfies(goal -> {
            assertThat(goal.id()).isEqualTo("goal_x");
            assertThat(goal.label()).isIn("Version 1", "Version 2");
            // le libelle, le montant et l'allocation viennent de la meme ecriture (pas de melange)
            boolean firstVersion = "Version 1".equals(goal.label());
            assertThat(goal.targetAmount()).isEqualByComparingTo(firstVersion ? "1000" : "2000");
            assertThat(goal.getEffectiveAllocations()).singleElement().satisfies(allocation -> {
                assertThat(allocation.id()).isEqualTo(firstVersion ? "al_1" : "al_2");
                assertThat(allocation.amount()).isEqualByComparingTo(firstVersion ? "100" : "200");
            });
        });
        assertThat(idsServedByApi()).containsExactly("goal_x");
    }

    @RepeatedTest(3)
    @DisplayName("Sauvegarde contre suppression du meme objectif : l'un des deux ordres sequentiels")
    void saveRacingWithDeleteEndsInASerialOrder() throws Exception {
        callConcurrently(List.of(saveGoal(goalBody("goal_x", "Initial", 1000, "")))).forEach(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<MvcResult> results = callConcurrently(List.of(
                saveGoal(goalBody("goal_x", "Revise", 1500, "")),
                deleteGoal("goal_x")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(204);
        // sauvegarde puis suppression : plus d'objectif ; suppression puis sauvegarde : la sauvegarde recree la ligne
        List<ObjectifModel> stored = readDatabase();
        assertThat(ids(stored)).isIn(List.<String>of(), List.of("goal_x"));
        assertThat(idsServedByApi()).isEqualTo(ids(stored));
        stored.forEach(goal -> assertThat(goal.label()).isEqualTo("Revise"));
    }

    @RepeatedTest(3)
    @DisplayName("Import contre creation d'un objectif : l'un des deux ordres sequentiels")
    void importRacingWithCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                saveGoal(goalBody("goal_race", "Concurrent", 1000, ""))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // creation puis import : l'import vide les objectifs ; import puis creation : l'objectif est present.
        List<String> stored = ids(readDatabase());
        assertThat(stored).isIn(List.<String>of(), List.of("goal_race"));
        assertThat(idsServedByApi()).isEqualTo(stored);
    }

    private Callable<MvcResult> saveGoal(String json) {
        return () -> mockMvc.perform(post("/api/v1/patrimoine/objectifs").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> deleteGoal(String id) {
        return () -> mockMvc.perform(delete("/api/v1/patrimoine/objectifs/" + id).contextPath("/api/v1"))
                .andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String goalBody(String id, String label, int targetAmount, String allocationsJson) {
        String allocations = allocationsJson.isEmpty() ? "" : ",\"allocations\":[" + allocationsJson + "]";
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"targetAmount\":" + targetAmount
                + ",\"targetDate\":\"2027-06-01\"" + allocations + "}";
    }

    private static String allocation(String id, String placementId, int amount) {
        return "{\"id\":\"" + id + "\",\"placementId\":\"" + placementId + "\",\"amount\":" + amount + "}";
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

    /** Relit les objectifs dans les tables {@code goal} et {@code goal_allocation}, dans une transaction. */
    private List<ObjectifModel> readDatabase() {
        return new TransactionTemplate(transactionManager).execute(status ->
                GoalEntityMapper.toModels(goalRepository.findAllByOrderByPositionAsc()));
    }

    private List<Integer> positionsInDatabase() {
        return new TransactionTemplate(transactionManager).execute(status ->
                goalRepository.findAllByOrderByPositionAsc().stream().map(GoalEntity::getPosition).toList());
    }

    /** Positions attendues : une suite d'entiers consecutifs, dans l'ordre, quelle que soit la base de numerotation. */
    private List<Integer> contiguousPositions(int count) {
        int first = positionsInDatabase().isEmpty() ? 0 : positionsInDatabase().get(0);
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expected.add(first + i);
        }
        return expected;
    }

    /** Identifiants des objectifs tels que l'API les sert (export du budget). */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/budget").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode goal : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("objectifs")) {
            served.add(goal.path("id").asText());
        }
        return served;
    }

    private static List<String> ids(List<ObjectifModel> goals) {
        return goals.stream().map(ObjectifModel::id).toList();
    }

    private static BigDecimal allocatedOn(List<ObjectifModel> goals, String placementId) {
        return goals.stream()
                .flatMap(goal -> goal.getEffectiveAllocations().stream())
                .filter(allocation -> placementId.equals(allocation.placementId()))
                .map(ObjectifAllocationModel::getEffectiveAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
