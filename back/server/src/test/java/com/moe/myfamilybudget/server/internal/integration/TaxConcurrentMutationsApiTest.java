package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.moe.myfamilybudget.domain.tax.model.TaxBracketModel;
import com.moe.myfamilybudget.domain.tax.model.TaxChildModel;
import com.moe.myfamilybudget.domain.tax.port.TaxReader;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;

/**
 * R-43 (24-backlog-reste-a-faire.md, Fiscalite lot B2 ; ref. 21 : SILO-211 B2) -- Ecritures concurrentes sur le
 * silo Fiscalite, de bout en bout (HTTP, Spring, H2). Depuis le lot B (R-42), les enfants, le bareme, les
 * ecarts et les parametres fiscaux s'ecrivent directement dans les tables {@code fiscal_*} ({@code
 * JpaTaxStore}) : la serialisation des ecritures repose sur le seul verrou de silo ({@code MutationSilo.TAX})
 * pris par {@code TaxCommandService}. Chaque test relit la base sans passer par un cache. Le redemarrage (H2 et
 * PostgreSQL) est couvert par {@code RestartPersistenceTest} (DB-011, enfant {@code child_db011}).
 *
 * <p>Contrairement a Credit/Tresorerie (une ligne a la fois), {@code PUT /impots} ecrit chaque liste fournie en
 * bloc ({@code deleteAll} puis {@code saveAll}, lecture-modification-ecriture sous verrou, DA-09) : deux appels
 * concurrents portant chacun une liste complete differente ne peuvent jamais se melanger, le dernier a entrer
 * dans la section critique l'emporte integralement.
 *
 * <p>Comportement documente : deux remplacements simultanes de la liste des enfants laissent exactement l'une
 * des deux listes completes (jamais un melange des deux) ; une reinitialisation du bareme par defaut contre un
 * bareme personnalise aboutit a l'un des deux etats complets, jamais une fusion ; deux ecritures simultanees du
 * meme parametre scalaire ({@code childExitAge}) laissent l'une des deux valeurs ; un import contre une
 * modification des enfants aboutit a l'un des deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference ({@code mock-budget.json}) ne porte aucun enfant, aucun bareme personnalise (repli sur le bareme
 * par defaut a la lecture) ni aucun ecart.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo211tax;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("R-43 (SILO-211 lot B2) -- Mutations concurrentes sur la fiscalite")
class TaxConcurrentMutationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaxReader taxReader;

    @Autowired
    private TaxSettingsReader taxSettingsReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(taxReader.getTaxChildren()).as("l'import remet les enfants a zero").isEmpty();
    }

    @RepeatedTest(5)
    @DisplayName("Deux remplacements simultanes des enfants : l'une des deux listes completes, jamais un melange")
    void concurrentChildrenReplacementsNeverMix() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveImpots(childrenBody(List.of("child_a1", "child_a2"))),
                saveImpots(childrenBody(List.of("child_b1", "child_b2", "child_b3")))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<String> stored = ids(taxReader.getTaxChildren());
        assertThat(stored).isIn(List.of("child_a1", "child_a2"), List.of("child_b1", "child_b2", "child_b3"));
        assertThat(idsServedByApi()).containsExactlyElementsOf(stored);
    }

    @RepeatedTest(5)
    @DisplayName("Reinitialisation du bareme contre un bareme personnalise : l'un des deux etats complets")
    void resetRacingWithCustomBracketsEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                resetDefaultBrackets(),
                saveImpots(bracketsBody())));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<TaxBracketModel> stored = taxReader.getTaxBrackets();
        List<String> ids = stored.stream().map(TaxBracketModel::id).toList();
        // le bareme par defaut (DEFAULT_BRACKETS, 5 tranches tb_1..tb_5) ou le bareme personnalise (2 tranches)
        assertThat(ids).isIn(List.of("tb_1", "tb_2", "tb_3", "tb_4", "tb_5"), List.of("tb_custom_1", "tb_custom_2"));
    }

    @RepeatedTest(5)
    @DisplayName("Meme parametre scalaire ecrit deux fois en meme temps : l'une des deux valeurs, jamais corrompue")
    void concurrentSettingWritesLeaveOneValue() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                updateChildExitAge(19),
                updateChildExitAge(25)));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(taxSettingsReader.getTaxSettings().childExitAge()).isIn(19, 25);
    }

    @RepeatedTest(3)
    @DisplayName("Import contre modification des enfants : l'un des deux ordres sequentiels")
    void importRacingWithChildrenUpdateEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                saveImpots(childrenBody(List.of("child_race")))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // modification puis import : l'import remet les enfants a l'etat de reference (aucun) ;
        // import puis modification : child_race est present.
        List<String> stored = ids(taxReader.getTaxChildren());
        assertThat(stored).isIn(List.<String>of(), List.of("child_race"));
        assertThat(idsServedByApi()).containsExactlyElementsOf(stored);
    }

    private Callable<MvcResult> saveImpots(String json) {
        return () -> mockMvc.perform(put("/api/v1/impots").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> resetDefaultBrackets() {
        return () -> mockMvc.perform(put("/api/v1/impots").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"resetDefaultTaxBrackets\"}")).andReturn();
    }

    private Callable<MvcResult> updateChildExitAge(int value) {
        String body = "{\"action\":\"updateSettings\",\"field\":\"childExitAge\",\"value\":" + value + "}";
        return () -> mockMvc.perform(put("/api/v1/impots").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String childrenBody(List<String> ids) {
        StringBuilder sb = new StringBuilder("{\"taxChildren\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(ids.get(i)).append("\",\"name\":\"Enfant ").append(i)
                    .append("\",\"birthYear\":2015}");
        }
        return sb.append("]}").toString();
    }

    private static String bracketsBody() {
        return "{\"taxBrackets\":["
                + "{\"id\":\"tb_custom_1\",\"upTo\":15000,\"rate\":0},"
                + "{\"id\":\"tb_custom_2\",\"upTo\":null,\"rate\":0.2}]}";
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

    /** Identifiants des enfants fiscaux tels que l'API les sert. */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/impots").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode child : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("taxChildren")) {
            served.add(child.path("id").asText());
        }
        return served;
    }

    private static List<String> ids(List<TaxChildModel> children) {
        return children.stream().map(TaxChildModel::id).toList();
    }
}
