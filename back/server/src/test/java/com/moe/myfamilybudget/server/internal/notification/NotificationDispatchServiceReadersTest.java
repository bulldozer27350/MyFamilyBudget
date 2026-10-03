package com.moe.myfamilybudget.server.internal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel;
import com.moe.myfamilybudget.domain.bankpointage.model.BankImportModel.BankTransactionModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.server.internal.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.server.internal.model.SettingsModel;
import com.moe.myfamilybudget.server.internal.notification.rules.BalanceFloorRule;
import com.moe.myfamilybudget.server.internal.notification.rules.DebitThresholdRule;
import com.moe.myfamilybudget.server.internal.notification.rules.ObjectifReachableRule;
import com.moe.myfamilybudget.server.internal.persistence.BudgetMutatedEvent;
import com.moe.myfamilybudget.server.internal.persistence.repository.NotificationSentLogRepository;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.server.internal.port.GoalReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.server.internal.port.SettingsReader;

/**
 * NOTIF-010 : {@code NotificationDispatchService} lit le budget uniquement via les ports de lecture, et
 * seulement pour les règles actives. Les règles et la factory restent testées séparément.
 */
class NotificationDispatchServiceReadersTest {

    private BankReader bankReader;
    private SettingsReader settingsReader;
    private GoalReader goalReader;
    private PatrimoineReader patrimoineReader;
    private NotificationSettingsService settingsService;
    private NotificationChannel channel;
    private NotificationSentLogRepository sentLogRepository;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        bankReader = mock(BankReader.class);
        settingsReader = mock(SettingsReader.class);
        goalReader = mock(GoalReader.class);
        patrimoineReader = mock(PatrimoineReader.class);
        settingsService = mock(NotificationSettingsService.class);
        channel = mock(NotificationChannel.class);
        sentLogRepository = mock(NotificationSentLogRepository.class);

        service = new NotificationDispatchService(new DebitThresholdRule(), new BalanceFloorRule(),
                new ObjectifReachableRule(), List.of(channel), settingsService, bankReader, settingsReader,
                goalReader, patrimoineReader, sentLogRepository);
    }

    @Test
    @DisplayName("Aucune règle active : aucun port de lecture n'est sollicité")
    void noActiveRuleReadsNothing() {
        enable(false, false, false);

        assertThat(service.runManualCheck()).isZero();

        verifyNoInteractions(bankReader, settingsReader, goalReader, patrimoineReader, channel);
    }

    @Test
    @DisplayName("Débit au-delà du seuil : seul l'import bancaire est lu, l'alerte part sur les canaux")
    void debitThresholdReadsOnlyBankImport() {
        enable(true, false, false);
        when(bankReader.getBankImport()).thenReturn(new BankImportModel(
                List.of(new BankTransactionModel("t1", LocalDate.now().toString(), "Garage", new BigDecimal("-900"))),
                List.of(), List.of()));

        assertThat(service.runManualCheck()).isEqualTo(1);

        verify(channel).send(any(NotificationMessage.class));
        verifyNoInteractions(settingsReader, goalReader, patrimoineReader);
    }

    @Test
    @DisplayName("Solde sous le plancher : le solde de départ vient du port Settings")
    void balanceFloorUsesOpeningBalanceFromSettingsReader() {
        enable(false, true, false);
        when(bankReader.getBankImport()).thenReturn(null);

        when(settingsReader.getSettings()).thenReturn(settingsWithStartBalance("250"));
        assertThat(service.runManualCheck()).isEqualTo(1);

        when(settingsReader.getSettings()).thenReturn(settingsWithStartBalance("5000"));
        assertThat(service.runManualCheck()).isZero();

        verifyNoInteractions(goalReader, patrimoineReader);
    }

    @Test
    @DisplayName("Paramètres absents : le solde de départ vaut zéro, sans erreur")
    void balanceFloorToleratesMissingSettings() {
        enable(false, true, false);
        when(bankReader.getBankImport()).thenReturn(null);
        when(settingsReader.getSettings()).thenReturn(null);

        assertThat(service.runManualCheck()).isEqualTo(1);
    }

    @Test
    @DisplayName("Objectif atteignable : objectifs et placements lus via leurs ports")
    void objectifReachableReadsGoalsAndPlacements() {
        enable(false, false, true);
        when(goalReader.getGoals()).thenReturn(List.of(new ObjectifModel("g1", "Vacances", new BigDecimal("1000"),
                null, null, null, null, List.of(new ObjectifAllocationModel("a1", "p1", new BigDecimal("600"))))));
        when(patrimoineReader.getPlacements()).thenReturn(List.of(new PlacementModel("p1", "Livret", "cat",
                new BigDecimal("700"), "2026-01", BigDecimal.ZERO, null, null, null, null, null, null, "")));

        // 600 couverts sur 1000 visés : non atteignable, mais les deux ports sont bien lus sans erreur.
        assertThat(service.runManualCheck()).isZero();

        verify(goalReader).getGoals();
        verify(patrimoineReader).getPlacements();
        verifyNoInteractions(bankReader, settingsReader);
    }

    @Test
    @DisplayName("Déclenchement automatique après commit : mêmes lectures par ports")
    void automaticTriggerUsesReaders() {
        enable(true, false, false);
        when(bankReader.getBankImport()).thenReturn(new BankImportModel(
                List.of(new BankTransactionModel("t1", LocalDate.now().toString(), "Garage", new BigDecimal("-900"))),
                List.of(), List.of()));

        service.onBudgetMutated(new BudgetMutatedEvent("test"));

        verify(bankReader).getBankImport();
        verify(channel).send(any(NotificationMessage.class));
    }

    @Test
    @DisplayName("Un port en erreur n'empêche pas les autres règles de s'exécuter")
    void failingReaderDoesNotBlockOtherRules() {
        enable(true, false, true);
        when(bankReader.getBankImport()).thenThrow(new IllegalStateException("lecture en échec"));
        when(goalReader.getGoals()).thenReturn(List.of());
        when(patrimoineReader.getPlacements()).thenReturn(List.of());

        assertThat(service.runManualCheck()).isZero();

        verify(goalReader).getGoals();
        verify(channel, never()).send(any(NotificationMessage.class));
    }

    private void enable(boolean debit, boolean floor, boolean objectif) {
        when(settingsService.current()).thenReturn(new NotificationSettingsParameters(
                debit, new BigDecimal("500"), floor, new BigDecimal("1000"), objectif,
                false, "22:00", "07:00"));
    }

    private static SettingsModel settingsWithStartBalance(String startBalance) {
        return new SettingsModel(1985, 64, 85, new BigDecimal("0.02"), "2026-01-01", "manual",
                new BigDecimal(startBalance), 21, new BigDecimal("0.10"));
    }
}
