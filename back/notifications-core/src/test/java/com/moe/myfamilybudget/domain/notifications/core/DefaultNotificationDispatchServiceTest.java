package com.moe.myfamilybudget.domain.notifications.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.notifications.calculation.BalanceFloorInput;
import com.moe.myfamilybudget.domain.notifications.calculation.DebitThresholdInput;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationDispatchService.RuleInputs;
import com.moe.myfamilybudget.domain.notifications.calculation.NotificationSettingsService;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.port.NotificationChannel;
import com.moe.myfamilybudget.domain.notifications.port.NotificationSentLogStore;
import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;

/**
 * SILO-180 : le dispatch du silo Notifications (règles actives, plage silencieuse, déduplication 24 h, canaux)
 * sans aucune dépendance à Spring, à la persistance ni aux autres silos.
 */
class DefaultNotificationDispatchServiceTest {

    private final List<NotificationMessage> sentMessages = new ArrayList<>();
    private final Map<String, Instant> sentLog = new HashMap<>();
    private final AtomicInteger debitAssemblies = new AtomicInteger();
    private final AtomicInteger otherAssemblies = new AtomicInteger();
    private NotificationSettingsParameters settings;
    private List<NotificationChannel> channels;
    private DefaultNotificationDispatchService service;

    @BeforeEach
    void setUp() {
        settings = settings(true, false, false);
        channels = List.of(new RecordingChannel());
        NotificationSettingsService settingsService = new NotificationSettingsService() {
            @Override
            public NotificationSettingsParameters current() {
                return settings;
            }

            @Override
            public NotificationSettingsParameters defaults() {
                return NotificationSettingsParameters.defaults();
            }

            @Override
            public NotificationSettingsParameters save(NotificationSettingsParameters parameters) {
                throw new UnsupportedOperationException();
            }
        };
        NotificationSentLogStore sentLogStore = new NotificationSentLogStore() {
            @Override
            public Optional<Instant> findLastSentAt(String dedupKey) {
                return Optional.ofNullable(sentLog.get(dedupKey));
            }

            @Override
            public void markSent(String dedupKey, Instant sentAt) {
                sentLog.put(dedupKey, sentAt);
            }
        };
        service = new DefaultNotificationDispatchService(new DebitThresholdRule(), new BalanceFloorRule(),
                new ObjectifReachableRule(), channels, settingsService, sentLogStore);
    }

    @Test
    @DisplayName("Aucune règle active : aucun assemblage d'entrée, aucun envoi")
    void noActiveRuleAssemblesNothing() {
        settings = settings(false, false, false);

        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isZero();

        assertThat(debitAssemblies.get() + otherAssemblies.get()).isZero();
        assertThat(sentMessages).isEmpty();
    }

    @Test
    @DisplayName("Règle de débit active : seule son entrée est assemblée, l'alerte part sur le canal")
    void onlyTheActiveRuleIsAssembled() {
        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isEqualTo(1);

        assertThat(debitAssemblies.get()).isEqualTo(1);
        assertThat(otherAssemblies.get()).isZero();
        assertThat(sentMessages).hasSize(1);
    }

    @Test
    @DisplayName("Déclenchement automatique : une alerte déjà envoyée il y a moins de 24 h n'est pas renvoyée")
    void automaticCheckDeduplicatesOver24Hours() {
        assertThat(service.runAutomaticCheck(inputsWithDebitAbove500())).isEqualTo(1);
        assertThat(service.runAutomaticCheck(inputsWithDebitAbove500())).isZero();
        assertThat(sentMessages).hasSize(1);

        sentLog.replaceAll((key, at) -> Instant.now().minus(Duration.ofHours(25)));
        assertThat(service.runAutomaticCheck(inputsWithDebitAbove500())).isEqualTo(1);
    }

    @Test
    @DisplayName("Déclenchement manuel : renvoie malgré la déduplication")
    void manualCheckIgnoresDeduplication() {
        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isEqualTo(1);
        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isEqualTo(1);
    }

    @Test
    @DisplayName("Plage silencieuse : le contrôle automatique est sauté, le manuel envoie")
    void quietHoursSkipAutomaticCheckOnly() {
        String start = LocalTime.now().minusHours(1).withSecond(0).withNano(0).toString();
        String end = LocalTime.now().plusHours(1).withSecond(0).withNano(0).toString();
        settings = new NotificationSettingsParameters(true, new BigDecimal("500"), false, new BigDecimal("1000"),
                false, true, start, end);

        assertThat(service.runAutomaticCheck(inputsWithDebitAbove500())).isZero();
        assertThat(debitAssemblies.get()).isZero();
        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isEqualTo(1);
    }

    @Test
    @DisplayName("Un assemblage en erreur n'empêche pas les autres règles de s'exécuter")
    void failingAssemblyDoesNotBlockOtherRules() {
        settings = settings(true, true, false);
        RuleInputs inputs = new RuleInputs(
                s -> {
                    throw new IllegalStateException("lecture en échec");
                },
                s -> {
                    otherAssemblies.incrementAndGet();
                    return new BalanceFloorInput(new BigDecimal("1000"), new BigDecimal("250"), List.of(), List.of());
                },
                s -> null);

        assertThat(service.runManualCheck(inputs)).isEqualTo(1);
        assertThat(otherAssemblies.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un canal en échec n'empêche pas les autres canaux d'envoyer")
    void failingChannelDoesNotBlockOtherChannels() {
        channels = List.of(new FailingChannel(), new RecordingChannel());
        service = new DefaultNotificationDispatchService(new DebitThresholdRule(), new BalanceFloorRule(),
                new ObjectifReachableRule(), channels, new FixedSettings(settings), new NotificationSentLogStore() {
                    @Override
                    public Optional<Instant> findLastSentAt(String dedupKey) {
                        return Optional.empty();
                    }

                    @Override
                    public void markSent(String dedupKey, Instant sentAt) {
                    }
                });

        assertThat(service.runManualCheck(inputsWithDebitAbove500())).isEqualTo(1);
        assertThat(sentMessages).hasSize(1);
    }

    private RuleInputs inputsWithDebitAbove500() {
        return new RuleInputs(
                s -> {
                    debitAssemblies.incrementAndGet();
                    return new DebitThresholdInput(s.debitThresholdAmount(), List.of(
                            new DebitThresholdInput.Transaction("t1", LocalDate.now().toString(), "Garage",
                                    new BigDecimal("-900"))));
                },
                s -> {
                    otherAssemblies.incrementAndGet();
                    return null;
                },
                s -> {
                    otherAssemblies.incrementAndGet();
                    return null;
                });
    }

    private static NotificationSettingsParameters settings(boolean debit, boolean floor, boolean objectif) {
        return new NotificationSettingsParameters(debit, new BigDecimal("500"), floor, new BigDecimal("1000"),
                objectif, false, "22:00", "07:00");
    }

    private final class RecordingChannel implements NotificationChannel {
        @Override
        public String key() {
            return "recording";
        }

        @Override
        public void send(NotificationMessage message) {
            sentMessages.add(message);
        }
    }

    private static final class FailingChannel implements NotificationChannel {
        @Override
        public String key() {
            return "failing";
        }

        @Override
        public void send(NotificationMessage message) {
            throw new IllegalStateException("canal en échec");
        }
    }

    private record FixedSettings(NotificationSettingsParameters value) implements NotificationSettingsService {
        @Override
        public NotificationSettingsParameters current() {
            return value;
        }

        @Override
        public NotificationSettingsParameters defaults() {
            return NotificationSettingsParameters.defaults();
        }

        @Override
        public NotificationSettingsParameters save(NotificationSettingsParameters parameters) {
            throw new UnsupportedOperationException();
        }
    }
}
