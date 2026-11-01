package com.moe.myfamilybudget.domain.notifications.model;

import java.time.Instant;

/** Abonnement Web Push d'un navigateur (endpoint et clés fournis par le navigateur). */
public record PushSubscriptionModel(String id, String endpoint, String p256dh, String auth, Instant createdAt) {
}
