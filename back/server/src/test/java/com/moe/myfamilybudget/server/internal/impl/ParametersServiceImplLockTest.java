package com.moe.myfamilybudget.server.internal.impl;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.moe.myfamilybudget.server.internal.calculation.ObjectifsSettingsService;
import com.moe.myfamilybudget.server.internal.command.PatrimoineCommandService;
import com.moe.myfamilybudget.server.internal.command.SettingsCommandRouter;
import com.moe.myfamilybudget.server.internal.mapper.SettingsMapper;
import com.moe.myfamilybudget.server.internal.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.RetirementReader;
import com.moe.myfamilybudget.server.internal.port.BudgetMutationLock;
import com.moe.myfamilybudget.server.internal.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * DB-061 -- {@code saveSettings} prend le verrou de mutation du budget (port {@link BudgetMutationLock}, VT-350b)
 * avant toute ecriture par un owner de parametre.
 */
@DisplayName("DB-061 -- ParametersServiceImpl : verrou du budget")
class ParametersServiceImplLockTest {

    @Test
    @DisplayName("saveSettings prend le verrou du budget avant d'appeler l'owner du parametre")
    void saveSettingsLocksBeforeWriting() {
        BudgetMutationLock lock = mock(BudgetMutationLock.class);
        SettingsCommandRouter router = mock(SettingsCommandRouter.class);
        ParametersServiceImpl service = new ParametersServiceImpl(
                mock(SettingsReader.class), mock(PatrimoineReader.class), mock(BankReader.class),
                mock(RetirementReader.class), new SettingsMapper(), mock(ObjectifsSettingsService.class), mock(PatrimoineCommandService.class),
                lock, router);

        service.saveSettings(Map.of("field", "retireAge", "value", 62));

        InOrder order = inOrder(lock, router);
        order.verify(lock).lockForCurrentTransaction();
        order.verify(router).updateSetting("retireAge", 62);
    }
}
