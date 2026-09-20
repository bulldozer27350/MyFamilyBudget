package com.moe.myfamilybudget.server.internal.calculation;

import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sérialisation JSON explicite des hypothèses de l'analyse des prêts (montants et taux en
 * chaînes, sans perte de précision, indépendante de la configuration Jackson de l'application).
 */
final class LoanAdviceSettingsCodec {

    private LoanAdviceSettingsCodec() {
    }

    static String toJson(LoanAdviceParameters p, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        if (p.marketRate() != null) {
            root.put("marketRate", p.marketRate().toPlainString());
        }
        root.put("repayMarginRate", p.repayMarginRate().toPlainString());
        root.put("renegotiationMinGapRate", p.renegotiationMinGapRate().toPlainString());
        root.put("renegotiationMinCrd", p.renegotiationMinCrd().toPlainString());
        root.put("renegotiationMinRemainingMonths", p.renegotiationMinRemainingMonths());
        root.put("renegotiationFixedCosts", p.renegotiationFixedCosts().toPlainString());
        root.put("flatTaxRate", p.flatTaxRate().toPlainString());
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation des hypothèses de l'analyse des prêts impossible", e);
        }
    }

    /**
     * @throws IllegalStateException si le contenu est illisible ou incomplet
     */
    static LoanAdviceParameters fromJson(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            return new LoanAdviceParameters(
                    optionalDecimal(root, "marketRate"),
                    requiredDecimal(root, "repayMarginRate"),
                    requiredDecimal(root, "renegotiationMinGapRate"),
                    requiredDecimal(root, "renegotiationMinCrd"),
                    requiredInt(root, "renegotiationMinRemainingMonths"),
                    requiredDecimal(root, "renegotiationFixedCosts"),
                    requiredDecimal(root, "flatTaxRate"));
        } catch (JsonProcessingException | NumberFormatException e) {
            throw new IllegalStateException("Hypothèses de l'analyse des prêts persistées illisibles", e);
        }
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : new BigDecimal(value.asText());
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        BigDecimal value = optionalDecimal(node, field);
        if (value == null) {
            throw new IllegalStateException("Champ manquant dans les hypothèses persistées : " + field);
        }
        return value;
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalStateException("Champ manquant ou invalide dans les hypothèses persistées : " + field);
        }
        return value.asInt();
    }
}
