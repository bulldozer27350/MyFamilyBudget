package com.moe.myfamilybudget.server.internal.enablebanking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.enablebanking.EnableBankingTransactionMapper.MappedTransactionRow;

/**
 * Les transactions de test reprennent la forme réelle observée dans le POC (voir la conversation
 * ayant mené à ce module) ; les identifiants et montants sont illustratifs.
 */
class EnableBankingTransactionMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode parse(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    @Test
    @DisplayName("map() convertit un prélèvement SEPA débiteur en ligne négative, avec type deviné")
    void mapDebitPrelevement() throws IOException {
        JsonNode tx = parse("""
                {
                  "entry_reference": "000025310007416810",
                  "transaction_amount": {"currency": "EUR", "amount": "45.90"},
                  "credit_debit_indicator": "DBIT",
                  "status": "BOOK",
                  "booking_date": "2026-09-21",
                  "bank_transaction_code": {"description": null, "code": "", "sub_code": null},
                  "remittance_information": ["PRLV SEPA ORANGE SA ECH/210926 ID EMETTEUR/FRXXX MDT/EPCBYYY REF/2XXX LIB/VOTRE ABONNEMENT FIBRE (FACTURE: XXXXX)"]
                }
                """);

        MappedTransactionRow row = EnableBankingTransactionMapper.map(tx).orElseThrow();

        assertEquals("2026-09-21", row.date());
        assertTrue(row.label().startsWith("PRLV SEPA ORANGE SA"));
        assertEquals("PRLV SEPA", row.type());
        assertEquals("-45.90", row.amount());
    }

    @Test
    @DisplayName("map() convertit un virement créditeur en ligne positive")
    void mapCreditVirement() throws IOException {
        JsonNode tx = parse("""
                {
                  "entry_reference": "000025310007416811",
                  "transaction_amount": {"currency": "EUR", "amount": "1200.00"},
                  "credit_debit_indicator": "CRDT",
                  "status": "BOOK",
                  "booking_date": "2026-09-05",
                  "remittance_information": ["VIR SEPA RECU EMPLOYEUR SALAIRE SEPTEMBRE"]
                }
                """);

        MappedTransactionRow row = EnableBankingTransactionMapper.map(tx).orElseThrow();

        assertEquals("1200.00", row.amount());
        assertEquals("VIR RECU", row.type());
    }

    @Test
    @DisplayName("map() reconnaît un virement de compte à compte")
    void mapInternalTransfer() throws IOException {
        JsonNode tx = parse("""
                {
                  "transaction_amount": {"currency": "EUR", "amount": "200.00"},
                  "credit_debit_indicator": "DBIT",
                  "booking_date": "2026-09-10",
                  "remittance_information": ["VIR CPTE A CPTE VERS LIVRET A"]
                }
                """);

        assertEquals("VIR CPTE A CPTE", EnableBankingTransactionMapper.map(tx).orElseThrow().type());
    }

    @Test
    @DisplayName("map() privilégie bank_transaction_code.description quand la banque le renseigne")
    void mapUsesBankProvidedDescriptionWhenPresent() throws IOException {
        JsonNode tx = parse("""
                {
                  "transaction_amount": {"currency": "EUR", "amount": "12.00"},
                  "credit_debit_indicator": "DBIT",
                  "booking_date": "2026-09-12",
                  "bank_transaction_code": {"description": "Paiement carte"},
                  "remittance_information": ["CB SUPERMARCHE"]
                }
                """);

        assertEquals("Paiement carte", EnableBankingTransactionMapper.map(tx).orElseThrow().type());
    }

    @Test
    @DisplayName("map() retombe sur value_date si booking_date est absent")
    void mapFallsBackToValueDate() throws IOException {
        JsonNode tx = parse("""
                {
                  "transaction_amount": {"currency": "EUR", "amount": "5.00"},
                  "credit_debit_indicator": "DBIT",
                  "value_date": "2026-09-01",
                  "remittance_information": ["FRAIS TENUE DE COMPTE"]
                }
                """);

        MappedTransactionRow row = EnableBankingTransactionMapper.map(tx).orElseThrow();
        assertEquals("2026-09-01", row.date());
        assertEquals("FRAIS BANCAIRES", row.type());
    }

    @Test
    @DisplayName("map() renvoie vide sans date ni sans montant exploitable")
    void mapMissingRequiredFields() throws IOException {
        assertEquals(Optional.empty(), EnableBankingTransactionMapper.map(parse(
                "{\"transaction_amount\": {\"amount\": \"1.00\"}, \"credit_debit_indicator\": \"DBIT\"}")));
        assertEquals(Optional.empty(), EnableBankingTransactionMapper.map(parse(
                "{\"booking_date\": \"2026-09-01\"}")));
    }
}
