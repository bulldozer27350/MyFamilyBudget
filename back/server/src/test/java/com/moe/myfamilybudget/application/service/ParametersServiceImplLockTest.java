package com.moe.myfamilybudget.application.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.application.settings.ObjectifsSettingsService;
import com.moe.myfamilybudget.application.command.PatrimoineCommandService;
import com.moe.myfamilybudget.application.command.SettingsCommandRouter;
import com.moe.myfamilybudget.application.mapper.SettingsMapper;
import com.moe.myfamilybudget.application.port.MutationSilo;
import com.moe.myfamilybudget.application.port.SiloMutationLock;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.retirement.port.RetirementReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.transition.port.SettingsReader;
import com.moe.myfamilybudget.server.internal.testsupport.RecordingTransactionRunner;

/**
 * DB-061 / SILO-206 -- {@code saveSettings} verrouille, avant toute ecriture par un owner de parametre, les seuls
 * silos qu'il ecrit (port {@link SiloMutationLock}, VT-350b).
 */
@DisplayName("SILO-206 -- ParametersServiceImpl : verrou par silo")
class ParametersServiceImplLockTest {

    private SiloMutationLock lock;
    private SettingsCommandRouter router;
    private PatrimoineCommandService patrimoine;
    private ParametersServiceImpl service;

    @BeforeEach
    void setUp() {
        lock = mock(SiloMutationLock.class);
        router = mock(SettingsCommandRouter.class);
        patrimoine = mock(PatrimoineCommandService.class);
        service = new ParametersServiceImpl(
                mock(SettingsReader.class), mock(PatrimoineReader.class), mock(BankReader.class),
                mock(RetirementReader.class), new SettingsMapper(), mock(ObjectifsSettingsService.class), patrimoine,
                lock, router, RecordingTransactionRunner.direct());
    }

    @Test
    @DisplayName("saveSettings verrouille le silo de l'owner avant de l'appeler")
    void saveSettingsLocksBeforeWriting() {
        service.saveSettings(Map.of("field", "retireAge", "value", 62));

        InOrder order = inOrder(lock, router);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.RETIREMENT));
        order.verify(router).updateSetting("retireAge", 62);
    }

    @Test
    @DisplayName("un PATCH multi-champs verrouille l'union des silos de leurs owners, en un seul appel")
    void saveSettingsLocksTheUnionOfOwnersOnce() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("simulateUntilAge", 90);
        settings.put("retireAge", 62);
        settings.put("inflationRate", 0.02);

        service.saveSettings(Map.of("settings", settings));

        verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.RETIREMENT, MutationSilo.SIMULATION,
                MutationSilo.ECONOMIC_ASSUMPTIONS));
    }

    @Test
    @DisplayName("une categorie d'actifs verrouille le seul silo Patrimoine")
    void assetCategoryLocksWealthOnly() {
        service.saveSettings(Map.of("action", "removeAssetCategory", "id", "cat-1"));

        InOrder order = inOrder(lock, patrimoine);
        order.verify(lock).lockForCurrentTransaction(EnumSet.of(MutationSilo.WEALTH));
        order.verify(patrimoine).removeAssetCategory("cat-1");
    }

    @Test
    @DisplayName("un champ inconnu ou un corps sans ecriture ne verrouille rien")
    void nothingToWriteLocksNothing() {
        service.saveSettings(Map.of("field", "unknownField", "value", 1));
        service.saveSettings(List.of("pas une map"));
        service.saveSettings(null);

        verifyNoInteractions(lock);
    }
}
