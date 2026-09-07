/**
 * Sync Status - detection de connectivite et file d'attente de synchronisation
 * hors-ligne (module complementaire au pattern Strangler Fig de api.js).
 *
 * Trois responsabilites :
 *  1. Detecter l'etat de connectivite au backend : ping actif periodique sur
 *     GET {API_BASE_URL}/heartbeat (voir HeartbeatController.java) + detection
 *     passive via reportFailure()/reportSuccess() appeles depuis api.js.
 *  2. Mettre en file d'attente les ecritures qui ont echoue, separees en deux
 *     categories selon le risque de conflit :
 *       - AUTO     : creations (nouvel id) ou mises a jour ciblees par
 *                    id+champ. Rejeu automatique et silencieux des le retour
 *                    du reseau (risque de conflit quasi nul : deux appareils
 *                    qui creent chacun une ligne, ou modifient un champ
 *                    different, ne peuvent pas s'ecraser mutuellement).
 *       - VALIDATE : remplacements complets d'un objet ou d'une ligne (ex.
 *                    donnees de retraite, ligne de patrimoine). Ne sont
 *                    JAMAIS rejoues automatiquement : un controle de derive
 *                    (comparaison entre beforeSnapshot et l'etat serveur
 *                    courant via driftCheckUrl) est effectue avant tout envoi,
 *                    et une confirmation explicite de l'utilisateur est
 *                    requise (voir resolveValidateItem).
 *  3. Notifier les composants abonnes (pattern pub/sub identique aux *Service
 *     de service-metier.js) des changements d'etat (online/offline, compteurs
 *     de files).
 */
(function (exports) {
  'use strict';

  const STORAGE_KEY = 'budgetapp.syncQueue.v1';
  const PING_INTERVAL_MS = 30000;
  const PING_TIMEOUT_MS = 4000;

  function apiBaseUrl() {
    return (typeof window !== 'undefined' && window.API_BASE_URL) || '/api/v1';
  }

  function uid() {
    return 'sync-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 9);
  }

  function emptyQueue() {
    return { auto: [], validate: [] };
  }

  function loadQueue() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : null;
      if (parsed && Array.isArray(parsed.auto) && Array.isArray(parsed.validate)) {
        return parsed;
      }
    } catch (e) {
      console.error('[SyncStatus] File de synchronisation illisible, reinitialisation', e);
    }
    return emptyQueue();
  }

  function saveQueue(q) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(q));
    } catch (e) {
      console.error('[SyncStatus] Echec de sauvegarde de la file de synchronisation', e);
    }
  }

  let queue = loadQueue();
  let online = true;
  let pingTimer = null;
  let flushing = false;
  const listeners = new Set();

  function getState() {
    return {
      online: online,
      autoCount: queue.auto.length,
      validateCount: queue.validate.length
    };
  }

  function notify() {
    const snapshot = getState();
    listeners.forEach(function (fn) {
      try { fn(snapshot); } catch (e) { /* un abonne fautif ne doit pas bloquer les autres */ }
    });
  }

  /**
   * S'abonne aux changements d'etat de synchronisation.
   * @param {Function} listener Appele avec {online, autoCount, validateCount}
   * @returns {Function} fonction de desabonnement
   */
  function subscribe(listener) {
    listeners.add(listener);
    return function () { listeners.delete(listener); };
  }

  /**
   * Enregistre une ecriture echouee dans la file adaptee a son niveau de
   * risque de conflit. A appeler depuis les catch() de api.js a la place du
   * catch silencieux existant.
   * @param {Object} mutation
   * @param {'auto'|'validate'} mutation.tier
   * @param {string} [mutation.method='POST']
   * @param {string} mutation.url URL absolue deja resolue (avec API_BASE_URL)
   * @param {*} [mutation.body]
   * @param {string} mutation.description Libelle humain affiche dans le panneau de synchronisation
   * @param {string} [mutation.driftCheckUrl] URL GET pour recuperer l'etat serveur courant avant
   *   rejeu (uniquement pertinent pour tier='validate')
   * @param {*} [mutation.beforeSnapshot] Etat connu localement avant la modification, compare a
   *   l'etat serveur au moment du rejeu (uniquement pertinent pour tier='validate')
   * @param {string} [mutation.driftListKey] Si la reponse de driftCheckUrl est un objet contenant
   *   plusieurs listes (ex. { placements: [...], loans: [...] }), nom de la liste dans laquelle
   *   chercher la ligne a comparer, plutot que de comparer tout l'objet renvoye (qui contiendrait
   *   aussi d'autres lignes non concernees par cette modification).
   * @param {string} [mutation.driftEntityId] Combine a driftListKey : id de la ligne a extraire
   *   pour la comparaison de derive.
   * @returns {string} identifiant de l'entree en file
   */
  function enqueue(mutation) {
    const entry = {
      id: uid(),
      createdAt: new Date().toISOString(),
      tier: mutation.tier === 'validate' ? 'validate' : 'auto',
      method: mutation.method || 'POST',
      url: mutation.url,
      body: mutation.body !== undefined ? mutation.body : null,
      description: mutation.description || mutation.url,
      driftCheckUrl: mutation.driftCheckUrl || null,
      beforeSnapshot: mutation.beforeSnapshot !== undefined ? mutation.beforeSnapshot : null,
      driftListKey: mutation.driftListKey || null,
      driftEntityId: mutation.driftEntityId !== undefined ? mutation.driftEntityId : null
    };
    queue[entry.tier].push(entry);
    saveQueue(queue);
    online = false;
    notify();
    return entry.id;
  }

  function removeFromQueue(tier, id) {
    queue[tier] = queue[tier].filter(function (e) { return e.id !== id; });
    saveQueue(queue);
    notify();
  }

  async function sendMutation(entry) {
    const hasBody = entry.body !== null && entry.body !== undefined;
    const res = await fetch(entry.url, {
      method: entry.method,
      headers: hasBody ? { 'Content-Type': 'application/json' } : undefined,
      body: hasBody ? JSON.stringify(entry.body) : undefined
    });
    if (!res.ok) throw new Error('HTTP ' + res.status + ' pour ' + entry.url);
    return res;
  }

  /**
   * Rejoue la file "auto" dans l'ordre chronologique (FIFO). S'arrete au
   * premier echec pour ne pas desynchroniser l'ordre des ecritures
   * restantes ; la tentative suivante reviendra au prochain ping reussi.
   */
  async function flushAutoQueue() {
    if (flushing) return;
    flushing = true;
    try {
      while (queue.auto.length > 0) {
        const entry = queue.auto[0];
        try {
          await sendMutation(entry);
          removeFromQueue('auto', entry.id);
        } catch (e) {
          console.error('[SyncStatus] Echec du rejeu automatique, nouvelle tentative au prochain retour reseau', e);
          break;
        }
      }
    } finally {
      flushing = false;
    }
  }

  /**
   * Renvoie une copie de la file "a valider" (remplacements complets), pour
   * affichage dans un panneau de revue.
   * @returns {Array<Object>}
   */
  function getValidateQueue() {
    return queue.validate.slice();
  }

  /**
   * Extrait, depuis la reponse de driftCheckUrl, la seule portion comparable a
   * beforeSnapshot. Si l'entree porte driftListKey+driftEntityId (cas d'une
   * ligne au sein d'une liste, ex. une ligne de Patrimoine), on va chercher
   * uniquement cette ligne dans la liste correspondante plutot que de
   * comparer tout l'objet renvoye par l'API (qui contient aussi les autres
   * lignes, non concernees par cette modification). Sinon, l'objet complet
   * renvoye est compare tel quel (cas d'un objet unique, ex. /retraite).
   */
  function extractComparable(serverPayload, entry) {
    if (entry.driftListKey && entry.driftEntityId !== null && entry.driftEntityId !== undefined) {
      const list = serverPayload ? serverPayload[entry.driftListKey] : null;
      if (Array.isArray(list)) {
        return list.find(function (r) { return r && String(r.id) === String(entry.driftEntityId); }) || null;
      }
      return null;
    }
    return serverPayload;
  }

  /**
   * Tente d'envoyer une entree de la file "a valider".
   * Effectue d'abord un controle de derive (sauf si force=true) : si
   * driftCheckUrl est renseignee, recupere l'etat serveur courant et le
   * compare a beforeSnapshot. En cas de derive detectee, l'envoi est
   * annule et onDrift (si fourni) est appele avec l'etat serveur courant ;
   * c'est a l'appelant (UI) de decider de la suite (forcer l'envoi,
   * abandonner la modification locale...).
   * @param {string} id
   * @param {{force?: boolean, onDrift?: Function}} [options]
   * @returns {Promise<{sent: boolean, drift: boolean, serverValue?: *, error?: Error}>}
   */
  async function resolveValidateItem(id, options) {
    const opts = options || {};
    const entry = queue.validate.find(function (e) { return e.id === id; });
    if (!entry) return { sent: false, drift: false };

    if (!opts.force && entry.driftCheckUrl) {
      try {
        const res = await fetch(entry.driftCheckUrl);
        if (res.ok) {
          const currentPayload = await res.json();
          const comparable = extractComparable(currentPayload, entry);
          const drifted = JSON.stringify(comparable) !== JSON.stringify(entry.beforeSnapshot);
          if (drifted) {
            if (opts.onDrift) opts.onDrift(comparable, entry);
            return { sent: false, drift: true, serverValue: comparable };
          }
        }
      } catch (e) {
        // Pas de connexion pour verifier la derive : on ne tente pas l'envoi a l'aveugle.
        return { sent: false, drift: false, error: e };
      }
    }

    await sendMutation(entry);
    removeFromQueue('validate', id);
    return { sent: true, drift: false };
  }

  /**
   * Abandonne une entree de la file "a valider" sans l'envoyer (l'utilisateur
   * choisit de conserver l'etat serveur plutot que sa modification locale).
   */
  function discardValidateItem(id) {
    removeFromQueue('validate', id);
  }

  async function ping() {
    try {
      let controller = null;
      let timer = null;
      if (typeof AbortController !== 'undefined') {
        controller = new AbortController();
        timer = setTimeout(function () { controller.abort(); }, PING_TIMEOUT_MS);
      }
      const res = await fetch(apiBaseUrl() + '/heartbeat', {
        signal: controller ? controller.signal : undefined
      });
      if (timer) clearTimeout(timer);
      const wasOffline = !online;
      online = !!(res && res.ok);
      notify();
      if (online && wasOffline) {
        flushAutoQueue();
      }
    } catch (e) {
      const wasOnline = online;
      online = false;
      if (wasOnline) notify();
    }
  }

  /**
   * Detection passive : a appeler depuis api.js quand une ecriture hors
   * file echoue avant meme d'avoir ete mise en queue (fait immediatement
   * basculer l'indicateur en hors-ligne sans attendre le prochain ping).
   */
  function reportFailure() {
    if (online) {
      online = false;
      notify();
    }
  }

  /**
   * Detection passive : a appeler depuis api.js quand une ecriture reussit,
   * pour republier l'etat en ligne plus tot que le prochain ping et
   * declencher le rejeu de la file auto.
   */
  function reportSuccess() {
    if (!online) {
      online = true;
      notify();
      flushAutoQueue();
    }
  }

  function startPolling() {
    if (pingTimer) return;
    ping();
    pingTimer = setInterval(ping, PING_INTERVAL_MS);
  }

  function stopPolling() {
    if (pingTimer) {
      clearInterval(pingTimer);
      pingTimer = null;
    }
  }

  if (typeof window !== 'undefined') {
    startPolling();
  }

  exports.SyncStatus = {
    subscribe: subscribe,
    getState: getState,
    enqueue: enqueue,
    flushAutoQueue: flushAutoQueue,
    getValidateQueue: getValidateQueue,
    resolveValidateItem: resolveValidateItem,
    discardValidateItem: discardValidateItem,
    reportFailure: reportFailure,
    reportSuccess: reportSuccess,
    ping: ping,
    startPolling: startPolling,
    stopPolling: stopPolling
  };
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
