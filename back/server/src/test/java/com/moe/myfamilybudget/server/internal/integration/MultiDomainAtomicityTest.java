package com.moe.myfamilybudget.server.internal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsStore;
import com.moe.myfamilybudget.transition.model.BudgetDataModel;
import com.moe.myfamilybudget.domain.budget.IncomeModel;
import com.moe.myfamilybudget.persistence.PersistenceManager;
import com.moe.myfamilybudget.persistence.converter.BankImportDocumentMapper;
import com.moe.myfamilybudget.persistence.converter.EntityModelConverter;
import com.moe.myfamilybudget.persistence.converter.FiscalEntityMapper;
import com.moe.myfamilybudget.persistence.converter.GoalEntityMapper;
import com.moe.myfamilybudget.persistence.converter.PensionEntityMapper;
import com.moe.myfamilybudget.persistence.repository.BankImportDocumentRepository;
import com.moe.myfamilybudget.persistence.repository.BudgetDataRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalActualOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalBracketRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalChildRepository;
import com.moe.myfamilybudget.persistence.repository.FiscalRateOverrideRepository;
import com.moe.myfamilybudget.persistence.repository.GoalRepository;
import com.moe.myfamilybudget.persistence.repository.PensionPlanRepository;

/**
 * VT-340 -- Atomicite des mutations multi-domaines. Import, reinitialisation et sauvegarde des parametres
 * ecrivent dans deux domaines (budget puis parametres Objectifs) : quand la seconde ecriture echoue, la
 * premiere doit etre annulee, en base ET en memoire. L'echec est simule sur le store Objectifs (spy) ; la
 * base est relue directement pour prouver le rollback reel, pas seulement le cache.
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
    private PersistenceManager persistenceManager;

    @Autowired
    private ObjectifsSettingsService objectifsSettingsService;

    @Autowired
    private BudgetDataRepository budgetDataRepository;

    @Autowired
    private PensionPlanRepository pensionPlanRepository;

    @Autowired
    private FiscalChildRepository fiscalChildRepository;

    @Autowired
    private FiscalBracketRepository fiscalBracketRepository;

    @Autowired
    private FiscalRateOverrideRepository fiscalRateOverrideRepository;

    @Autowired
    private FiscalActualOverrideRepository fiscalActualOverrideRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private BankImportDocumentRepository bankImportDocumentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private ObjectifsSettingsStore objectifsStore;

    private BudgetDataModel memoryBefore;
    private BudgetDataModel databaseBefore;

    @BeforeEach
    void importReferenceDataset() throws Exception {
        mockMvc.perform(post("/api/v1/budget/import").contextPath("/api/v1")
                .contentType(MediaType.APPLICATION_JSON).content(referenceDatasetJson()))
                .andExpect(status().isOk());
        objectifsSettingsService.save(new ObjectifsParameters(12, 3));

        memoryBefore = persistenceManager.getBudgetData();
        databaseBefore = readDatabase();
        assertThat(memoryBefore.settings().retireAge()).isEqualTo(64);
        assertThat(ids(databaseBefore)).containsExactly("inc_1");
    }

    @Test
    @DisplayName("Import : echec de l'ecriture Objectifs => budget importe annule en base et en memoire")
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

        assertThat(persistenceManager.getBudgetData().settings().retireAge()).isEqualTo(60);
        assertThat(readDatabase().settings().retireAge()).isEqualTo(60);
        assertThat(objectifsSettingsService.current().secureHorizonMonths()).isEqualTo(24);
    }

    private void assertStateUnchanged() {
        assertThat(persistenceManager.getBudgetData()).isEqualTo(memoryBefore);
        assertThat(readDatabase()).isEqualTo(databaseBefore);
        assertThat(objectifsSettingsService.current()).isEqualTo(new ObjectifsParameters(12, 3));
    }

    /** Relit la base sans passer par le cache memoire. */
    private BudgetDataModel readDatabase() {
        return new TransactionTemplate(transactionManager).execute(status ->
                EntityModelConverter.toModel(budgetDataRepository.findFirstByOrderByIdAsc().orElseThrow())
                        .withRetirement(PensionEntityMapper.toModel(
                                pensionPlanRepository.findFirstByOrderByIdAsc().orElse(null)))
                        .withTaxChildren(FiscalEntityMapper.toChildModels(
                                fiscalChildRepository.findAllByOrderByPositionAsc()))
                        .withTaxBrackets(FiscalEntityMapper.toBracketModels(
                                fiscalBracketRepository.findAllByOrderByPositionAsc()))
                        .withTaxRateOverrides(FiscalEntityMapper.toRateOverrideModels(
                                fiscalRateOverrideRepository.findAllByOrderByPositionAsc()))
                        .withTaxActualOverrides(FiscalEntityMapper.toActualOverrideModels(
                                fiscalActualOverrideRepository.findAllByOrderByPositionAsc()))
                        .withObjectifs(GoalEntityMapper.toModels(
                                goalRepository.findAllByOrderByPositionAsc()))
                        .withBankImport(BankImportDocumentMapper.toModel(
                                bankImportDocumentRepository.findFirstByOrderByIdAsc().orElse(null))));
    }

    private static List<String> ids(BudgetDataModel model) {
        return model.getEffectiveIncomes().stream().map(IncomeModel::id).toList();
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
