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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;

/**
 * R-11 (24-backlog-reste-a-faire.md, Credit lot B2 ; ref. 21 : SILO-214 B2) -- Ecritures concurrentes sur le
 * silo Credit, de bout en bout (HTTP, Spring, H2). Depuis le lot B (R-10), les prets s'ecrivent directement
 * dans {@code credit_loan} ({@code JpaLoanStore}) : la serialisation des ecritures repose sur le seul verrou de
 * silo ({@code MutationSilo.CREDIT}) pris par {@code LoanCommandService}. Chaque test relit la base sans passer
 * par un cache. Le round-trip H2 et le test du store ({@code JpaLoanStoreTest}) sont couverts par R-10 ; le
 * redemarrage (H2 et PostgreSQL) est couvert par {@code RestartPersistenceTest} (DB-011, pret {@code
 * loan_db011}).
 *
 * <p>Comportement documente : une creation concurrente de prets distincts ne perd aucune ligne, positions
 * contigues ; deux sauvegardes du meme pret laissent une seule ligne (derniere ecriture gagnante, valeurs d'une
 * seule des deux ecritures) ; une sauvegarde contre une suppression, ou contre un import, aboutit a l'un des
 * deux ordres sequentiels.
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference ({@code mock-budget.json}) ne porte aucun pret.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo214credit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("R-11 (SILO-214 lot B2) -- Mutations concurrentes sur les prets")
class CreditConcurrentMutationsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoanReader loanReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(loanReader.getLoans()).as("l'import remet les prets a zero").isEmpty();
    }

    @Test
    @DisplayName("Creations simultanees : tous les prets sont conserves, positions contigues, API = base")
    void concurrentCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            String id = "loan_c" + i;
            expected.add(id);
            requests.add(saveLoan(loanBody(id, "Pret " + i, 1000 + i)));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(ids(loanReader.getLoans())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(idsServedByApi()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Meme pret edite deux fois en meme temps : une seule ligne, l'une des deux valeurs")
    void concurrentEditsOfSameLoanLeaveOneRow() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                saveLoan(loanBody("loan_x", "Version 1", 1000)),
                saveLoan(loanBody("loan_x", "Version 2", 2000))));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<LoanModel> stored = loanReader.getLoans();
        assertThat(stored).singleElement().satisfies(loan -> {
            assertThat(loan.id()).isEqualTo("loan_x");
            assertThat(loan.label()).isIn("Version 1", "Version 2");
            // le libelle et le crd viennent de la meme ecriture (pas de melange entre les deux versions)
            boolean firstVersion = "Version 1".equals(loan.label());
            assertThat(loan.crd()).isEqualByComparingTo(firstVersion ? "1000" : "2000");
        });
        assertThat(idsServedByApi()).containsExactly("loan_x");
    }

    @RepeatedTest(3)
    @DisplayName("Sauvegarde contre suppression du meme pret : l'un des deux ordres sequentiels")
    void saveRacingWithDeleteEndsInASerialOrder() throws Exception {
        callConcurrently(List.of(saveLoan(loanBody("loan_x", "Initial", 1000)))).forEach(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));

        List<MvcResult> results = callConcurrently(List.of(
                saveLoan(loanBody("loan_x", "Revise", 1500)),
                deleteLoan("loan_x")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(204);
        // sauvegarde puis suppression : plus de pret ; suppression puis sauvegarde : la sauvegarde recree la ligne
        List<LoanModel> stored = loanReader.getLoans();
        assertThat(ids(stored)).isIn(List.<String>of(), List.of("loan_x"));
        assertThat(idsServedByApi()).isEqualTo(ids(stored));
        stored.forEach(loan -> assertThat(loan.label()).isEqualTo("Revise"));
    }

    @RepeatedTest(3)
    @DisplayName("Import contre creation d'un pret : l'un des deux ordres sequentiels")
    void importRacingWithCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                importDataset(),
                saveLoan(loanBody("loan_race", "Concurrent", 1000))));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // creation puis import : l'import vide les prets ; import puis creation : le pret est present.
        List<String> stored = ids(loanReader.getLoans());
        assertThat(stored).isIn(List.<String>of(), List.of("loan_race"));
        assertThat(idsServedByApi()).isEqualTo(stored);
    }

    private Callable<MvcResult> saveLoan(String json) {
        return () -> mockMvc.perform(post("/api/v1/patrimoine/loans").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> deleteLoan(String id) {
        return () -> mockMvc.perform(delete("/api/v1/patrimoine/loans/" + id).contextPath("/api/v1"))
                .andReturn();
    }

    private Callable<MvcResult> importDataset() {
        return () -> mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson)).andReturn();
    }

    private static String loanBody(String id, String label, int crd) {
        return "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"crd\":" + crd
                + ",\"rate\":0.02,\"monthly\":500,\"insurance\":10,"
                + "\"startDate\":\"2026-01-01\",\"endDate\":\"2046-01-01\"}";
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

    /** Identifiants des prets tels que l'API les sert (patrimoine). */
    private List<String> idsServedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/patrimoine").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        List<String> served = new ArrayList<>();
        for (JsonNode loan : objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("loans")) {
            served.add(loan.path("id").asText());
        }
        return served;
    }

    private static List<String> ids(List<LoanModel> loans) {
        return loans.stream().map(LoanModel::id).toList();
    }
}
