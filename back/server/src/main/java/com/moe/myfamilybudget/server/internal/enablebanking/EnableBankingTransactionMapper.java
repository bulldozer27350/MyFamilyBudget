package com.moe.myfamilybudget.server.internal.enablebanking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Convertit une transaction Enable Banking (JSON) en ligne {@code (date, libellé, type, montant)}
 * dans le format attendu par {@code BankImportCalculator.importTransactions}
 * ({@code colRoles = ["date", "label", "type", "amount"]}).
 */
final class EnableBankingTransactionMapper {

    private static final Logger log = LoggerFactory.getLogger(EnableBankingTransactionMapper.class);

    /**
     * Heuristique de détection du type d'opération à partir du libellé, faute de
     * {@code bank_transaction_code} exploitable pour toutes les banques. À ajuster selon les
     * libellés réellement observés (voir le README du projet).
     */
    private static final List<TypePattern> TYPE_PATTERNS = List.of(
            new TypePattern("^VIR(EMENT)?\\s+SEPA\\s+RECU", "VIR RECU"),
            new TypePattern("^VIR(EMENT)?\\s+SEPA", "VIR SEPA"),
            new TypePattern("^VIR(EMENT)?\\s+(INTERNE|CPTE\\s*A\\s*CPTE|DE\\s+COMPTE\\s+A\\s+COMPTE)", "VIR CPTE A CPTE"),
            new TypePattern("^VIR(EMENT)?\\b", "VIREMENT"),
            new TypePattern("^PRLV(EVEMENT)?\\s+SEPA", "PRLV SEPA"),
            new TypePattern("^PRLV(EVEMENT)?\\b", "PRELEVEMENT"),
            new TypePattern("^(CB|CARTE)\\b", "CARTE"),
            new TypePattern("^RET(RAIT)?\\s+DAB", "RETRAIT DAB"),
            new TypePattern("^CHQ|^CHEQUE", "CHEQUE"),
            new TypePattern("^FRAIS\\b", "FRAIS BANCAIRES"),
            new TypePattern("^COTIS(ATION)?\\b", "COTISATION"),
            new TypePattern("^VERSEMENT\\b", "VERSEMENT"),
            new TypePattern("^INTERETS?\\b", "INTERETS"));

    private record TypePattern(Pattern pattern, String type) {
        TypePattern(String regex, String type) {
            this(Pattern.compile(regex), type);
        }
    }

    record MappedTransactionRow(String date, String label, String type, String amount) {
    }

    private EnableBankingTransactionMapper() {
    }

    /** @return la ligne mappée, ou vide si la transaction n'a ni date ni montant exploitable. */
    static Optional<MappedTransactionRow> map(JsonNode tx) {
        String bookingDate = textOrNull(tx, "booking_date");
        if (bookingDate == null) {
            bookingDate = textOrNull(tx, "value_date");
        }
        if (bookingDate == null) {
            log.warn("Transaction sans date ignorée : {}", textOrNull(tx, "entry_reference"));
            return Optional.empty();
        }

        String label = buildLabel(tx);

        JsonNode amountNode = tx.path("transaction_amount").get("amount");
        if (amountNode == null || amountNode.isNull()) {
            log.warn("Transaction sans montant ignorée : {}", textOrNull(tx, "entry_reference"));
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(amountNode.asText());
        if ("DBIT".equals(textOrNull(tx, "credit_debit_indicator"))) {
            amount = amount.negate();
        }

        String codeDescription = textOrNull(tx.path("bank_transaction_code"), "description");
        String type = (codeDescription != null && !codeDescription.isBlank())
                ? codeDescription.trim()
                : guessType(label);

        return Optional.of(new MappedTransactionRow(bookingDate, label, type, amount.toPlainString()));
    }

    private static String buildLabel(JsonNode tx) {
        JsonNode remittance = tx.get("remittance_information");
        StringBuilder label = new StringBuilder();
        if (remittance != null && remittance.isArray()) {
            for (JsonNode part : remittance) {
                String text = part.asText("").trim();
                if (!text.isEmpty()) {
                    if (label.length() > 0) {
                        label.append(' ');
                    }
                    label.append(text);
                }
            }
        }
        if (label.length() == 0) {
            String reference = textOrNull(tx, "entry_reference");
            return reference != null ? reference : "(sans libellé)";
        }
        return label.toString();
    }

    private static String guessType(String label) {
        String upper = label.toUpperCase();
        for (TypePattern candidate : TYPE_PATTERNS) {
            if (candidate.pattern().matcher(upper).find()) {
                return candidate.type();
            }
        }
        return "AUTRE";
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}
