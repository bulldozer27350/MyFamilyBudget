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
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel.RetirementPersonModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;

/**
 * R-41 (24-backlog-reste-a-faire.md, Retraite lot B2 ; ref. 21 : SILO-210 B2) -- Ecritures concurrentes sur le
 * silo Retraite, de bout en bout (HTTP, Spring, H2). Depuis le lot B (R-40), le plan de retraite et
 * {@code pension_settings} s'ecrivent directement dans les tables {@code pension_plan}/{@code pension_person}/
 * {@code pension_salary} et {@code pension_settings} ({@code JpaRetirementStore}) : la serialisation des
 * ecritures repose sur le seul verrou de silo ({@code MutationSilo.RETIREMENT}) pris par
 * {@code RetirementCommandService}. Chaque test relit la base sans passer par un cache. Le round-trip H2 et le
 * redemarrage (H2 et PostgreSQL) sont couverts par {@code RestartPersistenceTest} (DB-011/DB-020, personnes
 * {@code p_1}/{@code p_2}).
 *
 * <p>{@code PUT /retraite} ecrit {@code pension_plan} (et ses personnes) en bloc (suppression puis reinsertion,
 * lecture-modification-ecriture sous verrou, DA-09) : deux appels concurrents portant chacun une liste de
 * personnes differente ne peuvent jamais se melanger, le dernier a entrer dans la section critique l'emporte
 * integralement. {@code PUT /settings} avec {@code field}/{@code value} ecrit un seul parametre scalaire de
 * {@code pension_settings} (SET-020, route vers {@code RetirementCommandService#updateRetirementSetting}).
 *
 * <p>Comportement documente : deux remplacements simultanes de la liste des personnes laissent exactement
 * l'une des deux listes completes (jamais un melange des deux) ; deux ecritures simultanees du meme parametre
 * scalaire ({@code birthYear}) laissent l'une des deux valeurs ; un import contre une modification du plan
 * aboutit a l'un des deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference ({@code mock-budget.json}) porte une seule personne ({@code p_1}, Alice).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo210retirement;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("R-41 (SILO-210 lot B2) -- Mutations concurrentes sur la retraite")
class RetirementConcurrentMutationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RetirementReader retirementReader;

    @Autowired
    private RetirementSettingsReader retirementSettingsReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(ids(retirementReader.getRetirement().getEffectivePeople()))
                .as("l'import remet le plan de retraite a la personne de reference")
                .containsExactly("p_1");
    }

    @RepeatedTest(5)
    @DisplayName("Deux remplacements simultanes du plan : l'une des deux listes completes, jamais un melange")
    void concurrentFullSaveReplacementsNeverMix() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveRetraite(peopleBody(List.of("person_a1", "person_a2"))),
                saveRetraite(peopleBody(List.of("person_b1", "person_b2", "person_b3")))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<String> stored = ids(retirementReader.getRetirement().getEffectivePeople());
        assertThat(stored).isIn(List.of("person_a1", "person_a2"),
                List.of("person_b1", "person_b2", "person_b3"));
        assertThat(idsServedByApi()).containsExactlyElementsOf(stored);
    }

    @RepeatedTest(5)
    @DisplayName("Meme parametre scalaire ecrit deux fois en meme temps : l'une des deux valeurs, jamais corrompue")
    void concurrentSettingWritesLeaveOneValue() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                updateBirthYear(1970),
                updateBirthYear(1988)));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(retirementSettingsReader.getRetirementSettings().birthYear()).isIn(1970, 1988);
    }

    @RepeatedTest(3)
    @DisplayName("Import contre modification du plan : l'un des deux ordres sequentiels")
    void importRacingWithFullSaveEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                saveRetraite(peopleBody(List.of("person_race")))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // modification puis import : l'import remet le plan a l'etat de reference (p_1 seul) ;
        // import puis modification : person_race est present.
        List<String> stored = ids(retirementReader.getRetirement().getEffectivePeople());
        assertThat(stored).isIn(List.of("p_1"), List.of("person_race"));
        assertThat(idsServedByApi()).containsExactlyElementsOf(stored);
    }

    private Callable<MvcResult> saveRetraite(String json) {
        return () -> mockMvc.perform(put("/api/v1/retraite").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> updateBirthYear(int value) {
        String body = "{\"field\":\"birthYear\",\"value\":" + value + "}";
        return () -> mockMvc.perform(put("/api/v1/settings").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String peopleBody(List<String> ids) {
        StringBuilder sb = new StringBuilder("{\"people\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(ids.get(i)).append("\",\"name\":\"Personne ").append(i)
                    .append("\",\"birthYear\":1990,\"incomeLabel\":\"Salaire\",\"trimestresValides\":0,")
                    .append("\"trimestresDate\":\"2025-01-01\",\"salaryHistory\":[],\"agircPoints\":0,")
                    .append("\"ratioPointsParEuro\":0.0051,\"cadre\":false}");
        }
        return sb.append("]}").toString();
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

    /** Identifiants des personnes du plan de retraite tels que l'API les sert. */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/retraite").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode person : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("retirement").path("people")) {
            served.add(person.path("id").asText());
        }
        return served;
    }

    private static List<String> ids(List<RetirementPersonModel> people) {
        return people.stream().map(RetirementPersonModel::id).toList();
    }
}
