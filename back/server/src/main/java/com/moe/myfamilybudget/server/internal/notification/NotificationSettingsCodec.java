package com.moe.myfamilybudget.server.internal.notification;

import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Sérialisation JSON explicite des paramètres de notification (montants en chaînes, sans perte de
 * précision, indépendante de la configuration Jackson de l'application) — même approche que
 * {@code LoanAdviceSettingsCodec}.
 */
final class NotificationSettingsCodec {

    private NotificationSettingsCodec() {
    }

    static String toJson(NotificationSettingsParameters p, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("debitThresholdEnabled", Boolean.TRUE.equals(p.debitThresholdEnabled()));
        root.put("debitThresholdAmount", p.debitThresholdAmount().toPlainString());
        root.put("balanceFloorEnabled", Boolean.TRUE.equals(p.balanceFloorEnabled()));
        root.put("balanceFloorAmount", p.balanceFloorAmount().toPlainString());
        root.put("objectifReachableEnabled", Boolean.TRUE.equals(p.objectifReachableEnabled()));
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation des paramètres de notification impossible", e);
        }
    }

    /**
     * @throws IllegalStateException si le contenu est illisible ou incomplet
     */
    static NotificationSettingsParameters fromJson(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            return new NotificationSettingsParameters(
                    root.path("debitThresholdEnabled").asBoolean(false),
                    requiredDecimal(root, "debitThresholdAmount"),
                    root.path("balanceFloorEnabled").asBoolean(false),
                    requiredDecimal(root, "balanceFloorAmount"),
                    root.path("objectifReachableEnabled").asBoolean(false));
        } catch (JsonProcessingException | NumberFormatException e) {
            throw new IllegalStateException("Paramètres de notification persistés illisibles", e);
        }
    }

    private static BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("Champ manquant dans les paramètres persistés : " + field);
        }
        return new BigDecimal(value.asText());
    }
}
