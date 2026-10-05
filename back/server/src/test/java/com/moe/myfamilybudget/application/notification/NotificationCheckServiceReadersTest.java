package com.moe.myfamilybudget.application.notification;

import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;
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
import com.moe.myfamilybudget.domain.goals.model.ObjectifAllocationModel;
import com.moe.myfamilybudget.domain.goals.model.ObjectifModel;
import com.moe.myfamilybudget.domain.wealth.model.PlacementModel;
import com.moe.myfamilybudget.domain.treasury.model.TresorerieSettingsModel;
import com.moe.myfamilybudget.domain.notifications.core.BalanceFloorRule;
import com.moe.myfamilybudget.domain.notifications.core.DebitThresholdRule;
import com.moe.myfamilybudget.domain.notifications.core.ObjectifReachableRule;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.core.DefaultNotificationDispatchService;
import com.moe.myfamilybudget.domain.notifications.port.NotificationChannel;
import com.moe.myfamilybudget.domain.notifications.port.NotificationSentLogStore;
import com.moe.myfamilybudget.domain.bankpointage.port.BankReader;
import com.moe.myfamilybudget.domain.goals.port.GoalReader;
import com.moe.myfamilybudget.domain.wealth.port.PatrimoineReader;
import com.moe.myfamilybudget.domain.treasury.port.TresorerieSettingsReader;

/**
 * NOTIF-010, déplacé par SILO-180 : {@code NotificationCheckService} (application) lit le budget uniquement via
 * les ports de lecture, et seulement pour les règles actives (le dispatch du silo n'appelle l'assemblage d'une
 * règle que si elle est active). Les règles et la factory restent testées séparément.
 */
class NotificationCheckServiceReadersTest {

    private BankReader bankReader;
    private TresorerieSettingsReader settingsReader;
    private GoalReader goalReader;
    private PatrimoineReader patrimoineReader;
    private NotificationSettingsService settingsService;
    private NotificationChannel channel;
    private NotificationSentLogStore sentLogStore;
    private NotificationCheckService service;

    @BeforeEach
    void setUp() {
        bankReader = mock(BankReader.class);
        settingsReader = mock(TresorerieSettingsReader.class);
        goalReader = mock(GoalReader.class);
        patrimoineReader = mock(PatrimoineReader.class);
        settingsService = mock(NotificationSettingsService.class);
        channel = mock(NotificationChannel.class);
        sentLogStore = mock(NotificationSentLogStore.class);

        service = new NotificationCheckService(
                new DefaultNotificationDispatchService(new DebitThresholdRule(), new BalanceFloorRule(),
                        new ObjectifReachableRule(), List.of(channel), settingsService, sentLogStore),
                bankReader, settingsReader, goalReader, patrimoineReader);
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
    @DisplayName("Solde sous le plancher : le solde de départ vient du port des paramètres Trésorerie")
    void balanceFloorUsesOpeningBalanceFromTresorerieSettingsReader() {
        enable(false, true, false);
        when(bankReader.getBankImport()).thenReturn(null);

        when(settingsReader.getTresorerieSettings()).thenReturn(settingsWithStartBalance("250"));
        assertThat(service.runManualCheck()).isEqualTo(1);

        when(settingsReader.getTresorerieSettings()).thenReturn(settingsWithStartBalance("5000"));
        assertThat(service.runManualCheck()).isZero();

        verifyNoInteractions(goalReader, patrimoineReader);
    }

    @Test
    @DisplayName("Paramètres absents : le solde de départ vaut zéro, sans erreur")
    void balanceFloorToleratesMissingSettings() {
        enable(false, true, false);
        when(bankReader.getBankImport()).thenReturn(null);
        when(settingsReader.getTresorerieSettings()).thenReturn(null);

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
    @DisplayName("Déclenchement automatique : mêmes lectures par ports")
    void automaticTriggerUsesReaders() {
        enable(true, false, false);
        when(bankReader.getBankImport()).thenReturn(new BankImportModel(
                List.of(new BankTransactionModel("t1", LocalDate.now().toString(), "Garage", new BigDecimal("-900"))),
                List.of(), List.of()));

        assertThat(service.runAutomaticCheck()).isEqualTo(1);

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

    private static TresorerieSettingsModel settingsWithStartBalance(String startBalance) {
        return new TresorerieSettingsModel("2026-01-01", "manual", new BigDecimal(startBalance), false, null, null,
                null);
    }
}
