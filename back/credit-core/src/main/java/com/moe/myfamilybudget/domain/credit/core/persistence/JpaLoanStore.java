package com.moe.myfamilybudget.domain.credit.core.persistence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.moe.myfamilybudget.domain.credit.model.LoanModel;
import com.moe.myfamilybudget.domain.credit.model.LoansMutatedEvent;
import com.moe.myfamilybudget.domain.credit.port.LoanReader;
import com.moe.myfamilybudget.domain.credit.port.LoanSnapshotWriter;
import com.moe.myfamilybudget.domain.credit.port.LoanWriter;

/**
 * Adaptateur JPA du silo Credit (SILO-214, lot B) : implemente {@link LoanReader}, {@link LoanWriter} et
 * {@link LoanSnapshotWriter} directement sur la table {@code credit_loan}, sans passer par le cache global ni par
 * le {@code PersistenceManager}.
 *
 * <p>Les ecritures ne s'appellent que dans une transaction deja ouverte ({@code TransactionRunner}) et apres la
 * prise du verrou du silo ({@code MutationSilo.CREDIT}) par l'appelant : l'adaptateur ne porte ni
 * {@code @Transactional} ni verrou. La lecture de la liste courante et sa reecriture se font donc sous ce verrou
 * (pas de perte d'une modification concurrente). Chaque ecriture remplace le contenu de la table par la liste
 * resultante (suppression, {@code flush}, insertion), comme l'ancienne synchronisation du modele global : le
 * {@code flush} est indispensable, Hibernate executant les insertions avant les suppressions, ce qui violerait la
 * cle primaire metier lors du remplacement d'un pret existant. Les positions sont ainsi toujours contigues.
 *
 * <p>Parite avec l'ancienne mutation generique ({@code savePatrimoineRow("loans", ...)}) : memes valeurs par
 * defaut, meme identifiant genere, meme ligne renvoyee.
 *
 * <p>Apres chaque ecriture, un {@link LoansMutatedEvent} est publie : l'ecoute apres commit declenche le controle
 * automatique des notifications, comme le faisait {@code BudgetMutatedEvent}.
 */
@Component
public class JpaLoanStore implements LoanReader, LoanWriter, LoanSnapshotWriter {

    private static final Logger LOG = LoggerFactory.getLogger(JpaLoanStore.class);

    private final CreditLoanRepository creditLoanRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JpaLoanStore(CreditLoanRepository creditLoanRepository, ApplicationEventPublisher eventPublisher) {
        this.creditLoanRepository = creditLoanRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<LoanModel> getLoans() {
        return CreditLoanEntityMapper.toModels(creditLoanRepository.findAllByOrderByPositionAsc());
    }

    @Override
    public Map<String, Object> saveLoanRow(Map<String, Object> body) {
        String givenId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String uid = (givenId != null && !givenId.trim().isEmpty())
                ? givenId
                : ("pat_" + UUID.randomUUID().toString().substring(0, 8));

        String label = getString(body, "label", "Nouveau prêt");
        BigDecimal crd = getBigDecimal(body, "crd", BigDecimal.ZERO);
        BigDecimal rate = getBigDecimal(body, "rate", BigDecimal.ZERO);
        BigDecimal monthly = getBigDecimal(body, "monthly", BigDecimal.ZERO);
        BigDecimal insurance = getBigDecimal(body, "insurance", BigDecimal.ZERO);
        String startDate = getString(body, "startDate", "2026-01-01");
        String endDate = getString(body, "endDate", "2046-01-01");
        // Informations du contrat bancaire : optionnelles, absentes tant qu'elles ne sont pas saisies.
        BigDecimal initialAmount = getBigDecimal(body, "initialAmount", null);
        Integer totalInstallments = getInteger(body, "totalInstallments", null);
        String stepDate = getString(body, "stepDate", null);
        if (stepDate != null && stepDate.isBlank()) {
            stepDate = null;
        }

        LoanModel model = new LoanModel(uid, label, crd, rate, monthly, insurance, startDate, endDate,
                initialAmount, totalInstallments, stepDate);

        List<LoanModel> list = new ArrayList<>();
        boolean found = false;
        for (LoanModel existing : getLoans()) {
            if (Objects.equals(existing.id(), uid)) {
                list.add(model);
                found = true;
            } else {
                list.add(existing);
            }
        }
        if (!found) {
            list.add(model);
        }
        rewrite(list);
        publishMutated("saveLoanRow");

        Map<String, Object> resultRow = new HashMap<>();
        resultRow.put("id", uid);
        resultRow.put("label", label);
        resultRow.put("crd", crd);
        resultRow.put("rate", rate);
        resultRow.put("monthly", monthly);
        resultRow.put("insurance", insurance);
        resultRow.put("startDate", startDate);
        resultRow.put("endDate", endDate);
        resultRow.put("initialAmount", initialAmount);
        resultRow.put("totalInstallments", totalInstallments);
        resultRow.put("stepDate", stepDate);
        return resultRow;
    }

    @Override
    public void deleteLoanRow(String id) {
        List<LoanModel> remaining = getLoans().stream()
                .filter(loan -> !Objects.equals(loan.id(), id))
                .toList();
        rewrite(remaining);
        publishMutated("deleteLoanRow");
    }

    /** SILO-119 (lot B1) : import des prets ; une liste {@code null} est lue comme vide. */
    @Override
    public void replace(List<LoanModel> loans) {
        rewrite(loans == null ? List.of() : loans);
        publishMutated("replace");
    }

    /** SILO-119 (lot B1) : suppression de tous les prets. */
    @Override
    public void reset() {
        rewrite(List.of());
        publishMutated("reset");
    }

    /**
     * Remplace le contenu de la table par {@code loans}. Un pret sans identifiant ne peut etre adresse par aucune
     * API : il n'est pas copie dans la table (avertissement journalise).
     */
    private void rewrite(List<LoanModel> loans) {
        creditLoanRepository.deleteAll();
        creditLoanRepository.flush();
        if (loans.isEmpty()) {
            return;
        }
        List<LoanModel> identified = loans.stream()
                .filter(loan -> loan != null && loan.id() != null)
                .toList();
        if (identified.size() != loans.size()) {
            LOG.warn("{} pret(s) sans identifiant ignore(s) lors de l'ecriture de la table Credit",
                    loans.size() - identified.size());
        }
        creditLoanRepository.saveAll(CreditLoanEntityMapper.toEntities(identified));
    }

    private void publishMutated(String mutationKind) {
        eventPublisher.publishEvent(new LoansMutatedEvent(mutationKind));
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(map.get(key));
    }

    private static BigDecimal getBigDecimal(Map<String, Object> map, String key, BigDecimal defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toBigDecimal(map.get(key), defaultValue);
    }

    private static Integer getInteger(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return defaultValue;
        }
        return toInteger(map.get(key), defaultValue);
    }

    private static BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            String text = String.valueOf(value).trim().replace(",", ".");
            if (text.isEmpty()) {
                return fallback;
            }
            return new BigDecimal(text);
        } catch (Exception e) {
            LOG.warn("Valeur numerique decimale illisible, valeur par defaut '{}' utilisee : '{}'", fallback, value, e);
            return fallback;
        }
    }

    private static Integer toInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            return Integer.parseInt(text);
        } catch (Exception e) {
            LOG.warn("Valeur entiere illisible, valeur par defaut '{}' utilisee : '{}'", fallback, value, e);
            return fallback;
        }
    }
}
