package com.moe.myfamilybudget.server.internal.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimpleCsvTest {

    @Test
    @DisplayName("Champs simples, champs vides et fin de fichier sans saut de ligne")
    void plainFields() {
        List<List<String>> rows = SimpleCsv.parse("a,b,c\n1,,3");

        assertEquals(List.of(List.of("a", "b", "c"), List.of("1", "", "3")), rows);
    }

    @Test
    @DisplayName("Un champ entre guillemets peut contenir des virgules et des guillemets doublés")
    void quotedFields() {
        List<List<String>> rows = SimpleCsv.parse("x,\"Euro area, nominal\",\"dit \"\"oui\"\"\",z\n");

        assertEquals(List.of(List.of("x", "Euro area, nominal", "dit \"oui\"", "z")), rows);
    }

    @Test
    @DisplayName("Fins de ligne CRLF et lignes vides ignorées")
    void crlfAndBlankLines() {
        List<List<String>> rows = SimpleCsv.parse("a,b\r\n\r\n1,2\r\n");

        assertEquals(List.of(List.of("a", "b"), List.of("1", "2")), rows);
    }

    @Test
    @DisplayName("Contenu vide : aucune ligne")
    void empty() {
        assertTrue(SimpleCsv.parse("").isEmpty());
    }
}
