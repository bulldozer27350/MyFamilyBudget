package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.wealth.model.RealEstateModel;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;

/**
 * R-31 (24-backlog-reste-a-faire.md, Patrimoine lot B2 ; ref. 21 : SILO-215 B2) -- Ecritures concurrentes sur
 * le silo Patrimoine, de bout en bout (HTTP, Spring, H2). Depuis le lot B (R-30), les placements et
 * l'immobilier s'ecrivent directement dans {@code wealth_placement}/{@code wealth_real_estate} ({@code
 * JpaWealthStore}) : la serialisation des ecritures repose sur le seul verrou de silo ({@code
 * MutationSilo.WEALTH}) pris par {@code PatrimoineCommandService}. Chaque test relit la base sans passer par
 * un cache. Le redemarrage (H2 et PostgreSQL) reste a la charge d'un futur ajout a {@code
 * RestartPersistenceTest} (DB-011 ne porte pas de placement). Les virements (DA-14, silo Tresorerie) et les
 * categories d'actifs ({@code /settings}, hors de {@code savePatrimoineRow}) restent hors de ce patch.
 *
 * <p>Comportement documente ({@code savePatrimoineRow} relit, modifie et reecrit la liste entiere sous verrou,
 * meme gabarit que {@code saveLoanRow} pour le Credit, R-11) : une creation concurrente de placements/biens
 * distincts ne perd aucune ligne ; deux sauvegardes de la meme ligne laissent une seule ligne, avec l'une des
 * deux valeurs ; une sauvegarde contre une suppression de la meme ligne aboutit a l'un des deux ordres
 * sequentiels (ligne absente si la sauvegarde est relue avant la suppression, recreee si la suppression est
 * relue avant la sauvegarde) puisque {@code savePatrimoineRow} ajoute la ligne si elle est absente de la liste
 * relue, memes gabarit et limite que {@code saveGoal}/{@code saveLoanRow} ; un import contre une creation
 * aboutit egalement a l'un des deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference ({@code mock-budget.json}) porte un placement ({@code plc_1}) et un bien ({@code re_1}), presents
 * apres chaque import.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo215wealth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("R-31 (SILO-215 lot B2) -- Mutations concurrentes sur le patrimoine")
class PatrimoineConcurrentMutationsApiTest {

    private static final String REFERENCE_PLACEMENT_ID = "plc_1";
    private static final String REFERENCE_REAL_ESTATE_ID = "re_1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PatrimoineReader patrimoineReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(placementIds()).as("l'import ne porte que le placement de reference")
                .containsExactly(REFERENCE_PLACEMENT_ID);
        assertThat(realEstateIds()).as("l'import ne porte que le bien de reference")
                .containsExactly(REFERENCE_REAL_ESTATE_ID);
    }

    @Test
    @DisplayName("Creations simultanees de placements : tous conserves, API = base")
    void concurrentPlacementCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        expected.add(REFERENCE_PLACEMENT_ID);
        for (int i = 0; i < calls; i++) {
            String id = "plc_c" + i;
            expected.add(id);
            requests.add(savePlacement(placementBody(id, "Placement " + i, 1000 + i)));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(placementIds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(placementIdsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Meme placement edite deux fois en meme temps : une seule ligne, l'une des deux valeurs")
    void concurrentEditsOfSamePlacementLeaveOneRow() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                savePlacement(placementBody("plc_x", "Version 1", 1000)),
                savePlacement(placementBody("plc_x", "Version 2", 2000))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<PlacementModel> stored = patrimoineReader.getPlacements().stream()
                .filter(p -> "plc_x".equals(p.id())).toList();
        assertThat(stored).singleElement().satisfies(placement -> {
            assertThat(placement.label()).isIn("Version 1", "Version 2");
            boolean firstVersion = "Version 1".equals(placement.label());
            assertThat(placement.balance()).isEqualByComparingTo(firstVersion ? "1000" : "2000");
        });
        assertThat(placementIdsServedByApi()).contains("plc_x");
    }

    @RepeatedTest(3)
    @DisplayName("Sauvegarde contre suppression du meme placement : l'un des deux ordres sequentiels")
    void saveRacingWithDeleteEndsInASerialOrder() throws Exception {
        callConcurrently(List.of(savePlacement(placementBody("plc_y", "Initial", 1000)))).forEach(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<MvcResult> results = callConcurrently(List.of(
                savePlacement(placementBody("plc_y", "Revise", 1500)),
                deletePlacement("plc_y")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(204);
        // sauvegarde puis suppression : plc_y absent (la suppression relit l'etat deja sauvegarde) ;
        // suppression puis sauvegarde : savePatrimoineRow ne retrouve plus la ligne et la recree (plc_y present,
        // valeurs "Revise"/1500) -- meme patron que GoalConcurrentMutationsApiTest/CreditConcurrentMutationsApiTest.
        List<String> stored = placementIds();
        assertThat(stored).isIn(List.of(REFERENCE_PLACEMENT_ID), List.of(REFERENCE_PLACEMENT_ID, "plc_y"));
        assertThat(placementIdsServedByApi()).isEqualTo(stored);
    }

    @RepeatedTest(3)
    @DisplayName("Import contre creation d'un placement : l'un des deux ordres sequentiels")
    void importRacingWithPlacementCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                savePlacement(placementBody("plc_race", "Concurrent", 1000))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // creation puis import : l'import remet les placements a l'etat de reference (plc_1 seul) ;
        // import puis creation : plc_1 et plc_race sont tous les deux presents.
        List<String> stored = placementIds();
        assertThat(stored).isIn(List.of(REFERENCE_PLACEMENT_ID), List.of(REFERENCE_PLACEMENT_ID, "plc_race"));
        assertThat(placementIdsServedByApi()).containsExactlyInAnyOrderElementsOf(stored);
    }

    @Test
    @DisplayName("Creations simultanees de biens immobiliers : tous conserves, API = base")
    void concurrentRealEstateCreationsAreAllKept() throws Exception {
        int calls = 6;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        expected.add(REFERENCE_REAL_ESTATE_ID);
        for (int i = 0; i < calls; i++) {
            String id = "re_c" + i;
            expected.add(id);
            requests.add(saveRealEstate(realEstateBody(id, "Bien " + i, 100000 + i)));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(realEstateIds()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(realEstateIdsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Meme bien edite deux fois en meme temps : une seule ligne, l'une des deux valeurs")
    void concurrentEditsOfSameRealEstateLeaveOneRow() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveRealEstate(realEstateBody("re_x", "Version 1", 200000)),
                saveRealEstate(realEstateBody("re_x", "Version 2", 300000))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<RealEstateModel> stored = patrimoineReader.getRealEstate().stream()
                .filter(re -> "re_x".equals(re.id())).toList();
        assertThat(stored).singleElement().satisfies(realEstate -> {
            assertThat(realEstate.label()).isIn("Version 1", "Version 2");
            boolean firstVersion = "Version 1".equals(realEstate.label());
            assertThat(realEstate.currentValue()).isEqualByComparingTo(firstVersion ? "200000" : "300000");
        });
        assertThat(realEstateIdsServedByApi()).contains("re_x");
    }

    private Callable<MvcResult> savePlacement(String json) {
        return () -> mockMvc.perform(post("/api/v1/patrimoine/placements").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> deletePlacement(String id) {
        return () -> mockMvc.perform(delete("/api/v1/patrimoine/placements/" + id).contextPath("/api/v1"))
                .andReturn();
    }

    private Callable<MvcResult> saveRealEstate(String json) {
        return () -> mockMvc.perform(post("/api/v1/patrimoine/realEstate").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String placementBody(String id, String label, int balance) {
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"category\":\"Epargne\",\"balance\":" + balance
                + ",\"balanceDate\":\"2026-01-01\",\"monthly\":0,\"monthlyFrom\":\"2026-01-01\","
                + "\"monthlyUntil\":\"2050-12-31\",\"ratePess\":0,\"rateCorr\":0,\"rateOpti\":0,"
                + "\"excludedFromRetirement\":false,\"notes\":\"\"}";
    }

    private static String realEstateBody(String id, String label, int currentValue) {
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"type\":\"Résidence Principale\","
                + "\"currentValue\":" + currentValue + ",\"valuationYear\":2026,\"annualGrowthRate\":0.02,"
                + "\"notes\":\"\"}";
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

    private List<String> placementIds() {
        return patrimoineReader.getPlacements().stream().map(PlacementModel::id).toList();
    }

    private List<String> realEstateIds() {
        return patrimoineReader.getRealEstate().stream().map(RealEstateModel::id).toList();
    }

    /** Identifiants des placements tels que l'API les sert. */
    private List<String> placementIdsServedByApi() throws Exception {
        return idsServedByApi("placements");
    }

    /** Identifiants des biens immobiliers tels que l'API les sert. */
    private List<String> realEstateIdsServedByApi() throws Exception {
        return idsServedByApi("realEstate");
    }

    private List<String> idsServedByApi(String field) throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/patrimoine").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path(field)) {
            served.add(row.path("id").asText());
        }
        return served;
    }
}
