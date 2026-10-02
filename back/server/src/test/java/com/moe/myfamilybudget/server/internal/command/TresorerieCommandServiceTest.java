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
import com.moe.myfamilybudget.server.internal.port.TresorerieAdjustmentKind;
import com.moe.myfamilybudget.server.internal.port.TresorerieLineField;
import com.moe.myfamilybudget.server.internal.port.TresorerieList;
import com.moe.myfamilybudget.server.internal.port.TresorerieWriter;
import com.moe.myfamilybudget.server.internal.testsupport.PersistenceManagerTestFactory;

/**
 * DB-031 -- Le command service Tresorerie valide la commande, delegue au port d'ecriture et laisse
 * l'erreur de persistance remonter telle quelle ; l'adaptateur ecrit bien dans le modele relu par le
 * lecteur Budget.
 */
@DisplayName("DB-031 / DB-050 -- TresorerieCommandService")
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
        when(writer.addTresorerieRow(TresorerieList.INCOMES, body)).thenReturn(created);

        assertThat(service.addTresorerieRow(TresorerieList.INCOMES, body)).isSameAs(created);
        service.updateTresorerieRow(TresorerieList.INCOMES, "inc_1", TresorerieLineField.MONTHLY, new BigDecimal("3000"));
        service.removeTresorerieRow(TresorerieList.INCOMES, "inc_1");
        service.applyTresorerieAjustement("inc_1", TresorerieAdjustmentKind.INCOME, new BigDecimal("3200"));

        verify(writer).updateTresorerieRow(TresorerieList.INCOMES, "inc_1", TresorerieLineField.MONTHLY, new BigDecimal("3000"));
        verify(writer).removeTresorerieRow(TresorerieList.INCOMES, "inc_1");
        verify(writer).applyTresorerieAjustement("inc_1", TresorerieAdjustmentKind.INCOME, new BigDecimal("3200"));
    }

    @Test
    @DisplayName("succes : un corps null (ajout) et une valeur null (mise a jour) restent acceptes")
    void nullBodyAndNullValueAreAllowed() {
        service.addTresorerieRow(TresorerieList.CHARGES, null);
        service.updateTresorerieRow(TresorerieList.CHARGES, "chg_1", TresorerieLineField.END, null);

        verify(writer).addTresorerieRow(TresorerieList.CHARGES, null);
        verify(writer).updateTresorerieRow(TresorerieList.CHARGES, "chg_1", TresorerieLineField.END, null);
    }


    // --- DB-050 : enums interpretes a la frontiere REST ---

    @Test
    @DisplayName("DB-050 : TresorerieList.fromKey est insensible a la casse et couvre les six listes")
    void listFromKeyIsCaseInsensitive() {
        assertThat(TresorerieList.fromKey("incomes")).isEqualTo(TresorerieList.INCOMES);
        assertThat(TresorerieList.fromKey("CHARGES")).isEqualTo(TresorerieList.CHARGES);
        assertThat(TresorerieList.fromKey("oneoff")).isEqualTo(TresorerieList.ONEOFF);
        assertThat(TresorerieList.fromKey("variableincomes")).isEqualTo(TresorerieList.VARIABLE_INCOMES);
        assertThat(TresorerieList.fromKey("variableOverrides")).isEqualTo(TresorerieList.VARIABLE_OVERRIDES);
        assertThat(TresorerieList.fromKey("Placements")).isEqualTo(TresorerieList.PLACEMENTS);
    }

    @Test
    @DisplayName("DB-050 : une liste inconnue ou null est refusee avant toute ecriture")
    void unknownListIsRejected() {
        assertThatThrownBy(() -> TresorerieList.fromKey("inconnue"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TresorerieList.fromKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DB-050 : TresorerieAdjustmentKind.fromKind conserve le mapping historique")
    void adjustmentKindFromKind() {
        assertThat(TresorerieAdjustmentKind.fromKind("charge")).isEqualTo(TresorerieAdjustmentKind.CHARGE);
        assertThat(TresorerieAdjustmentKind.fromKind("revenu")).isEqualTo(TresorerieAdjustmentKind.INCOME);
        assertThat(TresorerieAdjustmentKind.fromKind("income")).isEqualTo(TresorerieAdjustmentKind.INCOME);
        assertThat(TresorerieAdjustmentKind.fromKind("placement")).isEqualTo(TresorerieAdjustmentKind.PLACEMENT);
        assertThat(TresorerieAdjustmentKind.fromKind("autre")).isEqualTo(TresorerieAdjustmentKind.PLACEMENT);
        assertThatThrownBy(() -> TresorerieAdjustmentKind.fromKind(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DB-050 : TresorerieLineField.find respecte la casse, couvre toutes les cles des updaters et ignore l'inconnu")
    void lineFieldFind() {
        assertThat(TresorerieLineField.find("monthly")).contains(TresorerieLineField.MONTHLY);
        assertThat(TresorerieLineField.find("excludedFromRetirement")).contains(TresorerieLineField.EXCLUDED_FROM_RETIREMENT);
        assertThat(TresorerieLineField.find("pausePriority")).contains(TresorerieLineField.PAUSE_PRIORITY);
        assertThat(TresorerieLineField.find("Monthly")).isEmpty();
        assertThat(TresorerieLineField.find("inconnu")).isEmpty();
        assertThat(TresorerieLineField.find(null)).isEmpty();
        assertThat(TresorerieLineField.values()).hasSize(29);
    }

    // --- validation ---

    @Test
    @DisplayName("validation : un argument obligatoire null est refuse et rien n'est ecrit")
    void nullArgumentsAreRejectedWithoutWriting() {
        assertThatThrownBy(() -> service.addTresorerieRow(null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow(null, "inc_1", TresorerieLineField.MONTHLY, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow(TresorerieList.INCOMES, null, TresorerieLineField.MONTHLY, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updateTresorerieRow(TresorerieList.INCOMES, "inc_1", null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeTresorerieRow(null, "inc_1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.removeTresorerieRow(TresorerieList.INCOMES, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement(null, TresorerieAdjustmentKind.CHARGE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("chg_1", null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("chg_1", TresorerieAdjustmentKind.CHARGE, null))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(writer);
    }

    // --- propagation d'erreur ---

    @Test
    @DisplayName("erreur : l'exception du port est propagee telle quelle")
    void writerFailuresArePropagated() {
        IllegalStateException dbDown = new IllegalStateException("database down");
        when(writer.addTresorerieRow(any(TresorerieList.class), any())).thenThrow(dbDown);
        doThrow(dbDown).when(writer).updateTresorerieRow(any(TresorerieList.class), anyString(), any(TresorerieLineField.class), any());
        doThrow(dbDown).when(writer).removeTresorerieRow(any(TresorerieList.class), anyString());
        doThrow(dbDown).when(writer).applyTresorerieAjustement(anyString(), any(TresorerieAdjustmentKind.class), any());

        assertThatThrownBy(() -> service.addTresorerieRow(TresorerieList.INCOMES, Map.of())).isSameAs(dbDown);
        assertThatThrownBy(() -> service.updateTresorerieRow(TresorerieList.INCOMES, "inc_1", TresorerieLineField.MONTHLY, 1)).isSameAs(dbDown);
        assertThatThrownBy(() -> service.removeTresorerieRow(TresorerieList.INCOMES, "inc_1")).isSameAs(dbDown);
        assertThatThrownBy(() -> service.applyTresorerieAjustement("inc_1", TresorerieAdjustmentKind.INCOME, BigDecimal.TEN))
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

        realService.addTresorerieRow(TresorerieList.INCOMES,
                Map.of("id", "inc_1", "label", "Salaire", "monthly", new BigDecimal("3000")));
        realService.addTresorerieRow(TresorerieList.CHARGES,
                Map.of("id", "chg_1", "label", "Electricite", "monthly", new BigDecimal("120")));
        assertThat(reader.getIncomes()).extracting(IncomeModel::id).containsExactly("inc_1");
        assertThat(reader.getCharges()).extracting(ChargeModel::id).containsExactly("chg_1");

        realService.updateTresorerieRow(TresorerieList.INCOMES, "inc_1", TresorerieLineField.LABEL, "Salaire net");
        assertThat(reader.getIncomes().get(0).label()).isEqualTo("Salaire net");

        realService.applyTresorerieAjustement("inc_1", TresorerieAdjustmentKind.INCOME, new BigDecimal("3200"));
        assertThat(reader.getIncomes().get(0).monthly()).isEqualByComparingTo("3200");
        realService.applyTresorerieAjustement("chg_1", TresorerieAdjustmentKind.CHARGE, new BigDecimal("145"));
        assertThat(reader.getCharges().get(0).monthly()).isEqualByComparingTo("145");

        realService.removeTresorerieRow(TresorerieList.INCOMES, "inc_1");
        realService.removeTresorerieRow(TresorerieList.CHARGES, "chg_1");
        assertThat(reader.getIncomes()).isEmpty();
        assertThat(reader.getCharges()).isEmpty();
    }
}
