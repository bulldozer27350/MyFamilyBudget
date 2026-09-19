package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sérialisation JSON explicite d'un {@link MarketSnapshot}. Le format est écrit à la main (dates
 * en ISO-8601, montants en chaînes) pour ne dépendre ni de la configuration Jackson de
 * l'application ni d'une perte de précision sur les BigDecimal.
 */
final class MarketSnapshotCodec {

    private MarketSnapshotCodec() {
    }

    static String toJson(MarketSnapshot snapshot, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("fetchedAt", snapshot.fetchedAt().toString());
        RegulatedRatesQuote quote = snapshot.regulatedRates();
        if (quote != null) {
            ObjectNode regulated = root.putObject("regulatedRates");
            regulated.put("asOf", quote.asOf().toString());
            putDecimal(regulated, "livretA", quote.livretA());
            putDecimal(regulated, "ldds", quote.ldds());
            putDecimal(regulated, "lep", quote.lep());
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new MarketDataException("Sérialisation de l'instantané de marché impossible", e);
        }
    }

    static MarketSnapshot fromJson(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            Instant fetchedAt = Instant.parse(root.path("fetchedAt").asText(""));
            RegulatedRatesQuote quote = null;
            JsonNode regulated = root.get("regulatedRates");
            if (regulated != null && regulated.isObject()) {
                quote = new RegulatedRatesQuote(
                        YearMonth.parse(regulated.path("asOf").asText("")),
                        readDecimal(regulated, "livretA"),
                        readDecimal(regulated, "ldds"),
                        readDecimal(regulated, "lep"));
            }
            return new MarketSnapshot(fetchedAt, quote);
        } catch (JsonProcessingException | DateTimeParseException | NumberFormatException e) {
            throw new MarketDataException("Instantané de marché persisté illisible", e);
        }
    }

    private static void putDecimal(ObjectNode node, String field, BigDecimal value) {
        if (value != null) {
            node.put(field, value.toPlainString());
        }
    }

    private static BigDecimal readDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : new BigDecimal(value.asText());
    }
}
