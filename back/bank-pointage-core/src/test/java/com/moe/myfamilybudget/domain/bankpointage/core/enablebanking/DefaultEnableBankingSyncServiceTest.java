package com.moe.myfamilybudget.domain.bankpointage.core.enablebanking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.EnableBankingException;
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportChange;
import com.moe.myfamilybudget.domain.bankpointage.port.BankImportModifier;
import com.moe.myfamilybudget.domain.bankpointage.port.EnableBankingSyncStateStore;

/**
 * SILO-170 : la synchronisation Enable Banking, désormais dans {@code bank-pointage-core}, reste silencieuse
 * tant que la configuration est absente (distribution portable) et ne touche à aucun port.
 */
class DefaultEnableBankingSyncServiceTest {

    @Test
    @DisplayName("sans configuration : service indisponible, motif explicite, aucun accès aux ports")
    void unconfigured_service_reports_reason_and_refuses_to_sync() {
        EnableBankingConfig config = new EnableBankingConfig("", "", "", "https://api.enablebanking.com", 15);
        config.init();
        AtomicInteger portCalls = new AtomicInteger();
        EnableBankingSyncStateStore stateStore = new EnableBankingSyncStateStore() {
            @Override
            public Optional<String> findLastBookingDate(String accountUid) {
                portCalls.incrementAndGet();
                return Optional.empty();
            }

            @Override
            public void save(String accountUid, String lastBookingDate, Instant lastSyncAt) {
                portCalls.incrementAndGet();
            }
        };
        DefaultEnableBankingSyncService service = new DefaultEnableBankingSyncService(
                config,
                new EnableBankingClient(config),
                stateStore,
                new BankImportModifier() {
                    @Override
                    public <T> T modifyBankImport(Function<BankImportModel, BankImportChange<T>> modification) {
                        portCalls.incrementAndGet();
                        return null;
                    }
                },
                null);

        assertFalse(service.isConfigured());
        assertTrue(service.unavailableReason().contains("MYFAMILYBUDGET_ENABLE_BANKING_APPLICATION_ID"));
        assertThrows(EnableBankingException.class, service::sync);
        assertTrue(portCalls.get() == 0, "aucun port ne doit être appelé quand la synchronisation est désactivée");
    }
}
