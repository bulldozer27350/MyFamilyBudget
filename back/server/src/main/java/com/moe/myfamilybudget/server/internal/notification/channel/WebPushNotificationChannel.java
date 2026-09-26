package com.moe.myfamilybudget.server.internal.notification.channel;

import java.security.Security;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moe.myfamilybudget.server.internal.notification.NotificationChannel;
import com.moe.myfamilybudget.server.internal.notification.NotificationMessage;
import com.moe.myfamilybudget.server.internal.persistence.entity.PushSubscriptionEntity;
import com.moe.myfamilybudget.server.internal.persistence.repository.PushSubscriptionRepository;

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
 */
@Component
public class WebPushNotificationChannel implements NotificationChannel {

    private static final Logger LOG = LoggerFactory.getLogger(WebPushNotificationChannel.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushNotificationChannel(PushSubscriptionRepository subscriptionRepository, ObjectMapper objectMapper,
            @Value("${myfamilybudget.notifications.push.vapid-public-key:}") String vapidPublicKey,
            @Value("${myfamilybudget.notifications.push.vapid-private-key:}") String vapidPrivateKey,
            @Value("${myfamilybudget.notifications.push.vapid-subject:}") String vapidSubject) {
        this.subscriptionRepository = subscriptionRepository;
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
        List<PushSubscriptionEntity> subscriptions = subscriptionRepository.findAll();
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = toPayload(message);
        for (PushSubscriptionEntity sub : subscriptions) {
            sendTo(sub, payload);
        }
    }

    private void sendTo(PushSubscriptionEntity sub, String payload) {
        try {
            Subscription.Keys keys = new Subscription.Keys(sub.getP256dh(), sub.getAuth());
            Subscription subscription = new Subscription(sub.getEndpoint(), keys);
            HttpResponse response = pushService.send(new Notification(subscription, payload));
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // Abonnement expiré ou révoqué côté navigateur : nettoyage.
                subscriptionRepository.deleteById(sub.getId());
            }
        } catch (Exception e) {
            LOG.warn("Échec de l'envoi push vers un abonnement (endpoint tronqué : {})",
                    truncate(sub.getEndpoint()), e);
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
