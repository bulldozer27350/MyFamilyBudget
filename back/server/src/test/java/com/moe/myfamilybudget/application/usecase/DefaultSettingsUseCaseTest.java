package com.moe.myfamilybudget.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.usecase.settings.SettingsUseCase;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.goals.calculation.ObjectifsParameters;
import com.moe.myfamilybudget.domain.retirement.model.RetirementModel;
import com.moe.myfamilybudget.domain.retirement.model.RetirementSettingsModel;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementSettingsReader;
import com.moe.myfamilybudget.domain.settings.model.EconomicAssumptionsModel;
import com.moe.myfamilybudget.domain.settings.model.SimulationSettingsModel;
import com.moe.myfamilybudget.domain.settings.port.EconomicAssumptionsReader;
import com.moe.myfamilybudget.domain.settings.port.SimulationSettingsReader;
import com.moe.myfamilybudget.domain.tax.model.TaxSettingsModel;
import com.moe.myfamilybudget.domain.tax.port.TaxSettingsReader;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * Tests unitaires de {@link DefaultSettingsUseCase} (R-88, SILO-319).
 */
class DefaultSettingsUseCaseTest {

    private RetirementSettingsReader retirementSettingsReader;
    private TaxSettingsReader taxSettingsReader;
    private TresorerieSettingsReader tresorerieSettingsReader;
    private SimulationSettingsReader simulationSettingsReader;
    private EconomicAssumptionsReader economicAssumptionsReader;
    private PatrimoineReader patrimoineReader;
    private BankReader bankReader;
    private RetirementReader retirementReader;
    private ObjectifsSettingsService objectifsSettingsService;
    private PatrimoineCommandService patrimoineCommandService;
    private SiloMutationLock siloMutationLock;
    private SettingsCommandRouter settingsCommandRouter;
    private SettingsUseCase useCase;

    @BeforeEach
    void setUp() {
        retirementSettingsReader = mock(RetirementSettingsReader.class);
        taxSettingsReader = mock(TaxSettingsReader.class);
        tresorerieSettingsReader = mock(TresorerieSettingsReader.class);
        simulationSettingsReader = mock(SimulationSettingsReader.class);
        economicAssumptionsReader = mock(EconomicAssumptionsReader.class);
        patrimoineReader = mock(PatrimoineReader.class);
        bankReader = mock(BankReader.class);
        retirementReader = mock(RetirementReader.class);
        objectifsSettingsService = mock(ObjectifsSettingsService.class);
        patrimoineCommandService = mock(PatrimoineCommandService.class);
        siloMutationLock = mock(SiloMutationLock.class);
        settingsCommandRouter = mock(SettingsCommandRouter.class);

        when(retirementSettingsReader.getRetirementSettings()).thenReturn(new RetirementSettingsModel(1985, 64));
        when(taxSettingsReader.getTaxSettings()).thenReturn(new TaxSettingsModel(25, new BigDecimal("0.10")));
        when(tresorerieSettingsReader.getTresorerieSettings()).thenReturn(
                new TresorerieSettingsModel("2026-01-01", "manual", new BigDecimal("1000"), true,
                        new BigDecimal("5000"), new BigDecimal("1000"), new BigDecimal("500")));
        when(simulationSettingsReader.getSimulationSettings()).thenReturn(new SimulationSettingsModel(85));
        when(economicAssumptionsReader.getEconomicAssumptions()).thenReturn(new EconomicAssumptionsModel(new BigDecimal("0.02")));
        when(patrimoineReader.getAssetCategories()).thenReturn(List.of());
        when(bankReader.getBankImport()).thenReturn(new com.moe.myfamilybudget.server.internal.testsupport.InMemoryBankStore().getBankImport());
        when(objectifsSettingsService.current()).thenReturn(ObjectifsParameters.defaults());
        when(retirementReader.getRetirement()).thenReturn(new com.moe.myfamilybudget.server.internal.testsupport.InMemoryRetirementStore().getRetirement());

        useCase = new DefaultSettingsUseCase(
                retirementSettingsReader, taxSettingsReader, tresorerieSettingsReader,
                simulationSettingsReader, economicAssumptionsReader, patrimoineReader,
                bankReader, retirementReader, new SettingsMapper(), objectifsSettingsService,
                patrimoineCommandService, siloMutationLock, settingsCommandRouter,
                RecordingTransactionRunner.direct());
    }

    @Test
    @DisplayName("getSettings compose la vue des paramètres depuis les readers des silos")
    void getSettings_composesView() {
        Map<String, Object> result = useCase.getSettings();

        assertNotNull(result);
        assertThat(result).containsKey("settings");
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) result.get("settings");
        assertEquals(1985, settings.get("birthYear"));
        assertEquals(85, settings.get("simulateUntilAge"));
    }

    @Test
    @DisplayName("saveSettings délègue le routage des champs au SettingsCommandRouter")
    void saveSettings_routesField() {
        Map<String, Object> body = Map.of("field", "birthYear", "value", 1990);

        useCase.saveSettings(body);

        verify(settingsCommandRouter).updateSetting("birthYear", 1990);
    }
}
