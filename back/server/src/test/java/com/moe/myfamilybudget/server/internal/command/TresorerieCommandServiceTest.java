package com.moe.myfamilybudget.server.internal.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.server.internal.model.ChargeModel;
import com.moe.myfamilybudget.server.internal.model.IncomeModel;
import com.moe.myfamilybudget.server.internal.persistence.PersistenceManager;
import com.moe.myfamilybudget.server.internal.persistence.adapter.BudgetPersistenceAdapter;
import com.moe.myfamilybudget.server.internal.persistence.adapter.TresoreriePersistenceAdapter;
import com.moe.myfamilybudget.server.internal.port.TresorerieWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-031 -- Le command service Tresorerie valide la commande, delegue au port d'ecriture et laisse
 * l'erreur de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le
 * lecteur Budget.
 */
@DisplayName("DB-031 -- TresorerieCommandService")
class TresorerieCommandServiceTest {

    private TresorerieWriter writer;
    private TresorerieCommandService service;

    @BeforeEach
    void setUp() {
        writer = mock(TresorerieWriter.class);
        service = new TresorerieCommandService(writer);
    }

    // --- succes : delegation au port ---

    @Test
    @DisplayName("succes : ajout, mise a jour, suppression et ajustement sont transmis au port")
    void commandsDelegate() {
        Map<String, Object> body = Map.of("id", "inc_1", "label", "Salaire");
        Map<String, Object> created = Map.of("id", "inc_1");
        when(writer.addTresorerieRow("incomes", body)).thenReturn(created);

        assertThat(service.addTresorerieRow("incomes", body)).isSameAs(created);
        service.updateTresorerieRow("incomes", "inc_1", "monthly", new BigDecimal("3000"));
        service.removeTresorerieRow("incomes", "inc_1");
        service.applyTresorerieAjustement("inc_1", "revenu", new BigDecimal("3200"));

        verify(writer).updateTresorerieRow("incomes", "inc_1", "monthly", new BigDecimal("3000"));
        verify(writer).removeTresorerieRow("incomes", "inc_1");
        verify(writer).applyTresorerieAjustement("inc_1", "revenu", new BigDecimal("3200"));
    }

    @Test
    @DisplayName("succes : un corps null (ajout) et une valeur null (mise a jour) restent acceptes")
    void nullBodyAndNullValueAreAllowed() {
        service.addTresorerieRow("charges", null);
        service.updateTresorerieRow("charges", "chg_1", "end", null);

        verify(writer).addTresorerieRow("charges", null);
        verify(writer).updateTresorerieRow("charges", "chg_1", "end", null);
    }

    // --- validation ---

    @Test
    @DisplayName("validation : un argument obligatoire null est refuse et rien n'est ecrit")
    void nullArgumentsAreRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.addTresorerieRow(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow(null, "inc_1", "monthly", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow("incomes", null, "monthly", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow("incomes", "inc_1", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeTresorerieRow(null, "inc_1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeTresorerieRow("incomes", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement(null, "charge", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("chg_1", null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("chg_1", "charge", null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    // --- propagation d'erreur ---

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        when(writer.addTresorerieRow(anyString(), any())).thenThrow(dbDown);
        doThrow(dbDown).when(writer).updateTresorerieRow(anyString(), anyString(), anyString(), any());
        doThrow(dbDown).when(writer).removeTresorerieRow(anyString(), anyString());
        doThrow(dbDown).when(writer).applyTresorerieAjustement(anyString(), anyString(), any());

        assertThatThrownBy(() -> service.addTresorerieRow("incomes", Map.of())).isSameAs(dbDown);
        assertThatThrownBy(() -> service.updateTresorerieRow("incomes", "inc_1", "monthly", 1)).isSameAs(dbDown);
        assertThatThrownBy(() -> service.removeTresorerieRow("incomes", "inc_1")).isSameAs(dbDown);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("inc_1", "revenu", BigDecimal.TEN))
                .isSameAs(dbDown);
    }

    // --- integration avec l'adaptateur ---

    @Test
    @DisplayName("integration adaptateur : revenus et charges relus par le lecteur Budget")
    void adapterWritesAreReadBackByReader() {
        PersistenceManager persistenceManager = PersistenceManagerTestFactory.inMemory();
        persistenceManager.init();
        BudgetPersistenceAdapter reader = new BudgetPersistenceAdapter(persistenceManager);
        TresorerieCommandService realService =
                new TresorerieCommandService(new TresoreriePersistenceAdapter(persistenceManager));

        realService.addTresorerieRow("incomes",
                Map.of("id", "inc_1", "label", "Salaire", "monthly", new BigDecimal("3000")));
        realService.addTresorerieRow("charges",
                Map.of("id", "chg_1", "label", "Electricite", "monthly", new BigDecimal("120")));
        assertThat(reader.getIncomes()).extracting(IncomeModel::id).containsExactly("inc_1");
        assertThat(reader.getCharges()).extracting(ChargeModel::id).containsExactly("chg_1");

        realService.updateTresorerieRow("incomes", "inc_1", "label", "Salaire net");
        assertThat(reader.getIncomes().get(0).label()).isEqualTo("Salaire net");

        realService.applyTresorerieAjustement("inc_1", "revenu", new BigDecimal("3200"));
        assertThat(reader.getIncomes().get(0).monthly()).isEqualByComparingTo("3200");
        realService.applyTresorerieAjustement("chg_1", "charge", new BigDecimal("145"));
        assertThat(reader.getCharges().get(0).monthly()).isEqualByComparingTo("145");

        realService.removeTresorerieRow("incomes", "inc_1");
        realService.removeTresorerieRow("charges", "chg_1");
        assertThat(reader.getIncomes()).isEmpty();
        assertThat(reader.getCharges()).isEmpty();
    }
}
