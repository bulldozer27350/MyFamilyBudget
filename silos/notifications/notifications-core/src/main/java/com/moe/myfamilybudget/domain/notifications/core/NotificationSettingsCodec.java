package com.moe.myfamilybudget.domain.notifications.core;

import com.moe.myfamilybudget.domain.notifications.rules.NotificationSettingsParameters;
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
public final class NotificationSettingsCodec {

    private NotificationSettingsCodec() {
    }

    public static String toJson(NotificationSettingsParameters p, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("debitThresholdEnabled", Boolean.TRUE.equals(p.debitThresholdEnabled()));
        root.put("debitThresholdAmount", p.debitThresholdAmount().toPlainString());
        root.put("balanceFloorEnabled", Boolean.TRUE.equals(p.balanceFloorEnabled()));
        root.put("balanceFloorAmount", p.balanceFloorAmount().toPlainString());
        root.put("objectifReachableEnabled", Boolean.TRUE.equals(p.objectifReachableEnabled()));
        root.put("quietHoursEnabled", Boolean.TRUE.equals(p.quietHoursEnabled()));
        root.put("quietHoursStart", p.quietHoursStart());
        root.put("quietHoursEnd", p.quietHoursEnd());
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation des paramètres de notification impossible", e);
        }
    }

    /**
     * Les 3 champs de plage silencieuse sont lus avec repli sur les valeurs par défaut (absents
     * d'une ligne enregistrée avant leur introduction), contrairement aux montants qui restent
     * strictement requis.
     *
     * @throws IllegalStateException si le contenu est illisible ou un montant est manquant
     */
    public static NotificationSettingsParameters fromJson(String json, ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(json);
            NotificationSettingsParameters defaults = NotificationSettingsParameters.defaults();
            return new NotificationSettingsParameters(
                    root.path("debitThresholdEnabled").asBoolean(false),
                    requiredDecimal(root, "debitThresholdAmount"),
                    root.path("balanceFloorEnabled").asBoolean(false),
                    requiredDecimal(root, "balanceFloorAmount"),
                    root.path("objectifReachableEnabled").asBoolean(false),
                    root.path("quietHoursEnabled").asBoolean(false),
                    root.path("quietHoursStart").asText(defaults.quietHoursStart()),
                    root.path("quietHoursEnd").asText(defaults.quietHoursEnd()));
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
