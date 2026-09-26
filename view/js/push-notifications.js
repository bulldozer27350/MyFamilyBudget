/**
 * Abonnement Web Push (notifications push mobile via PWA, sans passer par un store
 * d'applications). Utilisé par l'onglet "Notifications" des paramètres
 * (view/js/views/settings-view.js).
 *
 * Fonctionnement : Service Worker (view/service-worker.js) enregistré une fois pour toute
 * l'application, abonnement obtenu via l'API Push du navigateur (clé publique VAPID fournie par
 * le back-end), abonnement transmis au back-end (NotificationsApi) qui s'en sert pour l'envoi
 * réel (WebPushNotificationChannel).
 */
(function (exports) {
  'use strict';

  function api() {
    return exports.BudgetApi || window.BudgetApp?.BudgetApi;
  }

  /**
   * @returns {boolean} false si le navigateur ne peut pas du tout faire de Web Push (API absente,
   * ou iOS Safari hors contexte "Ajouté à l'écran d'accueil")
   */
  function isSupported() {
    return typeof window !== 'undefined'
      && 'serviceWorker' in navigator
      && 'PushManager' in window
      && typeof Notification !== 'undefined';
  }

  // Conversion de la clé publique VAPID (base64 URL-safe, telle que renvoyée par le back-end)
  // vers le format Uint8Array attendu par PushManager.subscribe().
  function urlBase64ToUint8Array(base64String) {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; i++) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }

  async function registerServiceWorker() {
    return navigator.serviceWorker.register('/service-worker.js');
  }

  // Enregistrement best-effort dès le chargement du module, pour que getStatus() puisse
  // détecter un abonnement existant sans attendre une action de l'utilisateur.
  if (isSupported()) {
    registerServiceWorker().catch(() => {
      // Navigateur restrictif ou contexte non sécurisé (http non-localhost) : la fonctionnalité
      // restera simplement indisponible, getStatus() renverra 'unsupported'/'not-subscribed'.
    });
  }

  /**
   * @returns {Promise<'unsupported'|'denied'|'subscribed'|'not-subscribed'>}
   */
  async function getStatus() {
    if (!isSupported()) {
      return 'unsupported';
    }
    if (Notification.permission === 'denied') {
      return 'denied';
    }
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      const subscription = registration ? await registration.pushManager.getSubscription() : null;
      return subscription ? 'subscribed' : 'not-subscribed';
    } catch (e) {
      return 'not-subscribed';
    }
  }

  /**
   * Demande la permission, s'abonne, et transmet l'abonnement au back-end.
   * @throws {Error} message adapté à l'affichage direct (permission refusée, VAPID absente...)
   */
  async function subscribe() {
    if (!isSupported()) {
      throw new Error('Notifications push non prises en charge par ce navigateur.');
    }
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      throw new Error('Permission de notification refusée.');
    }
    const keyResponse = await api()?.getPushPublicKey();
    const publicKey = keyResponse?.publicKey;
    if (!publicKey) {
      throw new Error('Clé VAPID non configurée côté serveur (voir myfamilybudget.notifications.push).');
    }
    const registration = await registerServiceWorker();
    await navigator.serviceWorker.ready;
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(publicKey)
    });
    await api()?.registerPushSubscription(subscription.toJSON());
    return subscription;
  }

  /**
   * Résilie l'abonnement local et prévient le back-end.
   */
  async function unsubscribe() {
    if (!isSupported()) {
      return;
    }
    const registration = await navigator.serviceWorker.getRegistration();
    const subscription = registration ? await registration.pushManager.getSubscription() : null;
    if (!subscription) {
      return;
    }
    const endpoint = subscription.endpoint;
    await subscription.unsubscribe();
    await api()?.unregisterPushSubscription(endpoint);
  }

  exports.PushNotifications = {
    isSupported,
    getStatus,
    subscribe,
    unsubscribe
  };
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
