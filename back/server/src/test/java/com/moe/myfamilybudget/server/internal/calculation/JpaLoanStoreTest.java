package com.moe.myfamilybudget.server.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.moe.myfamilybudget.domain.credit.core.persistence.JpaLoanStore;
import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.credit.model.LoansMutatedEvent;
import com.moe.myfamilybudget.server.internal.testsupport.InMemoryLoanRepository;

/**
 * SILO-214 (lot B) -- Adaptateur du silo Credit : parite avec l'ancienne mutation generique
 * ({@code savePatrimoineRow("loans", ...)}) et publication d'un evenement apres chaque ecriture. Le repository
 * est en memoire ; le round-trip contre une vraie base est couvert par {@code PersistenceAdaptersJpaRoundTripTest}.
 */
@DisplayName("SILO-214 -- JpaLoanStore")
class JpaLoanStoreTest {

    private final List<Object> events = new ArrayList<>();
    private JpaLoanStore store;

    @BeforeEach
    void setUp() {
        store = new JpaLoanStore(InMemoryLoanRepository.create(), events::add);
    }

    @Test
    @DisplayName("creation : valeurs par defaut, identifiant genere et ligne renvoyee comme l'ancienne mutation")
    void createsWithDefaultsAndGeneratedId() {
        Map<String, Object> row = store.saveLoanRow(null);

        String id = String.valueOf(row.get("id"));
        assertThat(id).startsWith("pat_").hasSize(12);
        assertThat(row.get("label")).isEqualTo("Nouveau prêt");
        assertThat((BigDecimal) row.get("crd")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("rate")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("monthly")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("insurance")).isEqualByComparingTo("0");
        assertThat(row.get("startDate")).isEqualTo("2026-01-01");
        assertThat(row.get("endDate")).isEqualTo("2046-01-01");
        assertThat(row.get("initialAmount")).isNull();
        assertThat(row.get("totalInstallments")).isNull();
        assertThat(row.get("stepDate")).isNull();
        assertThat(store.getLoans()).singleElement().satisfies(loan -> assertThat(loan.id()).isEqualTo(id));
    }

    @Test
    @DisplayName("un identifiant vide est traite comme absent")
    void blankIdIsGenerated() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "   ");

        assertThat(String.valueOf(store.saveLoanRow(body).get("id"))).startsWith("pat_");
    }

    @Test
    @DisplayName("les nombres sont lus comme avant : nombre, texte avec virgule, valeur illisible = defaut")
    void readsNumbersLeniently() {
        store.saveLoanRow(Map.of("id", "l_number", "crd", 1200.5, "totalInstallments", 240.0));
        store.saveLoanRow(Map.of("id", "l_text", "crd", "3000,25", "totalInstallments", "180"));
        store.saveLoanRow(Map.of("id", "l_bad", "crd", "abc", "totalInstallments", "xyz"));

        assertThat(loan("l_number").crd()).isEqualByComparingTo("1200.5");
        assertThat(loan("l_number").totalInstallments()).isEqualTo(240);
        assertThat(loan("l_text").crd()).isEqualByComparingTo("3000.25");
        assertThat(loan("l_text").totalInstallments()).isEqualTo(180);
        assertThat(loan("l_bad").crd()).isEqualByComparingTo("0");
        assertThat(loan("l_bad").totalInstallments()).isNull();
    }

    @Test
    @DisplayName("une date d'echelon vide est lue comme absente")
    void blankStepDateIsAbsent() {
        Map<String, Object> row = store.saveLoanRow(Map.of("id", "l_1", "stepDate", "  "));

        assertThat(row.get("stepDate")).isNull();
        assertThat(loan("l_1").stepDate()).isNull();
    }

    @Test
    @DisplayName("reedition : le pret garde sa place, un nouveau pret est ajoute a la fin")
    void editingKeepsPositionAndNewLoansAreAppended() {
        store.saveLoanRow(Map.of("id", "loan_1", "label", "A"));
        store.saveLoanRow(Map.of("id", "loan_2", "label", "B"));

        store.saveLoanRow(Map.of("id", "loan_1", "label", "A bis"));
        store.saveLoanRow(Map.of("id", "loan_3", "label", "C"));

        assertThat(store.getLoans()).extracting(LoanModel::id).containsExactly("loan_1", "loan_2", "loan_3");
        assertThat(store.getLoans().get(0).label()).isEqualTo("A bis");
    }

    @Test
    @DisplayName("suppression : seul le pret vise disparait ; un identifiant inconnu ne change rien")
    void deleteRemovesOnlyTheTarget() {
        store.saveLoanRow(Map.of("id", "loan_1"));
        store.saveLoanRow(Map.of("id", "loan_2"));

        store.deleteLoanRow("unknown");
        assertThat(store.getLoans()).extracting(LoanModel::id).containsExactly("loan_1", "loan_2");

        store.deleteLoanRow("loan_1");
        assertThat(store.getLoans()).extracting(LoanModel::id).containsExactly("loan_2");
    }

    @Test
    @DisplayName("replace : remplace tout, ignore les prets sans identifiant, null = vide ; reset vide")
    void replaceAndReset() {
        store.saveLoanRow(Map.of("id", "old"));

        store.replace(List.of(loanModel("loan_1"), loanModel(null)));
        assertThat(store.getLoans()).extracting(LoanModel::id).containsExactly("loan_1");

        store.replace(null);
        assertThat(store.getLoans()).isEmpty();

        store.replace(List.of(loanModel("loan_2")));
        store.reset();
        assertThat(store.getLoans()).isEmpty();
    }

    @Test
    @DisplayName("chaque ecriture publie un evenement de mutation, une lecture n'en publie pas")
    void publishesOneEventPerWrite() {
        store.getLoans();
        assertThat(events).isEmpty();

        store.saveLoanRow(Map.of("id", "loan_1"));
        store.deleteLoanRow("loan_1");
        store.replace(List.of());
        store.reset();

        assertThat(events).containsExactly(
                new LoansMutatedEvent("saveLoanRow"),
                new LoansMutatedEvent("deleteLoanRow"),
                new LoansMutatedEvent("replace"),
                new LoansMutatedEvent("reset"));
    }

    private LoanModel loan(String id) {
        return store.getLoans().stream()
                .filter(loan -> id.equals(loan.id()))
                .findFirst()
                .orElseThrow();
    }

    private static LoanModel loanModel(String id) {
        return new LoanModel(id, "Pret", new BigDecimal("1000"), new BigDecimal("1.5"), new BigDecimal("50"),
                BigDecimal.ZERO, "2026-01-01", "2036-01-01", null, null, null);
    }
}
