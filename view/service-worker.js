/**
 * Service worker minimal : uniquement les notifications push (Web Push / PWA).
 *
 * Volontairement sans cache/offline (`fetch` non intercepté) : le périmètre de ce patch est
 * strictement les notifications, pas le fonctionnement hors-ligne de l'application.
 */
self.addEventListener('push', event => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    data = {};
  }
  const title = data.title || 'MyFamilyBudget';
  const options = {
    body: data.body || '',
    icon: '/icons/icon.svg',
    badge: '/icons/icon.svg'
  };
  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(windowClients => {
      for (const client of windowClients) {
        if ('focus' in client) return client.focus();
      }
      if (clients.openWindow) return clients.openWindow('/');
    })
  );
});
