package com.moe.myfamilybudget.domain.notifications.core.channel;

import java.security.Security;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.domain.notifications.model.NotificationMessage;
import com.moe.myfamilybudget.domain.notifications.model.PushSubscriptionModel;
import com.moe.myfamilybudget.domain.notifications.port.NotificationChannel;
import com.moe.myfamilybudget.domain.notifications.port.PushSubscriptionStore;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;

/**
 * Envoie une notification push aux navigateurs abonnés (PWA ajoutée à l'écran d'accueil ou onglet
 * ouvert ayant autorisé les notifications), via le protocole Web Push (RFC 8291/8292, clés VAPID).
 *
 * ATTENTION (à vérifier par Marco au premier passage en CI) : l'intégration avec la bibliothèque
 * tierce {@code nl.martijndwars:web-push} n'a pas pu être compilée dans cet environnement (pas
 * d'accès à Maven Central en sandbox). L'API utilisée ci-dessous correspond à la version 5.1.x
 * documentée du projet, mais mérite une relecture attentive si le build échoue sur ce fichier —
 * en particulier les constructeurs de {@link PushService}/{@link Subscription}/{@link Notification}
 * et le type de retour de {@link PushService#send}.
 *
 * Sans clés VAPID configurées (variables d'environnement absentes), le canal se désactive
 * silencieusement au démarrage — même logique de dégradation gracieuse que la synchronisation
 * Enable Banking ou la clé API Banque de France.
 *
 * Depuis SILO-180, cette classe vit dans {@code notifications-core}, sans Spring ni JPA : les abonnements passent
 * par le port {@link PushSubscriptionStore} et les clés VAPID (propriétés
 * {@code myfamilybudget.notifications.push.*}) sont fournies par le composition root ({@code DomainEngineConfig}).
 */
public class WebPushNotificationChannel implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(WebPushNotificationChannel.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PushSubscriptionStore subscriptionStore;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushNotificationChannel(PushSubscriptionStore subscriptionStore, ObjectMapper objectMapper,
            String vapidPublicKey, String vapidPrivateKey, String vapidSubject) {
        this.subscriptionStore = subscriptionStore;
        this.objectMapper = objectMapper;
        this.pushService = buildPushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
    }

    private static PushService buildPushService(String publicKey, String privateKey, String subject) {
        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            LOG.warn("Clés VAPID absentes : les notifications push mobile sont désactivées "
                    + "(voir myfamilybudget.notifications.push dans application.yml)");
            return null;
        }
        try {
            PushService service = new PushService(publicKey, privateKey);
            service.setSubject(subject);
            return service;
        } catch (Exception e) {
            LOG.warn("Clés VAPID invalides : les notifications push mobile sont désactivées", e);
            return null;
        }
    }

    @Override
    public String key() {
        return "push-mobile";
    }

    @Override
    public void send(NotificationMessage message) {
        if (pushService == null) {
            return;
        }
        List<PushSubscriptionModel> subscriptions = subscriptionStore.findAll();
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = toPayload(message);
        for (PushSubscriptionModel sub : subscriptions) {
            sendTo(sub, payload);
        }
    }

    private void sendTo(PushSubscriptionModel sub, String payload) {
        try {
            Subscription.Keys keys = new Subscription.Keys(sub.p256dh(), sub.auth());
            Subscription subscription = new Subscription(sub.endpoint(), keys);
            HttpResponse response = pushService.send(new Notification(subscription, payload));
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // Abonnement expiré ou révoqué côté navigateur : nettoyage.
                subscriptionStore.deleteById(sub.id());
            }
        } catch (Exception e) {
            LOG.warn("Échec de l'envoi push vers un abonnement (endpoint tronqué : {})",
                    truncate(sub.endpoint()), e);
        }
    }

    private String toPayload(NotificationMessage message) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", message.title(), "body", message.body()));
        } catch (JsonProcessingException e) {
            return "{\"title\":\"MyFamilyBudget\",\"body\":\"Nouvelle alerte\"}";
        }
    }

    private static String truncate(String endpoint) {
        return endpoint == null ? "" : endpoint.substring(0, Math.min(40, endpoint.length())) + "...";
    }
}
