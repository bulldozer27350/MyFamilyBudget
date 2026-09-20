package com.moe.myfamilybudget.server.internal.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
        MortgageRateQuote mortgage = snapshot.mortgageRate();
        if (mortgage != null) {
            ObjectNode node = root.putObject("mortgageRate");
            node.put("asOf", mortgage.asOf().toString());
            putDecimal(node, "rate", mortgage.rate());
            node.put("seriesKey", mortgage.seriesKey());
        }
        YieldCurveQuote curve = snapshot.yieldCurve();
        if (curve != null) {
            ObjectNode node = root.putObject("yieldCurve");
            node.put("asOf", curve.asOf().toString());
            putDecimal(node, "spot2y", curve.spot2y());
            putDecimal(node, "spot10y", curve.spot10y());
            putDecimal(node, "forward1y", curve.forward1y());
            putDecimal(node, "forward2y", curve.forward2y());
            putDecimal(node, "forward5y", curve.forward5y());
            putDecimal(node, "forward10y", curve.forward10y());
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
            MortgageRateQuote mortgage = null;
            JsonNode mortgageNode = root.get("mortgageRate");
            if (mortgageNode != null && mortgageNode.isObject()) {
                mortgage = new MortgageRateQuote(
                        YearMonth.parse(mortgageNode.path("asOf").asText("")),
                        requiredDecimal(mortgageNode, "rate"),
                        mortgageNode.path("seriesKey").asText(""));
            }
            YieldCurveQuote curve = null;
            JsonNode curveNode = root.get("yieldCurve");
            if (curveNode != null && curveNode.isObject()) {
                curve = new YieldCurveQuote(
                        LocalDate.parse(curveNode.path("asOf").asText("")),
                        requiredDecimal(curveNode, "spot2y"),
                        requiredDecimal(curveNode, "spot10y"),
                        requiredDecimal(curveNode, "forward1y"),
                        requiredDecimal(curveNode, "forward2y"),
                        requiredDecimal(curveNode, "forward5y"),
                        requiredDecimal(curveNode, "forward10y"));
            }
            return new MarketSnapshot(fetchedAt, quote, mortgage, curve);
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

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        BigDecimal value = readDecimal(node, field);
        if (value == null) {
            throw new NumberFormatException("Champ manquant : " + field);
        }
        return value;
    }
}
