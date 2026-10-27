package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsStore;

/**
 * VT-340 -- Atomicite des mutations multi-domaines. Import, reinitialisation et sauvegarde des parametres
 * ecrivent dans plusieurs domaines (budget puis parametres Objectifs) : quand la seconde ecriture echoue, la
 * premiere doit etre annulee, en base ET telle qu'exposee par l'API. L'echec est simule sur le store
 * Objectifs (spy) ; l'etat est relu via {@code GET /budget} (contrat public, assemble par
 * {@code GlobalBudgetSnapshotService} directement depuis les ports des silos reels) pour prouver le rollback
 * reel, pas seulement un cache.
 *
 * <p>R-61-20 : depuis SILO-119 (lot B2), {@code GET /budget} ne passe plus par {@code PersistenceManager} ni
 * par {@code BudgetDataModel} -- comparer la reponse avant/apres couvre exactement le meme risque (rollback
 * coherent entre tous les domaines ecrits) que l'ancienne reconstruction manuelle via le hub et les
 * repositories de chaque silo, sans en dependre (meme principe que la migration de
 * {@code GlobalBudgetSnapshotServiceTest} en R-61-16).
 *
 * <p>Base H2 dediee : le contexte contient un bean espionne et ne doit pas partager la base des autres
 * contextes de test.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vt340;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@DisplayName("VT-340 -- Atomicite des mutations multi-domaines")
class MultiDomainAtomicityTest {

    private static final IllegalStateException STORE_DOWN = new IllegalStateException("simulated objectifs store failure");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectifsSettingsService objectifsSettingsService;

    @MockitoSpyBean
    private ObjectifsSettingsStore objectifsStore;

    private JsonNode budgetBefore;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(referenceDatasetJson()))
                .andExpect(status().isOk());
        objectifsSettingsService.save(new ObjectifsParameters(12, 3));

        budgetBefore = exportBudget();
        assertThat(budgetBefore.path("settings").path("retireAge").asInt()).isEqualTo(64);
        assertThat(incomeIds(budgetBefore)).containsExactly("inc_1");
    }

    @Test
    @DisplayName("Import : echec de l'ecriture Objectifs => budget importe annule en base et dans l'API")
    void failedImportIsRolledBack() throws Exception {
        doThrow(STORE_DOWN).when(objectifsStore).save(any());

        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(importedBudgetJson()))
                .andExpect(status().is5xxServerError());

        assertStateUnchanged();
    }

    @Test
    @DisplayName("Reinitialisation : echec cote Objectifs => budget non reinitialise")
    void failedResetIsRolledBack() throws Exception {
        doThrow(STORE_DOWN).when(objectifsStore).clear();

        mockMvc.perform(post("/api/v1/budget/reset").contextPath("/api/v1"))
                .andExpect(status().is5xxServerError());

        assertStateUnchanged();
    }

    @Test
    @DisplayName("Parametres : un champ Fiscalite applique puis un champ Objectifs en echec => tout est annule")
    void failedSettingsUpdateIsRolledBack() throws Exception {
        doThrow(STORE_DOWN).when(objectifsStore).save(any());

        mockMvc.perform(put("/api/v1/settings").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {\"retireAge\": 60, \"goalSecureHorizonMonths\": 24}}"))
                .andExpect(status().is5xxServerError());

        assertStateUnchanged();
    }

    @Test
    @DisplayName("Parametres : la meme mise a jour aboutit en entier quand tout fonctionne")
    void validSettingsUpdateAppliesEveryOwner() throws Exception {
        mockMvc.perform(put("/api/v1/settings").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settings\": {\"retireAge\": 60, \"goalSecureHorizonMonths\": 24}}"))
                .andExpect(status().isOk());

        // R-40 : retireAge est ecrit directement dans pension_settings (silo Retraite), plus dans un cache
        // partage ; on le relit via le contrat expose (GET /budget, compose par SettingsComposer depuis les
        // ports proprietaires), pour prouver l'ecriture reelle en base plutot que seulement dans le contexte
        // de persistance de la requete HTTP qui vient de s'executer.
        assertThat(exportBudget().path("settings").path("retireAge").asInt()).isEqualTo(60);
        assertThat(objectifsSettingsService.current().secureHorizonMonths()).isEqualTo(24);
    }

    private void assertStateUnchanged() throws Exception {
        assertThat(exportBudget()).isEqualTo(budgetBefore);
        assertThat(objectifsSettingsService.current()).isEqualTo(new ObjectifsParameters(12, 3));
    }

    /** Etat complet du budget tel qu'expose par le contrat public (silos reels, sans passer par un cache). */
    private JsonNode exportBudget() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/budget").contextPath("/api/v1"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> incomeIds(JsonNode budget) {
        List<String> ids = new ArrayList<>();
        for (JsonNode income : budget.path("incomes")) {
            ids.add(income.path("id").asText());
        }
        return ids;
    }

    /**
     * Dataset de reference : {@code mock-budget.json}, fichier partage inchange. {@code sweepEnabled} y est
     * absent : depuis FIX-020, modifier un autre parametre n'en depend plus.
     */
    private static String referenceDatasetJson() throws Exception {
        return new ClassPathResource("mock-budget.json").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Dataset de reference dont le revenu et l'age de depart en retraite different de l'etat initial. */
    private static String importedBudgetJson() throws Exception {
        return referenceDatasetJson().replace("\"retireAge\": 64", "\"retireAge\": 58")
                .replace("inc_1", "inc_imported");
    }
}
