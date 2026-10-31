package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;

/**
 * SILO-213 (lot B2) -- Ecritures concurrentes sur le silo Banque/Pointage, de bout en bout (HTTP, Spring, H2).
 * L'import bancaire s'ecrit directement dans {@code bank_import_document} ({@code JpaBankStore}) et chaque
 * ecriture lit l'import <em>sous le verrou</em> du silo ({@code BankImportCommandService#modifyBankImport},
 * decision du plan 21) : chaque test relit la base sans passer par un cache.
 *
 * <p>Garantie verifiee : <strong>aucune modification n'est perdue</strong>. Les modifications simultanees de
 * transactions distinctes (categorie), les creations simultanees (categories, regles, transactions) et leur
 * melange sont toutes presentes apres l'execution, et l'API sert exactement ce que la base contient. Un import
 * du budget contre une creation aboutit a l'un des deux ordres sequentiels. Le redemarrage de l'application est
 * couvert par {@code RestartPersistenceTest} (import bancaire, changement de categorie, relecture).
 *
 * <p>Base H2 dediee pour ne pas partager l'etat avec les autres contextes de test. Le jeu de donnees de
 * reference porte huit transactions ({@code tx_0} a {@code tx_7}, categorie {@code cat_loyer}), deux categories
 * et aucune regle.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:silo213bank;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@Timeout(120)
@DisplayName("SILO-213 lot B2 -- Mutations concurrentes sur l'import bancaire")
class BankConcurrentMutationsApiTest {

    private static final int INITIAL_TRANSACTIONS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BankReader bankReader;

    private String datasetJson;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        datasetJson = referenceDataset();
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(datasetJson))
                .andExpect(status().isOk());
        assertThat(transactionIds(bankReader.getBankImport())).as("l'import remet l'import bancaire a l'etat de reference")
                .hasSize(INITIAL_TRANSACTIONS);
    }

    @RepeatedTest(3)
    @DisplayName("Categories modifiees en meme temps sur des transactions distinctes : aucune modification perdue")
    void concurrentCategoryChangesOnDistinctTransactionsAreAllKept() throws Exception {
        List<Callable<MvcResult>> requests = new ArrayList<>();
        for (int i = 0; i < INITIAL_TRANSACTIONS; i++) {
            requests.add(setCategory("tx_" + i, "cat_n" + i));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        Map<String, String> categories = categoryByTransaction(bankReader.getBankImport());
        for (int i = 0; i < INITIAL_TRANSACTIONS; i++) {
            assertThat(categories).as("categorie de tx_" + i).containsEntry("tx_" + i, "cat_n" + i);
        }
        assertThat(categoryByTransaction(servedByApi())).isEqualTo(categories);
    }

    @RepeatedTest(3)
    @DisplayName("Creations simultanees de categories : toutes les categories sont conservees, API = base")
    void concurrentCategoryCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>(List.of("cat_loyer", "cat_courses"));
        for (int i = 0; i < calls; i++) {
            String id = "cat_c" + i;
            expected.add(id);
            requests.add(addLine("categories", "{\"id\":\"" + id + "\",\"label\":\"Categorie " + i
                    + "\",\"kind\":\"Dépense\",\"compressible\":\"Non\"}"));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(201));

        assertThat(categoryIds(bankReader.getBankImport())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(categoryIds(servedByApi())).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Creations simultanees de transactions : toutes les transactions sont conservees, API = base")
    void concurrentTransactionCreationsAreAllKept() throws Exception {
        int calls = 8;
        List<Callable<MvcResult>> requests = new ArrayList<>();
        List<String> expected = new ArrayList<>(initialTransactionIds());
        for (int i = 0; i < calls; i++) {
            String id = "tx_f" + i;
            expected.add(id);
            requests.add(forceTransaction(id));
        }

        callConcurrently(requests).forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(transactionIds(bankReader.getBankImport())).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(transactionIds(servedByApi())).containsExactlyInAnyOrderElementsOf(expected);
    }

    @RepeatedTest(3)
    @DisplayName("Categorie, regle et transaction creees en meme temps : les trois modifications sont presentes")
    void mixedWritesAreAllKept() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                setCategory("tx_0", "cat_courses"),
                addLine("rules", "{\"id\":\"rule_mix\",\"matchText\":\"MIX\",\"categoryId\":\"cat_loyer\"}"),
                forceTransaction("tx_mix"),
                addLine("categories", "{\"id\":\"cat_mix\",\"label\":\"Mixte\",\"kind\":\"Dépense\","
                        + "\"compressible\":\"Oui\"}")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(201);
        assertThat(results.get(2).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(3).getResponse().getStatus()).isEqualTo(201);

        BankImportModel stored = bankReader.getBankImport();
        assertThat(categoryByTransaction(stored)).containsEntry("tx_0", "cat_courses");
        assertThat(stored.rules()).extracting(BankImportModel.BankImportRuleModel::id).containsExactly("rule_mix");
        assertThat(transactionIds(stored)).contains("tx_mix").hasSize(INITIAL_TRANSACTIONS + 1);
        assertThat(categoryIds(stored)).contains("cat_mix", "cat_loyer", "cat_courses");
        assertThat(transactionIds(servedByApi())).isEqualTo(transactionIds(stored));
    }

    @Test
    @DisplayName("Meme transaction categorisee deux fois en meme temps : l'une des deux valeurs, sans perte ailleurs")
    void concurrentCategoryChangesOfSameTransactionKeepOneValue() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(
                setCategory("tx_0", "cat_a"),
                setCategory("tx_0", "cat_b"),
                setCategory("tx_1", "cat_c")));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        Map<String, String> categories = categoryByTransaction(bankReader.getBankImport());
        assertThat(categories.get("tx_0")).isIn("cat_a", "cat_b");
        assertThat(categories).containsEntry("tx_1", "cat_c");
        assertThat(categories).hasSize(INITIAL_TRANSACTIONS);
    }

    @RepeatedTest(3)
    @DisplayName("Import du budget contre creation d'une transaction : l'un des deux ordres sequentiels")
    void importRacingWithCreationEndsInASerialOrder() throws Exception {
        List<MvcResult> results = callConcurrently(List.of(importDataset(), forceTransaction("tx_race")));

        assertThat(results.get(0).getResponse().getStatus()).isEqualTo(200);
        assertThat(results.get(1).getResponse().getStatus()).isEqualTo(200);
        // creation puis import : l'import remplace l'import bancaire ; import puis creation : la transaction est ajoutee.
        List<String> stored = transactionIds(bankReader.getBankImport());
        List<String> reference = initialTransactionIds();
        List<String> withRace = new ArrayList<>(reference);
        withRace.add("tx_race");
        assertThat(stored).isIn(reference, withRace);
        assertThat(transactionIds(servedByApi())).isEqualTo(stored);
    }

    // ---------------------------------------------------------------------------------------------
    // Requetes
    // ---------------------------------------------------------------------------------------------

    private Callable<MvcResult> setCategory(String txId, String categoryId) {
        return () -> mockMvc.perform(put("/api/v1/bank-import/transactions/" + txId + "/category")
                .contextPath("/api/v1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId + "\"}")).andReturn();
    }

    private Callable<MvcResult> addLine(String listKey, String json) {
        return () -> mockMvc.perform(post("/api/v1/bank-import/" + listKey).contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Callable<MvcResult> forceTransaction(String id) {
        return () -> mockMvc.perform(post("/api/v1/bank-import/transactions/force").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"date\":\"2026-02-01\",\"label\":\"Force " + id
                        + "\",\"type\":\"CB\",\"amount\":-10,\"categoryId\":\"cat_loyer\"}")).andReturn();
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

    // ---------------------------------------------------------------------------------------------
    // Lecture
    // ---------------------------------------------------------------------------------------------

    /** Import bancaire tel que l'API le sert (export du budget), relu par le mapper du silo. */
    private BankImportModel servedByApi() throws Exception {
        MvcResult api = mockMvc.perform(get("/api/v1/budget").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        JsonNode bankImport = objectMapper.readTree(api.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("bankImport");
        return new BankImportModel(
                transactionsOf(bankImport.path("transactions")),
                categoriesOf(bankImport.path("categories")),
                List.of());
    }

    private static List<BankImportModel.BankTransactionModel> transactionsOf(JsonNode nodes) {
        List<BankImportModel.BankTransactionModel> transactions = new ArrayList<>();
        for (JsonNode node : nodes) {
            transactions.add(new BankImportModel.BankTransactionModel(node.path("id").asText(),
                    node.path("date").asText(), node.path("label").asText(), node.path("type").asText(),
                    node.path("amount").decimalValue(), node.path("categoryId").asText(), List.of()));
        }
        return transactions;
    }

    private static List<BankImportModel.CategoryModel> categoriesOf(JsonNode nodes) {
        List<BankImportModel.CategoryModel> categories = new ArrayList<>();
        for (JsonNode node : nodes) {
            categories.add(new BankImportModel.CategoryModel(node.path("id").asText(), node.path("label").asText(),
                    node.path("kind").asText(), node.path("compressible").asText()));
        }
        return categories;
    }

    private static List<String> transactionIds(BankImportModel bankImport) {
        return bankImport.transactions().stream().map(BankImportModel.BankTransactionModel::id).toList();
    }

    private static List<String> categoryIds(BankImportModel bankImport) {
        return bankImport.categories().stream().map(BankImportModel.CategoryModel::id).toList();
    }

    private static Map<String, String> categoryByTransaction(BankImportModel bankImport) {
        return bankImport.transactions().stream().collect(Collectors.toMap(
                BankImportModel.BankTransactionModel::id, BankImportModel.BankTransactionModel::categoryId));
    }

    private static List<String> initialTransactionIds() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < INITIAL_TRANSACTIONS; i++) {
            ids.add("tx_" + i);
        }
        return ids;
    }

    /** Jeu de donnees de reference ({@code mock-budget.json}, inchange) complete par un import bancaire. */
    private String referenceDataset() throws Exception {
        String raw = new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8)
                .replace("\uFEFF", "");
        ObjectNode budget = (ObjectNode) objectMapper.readTree(raw);
        StringBuilder transactions = new StringBuilder();
        for (int i = 0; i < INITIAL_TRANSACTIONS; i++) {
            if (i > 0) {
                transactions.append(',');
            }
            transactions.append("{\"id\":\"tx_").append(i).append("\",\"date\":\"2026-01-0").append(i + 1)
                    .append("\",\"label\":\"Operation ").append(i).append("\",\"type\":\"CB\",\"amount\":-")
                    .append(10 + i).append(",\"categoryId\":\"cat_loyer\"}");
        }
        budget.set("bankImport", objectMapper.readTree("{"
                + "\"categories\":["
                + "{\"id\":\"cat_loyer\",\"label\":\"Logement\",\"kind\":\"Dépense\",\"compressible\":\"Non\"},"
                + "{\"id\":\"cat_courses\",\"label\":\"Alimentation\",\"kind\":\"Dépense\",\"compressible\":\"Oui\"}],"
                + "\"transactions\":[" + transactions + "],"
                + "\"matchings\":[]}"));
        return objectMapper.writeValueAsString(budget);
    }
}
