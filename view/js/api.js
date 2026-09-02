/**
 * Façade API (Pattern Strangler Fig)
 *
 * Point d'entrée asynchrone unique des composants React vers les règles métier et le
 * stockage. Toutes les méthodes retournent une Promise : le jour où le traitement passera
 * sur un back-end Java Spring Boot, seule l'implémentation interne changera (fetch),
 * pas les composants.
 *
 * Fonctionnalités déjà migrées : Vue d'ensemble, Trésorerie, Patrimoine, Retraite, Impôts, Paramètres, Import Bancaire.
 */
(function (exports) {
  'use strict';

  function app() {
    return typeof window !== 'undefined' ? window.BudgetApp || exports : exports;
  }

  /**
   * Adresse et port du serveur Back-end
   */
  const API_BASE_URL = typeof window !== 'undefined'
    ? (window.API_BASE_URL || '/api/v1')
    : (typeof process !== 'undefined' && process?.env?.BACKEND_URL ? process.env.BACKEND_URL : '/api/v1');

  async function safeFetch(url, options = {}, timeoutMs = 5000) {
    if (typeof fetch === 'undefined') return null;
    try {
      let controller = null;
      let timer = null;
      if (typeof AbortController !== 'undefined') {
        controller = new AbortController();
        timer = setTimeout(() => controller.abort(), timeoutMs);
      }
      const res = await fetch(url, {
        ...options,
        signal: controller ? controller.signal : undefined
      });
      if (timer) clearTimeout(timer);
      return res;
    } catch (e) {
      return null;
    }
  }

  /**
   * Indique si le fallback silencieux vers le service JS local (service-metier.js /
   * localStorage) doit être désactivé. Piloté par window.DISABLE_JS_FALLBACK,
   * réglable dans config.js, pour lever toute ambiguïté sur l'origine réelle
   * (backend Spring Boot / H2 vs. store JS local) des données affichées.
   */
  function isJsFallbackDisabled() {
    return typeof window !== 'undefined' && window.DISABLE_JS_FALLBACK === true;
  }

  /**
   * Factorise le pattern « tenter le backend, sinon retomber sur le service JS local ».
   * @param {string} path Chemin relatif à API_BASE_URL (ex: '/overview')
   * @param {Function} fallbackFn Fonction synchrone retournant les données du service JS local
   * @param {string} [query] Query string éventuelle (ex: '?useConstantEuros=true')
   * @returns {Promise<Object>}
   */
  async function fetchJsonOrFallback(path, fallbackFn, query = '') {
    if (typeof fetch !== 'undefined') {
      try {
        const res = await fetch(API_BASE_URL + path + query);
        if (res.ok) return await res.json();
        if (isJsFallbackDisabled()) {
          throw new Error('Backend a répondu ' + res.status + ' pour ' + path);
        }
      } catch (e) {
        if (isJsFallbackDisabled()) {
          console.error('[DISABLE_JS_FALLBACK] Échec de ' + path + ', fallback JS désactivé.', e);
          throw e;
        }
      }
    } else if (isJsFallbackDisabled()) {
      throw new Error("fetch indisponible et fallback JS désactivé pour " + path);
    }
    return Promise.resolve(fallbackFn());
  }

  const BudgetApi = {
    /**
     * Vue d'ensemble : KPIs, projections (trésorerie, patrimoine) et jauges FIRE.
     * @param {{useConstantEuros?: boolean}} [options]
     * @returns {Promise<Object>} modèle de lecture de la Vue d'ensemble
     */
    async getVueDensemble(options) {
      return fetchJsonOrFallback('/overview', () => app().OverviewService.buildOverview(options));
    },

    /**
     * S'abonne aux changements des données de la Vue d'ensemble (autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onVueDensembleChanged(listener) {
      return app().OverviewService.subscribeOverview(listener);
    },

    /**
     * Trésorerie : revenus, charges, dépenses ponctuelles, primes et courbe simplifiée.
     * @param {{useConstantEuros?: boolean}} [options]
     * @returns {Promise<Object>} modèle de lecture de l'onglet Trésorerie
     */
    async getTresorerie(options) {
      const query = options?.useConstantEuros ? '?useConstantEuros=true' : '';
      return fetchJsonOrFallback('/tresorerie', () => app().TresorerieService.buildTresorerie(options), query);
    },

    /**
     * Met à jour une cellule d'une ligne de Trésorerie.
     * @returns {Promise<void>}
     */
    async updateTresorerieLigne(listKey, id, field, value) {
      const p = Promise.resolve(app().TresorerieService.updateTresorerieLigne(listKey, id, field, value));
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/tresorerie/' + encodeURIComponent(listKey), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id, field, value })
          });
        } catch (e) {}
      }
      return p;
    },

    /**
     * Ajoute une ligne vide du type demandé (incomes, charges, oneoff, variableIncomes, variableOverrides).
     * @returns {Promise<void>}
     */
    async addTresorerieLigne(listKey, options) {
      const p = Promise.resolve(app().TresorerieService.addTresorerieLigne(listKey, options));
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/tresorerie/' + encodeURIComponent(listKey), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(options || {})
          });
        } catch (e) {}
      }
      return p;
    },

    /**
     * Supprime une ligne de Trésorerie.
     * @returns {Promise<void>}
     */
    async removeTresorerieLigne(listKey, id) {
      const p = Promise.resolve(app().TresorerieService.removeTresorerieLigne(listKey, id));
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/tresorerie/' + encodeURIComponent(listKey) + '/' + encodeURIComponent(id), {
            method: 'DELETE'
          });
        } catch (e) {}
      }
      return p;
    },

    /**
     * Applique une suggestion d'ajustement du montant mensuel (dérive budget vs réel).
     * @returns {Promise<void>}
     */
    async applyTresorerieAjustement(lineId, kind, newMonthly) {
      const p = Promise.resolve(app().TresorerieService.applyTresorerieAjustement(lineId, kind, newMonthly));
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/tresorerie/adjust', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ lineId, kind, newMonthly })
          });
        } catch (e) {}
      }
      return p;
    },

    /**
     * S'abonne aux changements des données de Trésorerie (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onTresorerieChanged(listener) {
      return app().TresorerieService.subscribeTresorerie(listener);
    },

    /**
     * Patrimoine : placements, transferts, crédits, immobilier et courbe 3 scénarios.
     * @param {{useConstantEuros?: boolean}} [options]
     * @returns {Promise<Object>} modèle de lecture de l'onglet Patrimoine
     */
    async getPatrimoine(options) {
      const query = options?.useConstantEuros ? '?useConstantEuros=true' : '';
      return fetchJsonOrFallback('/patrimoine', () => app().PatrimoineService.buildPatrimoine(options), query);
    },

    /**
     * Crée une ligne Patrimoine non persistée (brouillon du tiroir Placement).
     * @returns {Promise<Object>}
     */
    async createPatrimoineLigne(listKey) {
      return Promise.resolve(app().PatrimoineService.createPatrimoineLigne(listKey));
    },

    /**
     * Met à jour une cellule d'une ligne de Patrimoine.
     * @returns {Promise<void>}
     */
    async updatePatrimoineLigne(listKey, id, field, value) {
      await app().PatrimoineService.updatePatrimoineLigne(listKey, id, field, value);
      if (typeof fetch !== 'undefined') {
        try {
          const list = app().BudgetStore.getData()[listKey] || [];
          const row = list.find(r => r.id === id);
          if (row) {
            await fetch(API_BASE_URL + '/patrimoine/' + encodeURIComponent(listKey), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(row)
            });
          }
        } catch (e) {
          console.error("Failed to sync updated patrimoine line to backend", e);
        }
      }
    },

    /**
     * Ajoute une ligne (placements, transfers, loans, realEstate).
     * Si `row` est fourni, il est persisté tel quel (création depuis le tiroir).
     * @returns {Promise<void>}
     */
    async addPatrimoineLigne(listKey, row) {
      await app().PatrimoineService.addPatrimoineLigne(listKey, row);
      if (typeof fetch !== 'undefined') {
        try {
          let targetRow = row;
          if (!targetRow) {
            const list = app().BudgetStore.getData()[listKey] || [];
            targetRow = list[list.length - 1];
          }
          if (targetRow) {
            await fetch(API_BASE_URL + '/patrimoine/' + encodeURIComponent(listKey), {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(targetRow)
            });
          }
        } catch (e) {
          console.error("Failed to sync new patrimoine line to backend", e);
        }
      }
    },

    /**
     * Supprime une ligne de Patrimoine.
     * @returns {Promise<void>}
     */
    async removePatrimoineLigne(listKey, id) {
      await app().PatrimoineService.removePatrimoineLigne(listKey, id);
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/patrimoine/' + encodeURIComponent(listKey) + '/' + encodeURIComponent(id), {
            method: 'DELETE'
          });
        } catch (e) {
          console.error("Failed to sync deleted patrimoine line to backend", e);
        }
      }
    },

    /**
     * S'abonne aux changements des données de Patrimoine (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onPatrimoineChanged(listener) {
      return app().PatrimoineService.subscribePatrimoine(listener);
    },

    /**
     * Récupère les données de retraite
     * @returns {Promise<Object>} Données de retraite avec les projections calculées
     */
    async getRetraiteData() {
      return new Promise((resolve, reject) => {
        try {
          // Utilise la fonction de service-metier.js
          const getRetraiteDataFn = exports.getRetraiteDataFromService || window.BudgetApp?.getRetraiteDataFromService;
          if (getRetraiteDataFn) {
            // Enveloppe le traitement synchrone dans Promise.resolve()
            resolve(Promise.resolve(getRetraiteDataFn()));
          } else {
            resolve({});
          }
        } catch (error) {
          reject(error);
        }
      });
    },

    async getRetraite() {
      return this.getRetraiteData();
    },

    /**
     * Sauvegarde les données de retraite
     * @param {Object} retirementData - Données de retraite à sauvegarder
     * @returns {Promise<void>}
     */
    async saveRetraiteData(retirementData) {
      return new Promise((resolve, reject) => {
        try {
          // Utilise la fonction de service-metier.js
          const saveRetraiteDataFn = exports.saveRetraiteDataToService || window.BudgetApp?.saveRetraiteDataToService;
          if (saveRetraiteDataFn) {
            // Enveloppe le traitement synchrone dans Promise.resolve()
            Promise.resolve(saveRetraiteDataFn(retirementData)).then(() => resolve());
          } else {
            resolve();
          }
        } catch (error) {
          reject(error);
        }
      });
    },

    async saveRetraite(retirementData) {
      return this.saveRetraiteData(retirementData);
    },

    /**
     * Impôts : foyer fiscal, barème progressif, simulateur PAS et ajustements réels.
     * @returns {Promise<Object>} modèle de lecture de l'onglet Impôts
     */
    async getImpots() {
      return Promise.resolve(app().ImpotsService.buildImpots());
    },

    /**
     * Met à jour une cellule d'une ligne d'Impôts.
     * @returns {Promise<void>}
     */
    async updateImpotsLigne(listKey, id, field, value) {
      return Promise.resolve(app().ImpotsService.updateImpotsLigne(listKey, id, field, value));
    },

    /**
     * Ajoute une ligne d'Impôts.
     * @returns {Promise<void>}
     */
    async addImpotsLigne(listKey, rowFactory) {
      return Promise.resolve(app().ImpotsService.addImpotsLigne(listKey, rowFactory));
    },

    /**
     * Supprime une ligne d'Impôts.
     * @returns {Promise<void>}
     */
    async removeImpotsLigne(listKey, id) {
      return Promise.resolve(app().ImpotsService.removeImpotsLigne(listKey, id));
    },

    /**
     * Met à jour les settings globaux liés aux impôts.
     * @returns {Promise<void>}
     */
    async updateImpotsSettings(field, value) {
      return Promise.resolve(app().ImpotsService.updateImpotsSettings(field, value));
    },

    /**
     * Réinitialise les tranches au barème standard.
     * @returns {Promise<void>}
     */
    async resetDefaultTaxBrackets() {
      return Promise.resolve(app().ImpotsService.resetDefaultTaxBrackets());
    },

    /**
     * S'abonne aux changements des données d'Impôts (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onImpotsChanged(listener) {
      return app().ImpotsService.subscribeImpots(listener);
    },

    /**
     * Paramètres : settings généraux, date pivot, virement auto et catégories d'actifs.
     * @returns {Promise<Object>} modèle de lecture de l'onglet Paramètres
     */
    async getSettings() {
      return fetchJsonOrFallback('/settings', () => app().SettingsService.buildSettings());
    },

    /**
     * Met à jour un champ des settings.
     * @returns {Promise<void>}
     */
    async updateSettingsField(field, value) {
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ field, value })
          });
        } catch (e) {
          console.error("Failed to update settings field on backend", e);
        }
      }
      return Promise.resolve(app().SettingsService.updateSettingsField(field, value));
    },

    /**
     * Met à jour une cellule d'une catégorie d'actif.
     * @returns {Promise<void>}
     */
    async updateAssetCategory(id, field, value) {
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "updateAssetCategory", id, field, value })
          });
        } catch (e) {
          console.error("Failed to update asset category on backend", e);
        }
      }
      return Promise.resolve(app().SettingsService.updateAssetCategory(id, field, value));
    },

    /**
     * Ajoute une nouvelle catégorie d'actif.
     * @returns {Promise<void>}
     */
    async addAssetCategory(row) {
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "addAssetCategory", row })
          });
        } catch (e) {
          console.error("Failed to add asset category on backend", e);
        }
      }
      return Promise.resolve(app().SettingsService.addAssetCategory(row));
    },

    /**
     * Supprime une catégorie d'actif.
     * @returns {Promise<void>}
     */
    async removeAssetCategory(id) {
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/settings', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: "removeAssetCategory", id })
          });
        } catch (e) {
          console.error("Failed to remove asset category on backend", e);
        }
      }
      return Promise.resolve(app().SettingsService.removeAssetCategory(id));
    },

    /**
     * S'abonne aux changements des données de Paramètres (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onSettingsChanged(listener) {
      return app().SettingsService.subscribeSettings(listener);
    },

    /**
     * Import Bancaire : mapping, catégories, règles et transactions.
     * @returns {Promise<Object>} modèle de lecture de l'onglet Import
     */
    async getBankImport() {
      return fetchJsonOrFallback('/bank-import', () => app().BankImportService.buildBankImport());
    },

    /**
     * Met à jour le mapping de colonnes pour l'import bancaire.
     * @returns {Promise<void>}
     */
    async updateBankImportMapping(newMapping) {
      return Promise.resolve(app().BankImportService.updateBankImportMapping(newMapping));
    },

    /**
     * Ajoute une nouvelle catégorie d'import bancaire.
     * @returns {Promise<void>}
     */
    async addBankImportCategory(category) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(category || {})
          });
          if (res && res.ok) {
            app().BankImportService.addBankImportCategory(category);
            return;
          }
        } catch (e) {
          console.error("Échec ajout catégorie sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.addBankImportCategory(category));
    },

    /**
     * Met à jour une catégorie d'import bancaire.
     * @returns {Promise<void>}
     */
    async updateBankImportCategory(id, field, value) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/categories/' + encodeURIComponent(id), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ field, value })
          });
          if (res && res.ok) {
            app().BankImportService.updateBankImportCategory(id, field, value);
            return;
          }
        } catch (e) {
          console.error("Échec mise à jour catégorie sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.updateBankImportCategory(id, field, value));
    },

    /**
     * Supprime une catégorie d'import bancaire.
     * @returns {Promise<void>}
     */
    async removeBankImportCategory(id) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/categories/' + encodeURIComponent(id), {
            method: 'DELETE'
          });
          if (res && res.ok) {
            app().BankImportService.removeBankImportCategory(id);
            return;
          }
        } catch (e) {
          console.error("Échec suppression catégorie sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.removeBankImportCategory(id));
    },

    /**
     * Ajoute une nouvelle règle de catégorisation.
     * @returns {Promise<void>}
     */
    async addBankImportRule(rule) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/rules', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(rule || {})
          });
          if (res && res.ok) {
            app().BankImportService.addBankImportRule(rule);
            return;
          }
        } catch (e) {
          console.error("Échec ajout règle sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.addBankImportRule(rule));
    },

    /**
     * Met à jour une règle de catégorisation.
     * @returns {Promise<void>}
     */
    async updateBankImportRule(id, field, value) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/rules/' + encodeURIComponent(id), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ field, value })
          });
          if (res && res.ok) {
            app().BankImportService.updateBankImportRule(id, field, value);
            return;
          }
        } catch (e) {
          console.error("Échec mise à jour règle sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.updateBankImportRule(id, field, value));
    },

    /**
     * Supprime une règle de catégorisation.
     * @returns {Promise<void>}
     */
    async removeBankImportRule(id) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/rules/' + encodeURIComponent(id), {
            method: 'DELETE'
          });
          if (res && res.ok) {
            app().BankImportService.removeBankImportRule(id);
            return;
          }
        } catch (e) {
          console.error("Échec suppression règle sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.removeBankImportRule(id));
    },

    /**
     * Réapplique les règles aux transactions non catégorisées.
     * @returns {Promise<void>}
     */
    async recalculateBankImportRules() {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/rules/recalculate', {
            method: 'POST'
          });
          if (res && res.ok) {
            app().BankImportService.recalculateBankImportRules();
            return;
          }
        } catch (e) {
          console.error("Échec recalcul des règles sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.recalculateBankImportRules());
    },

    /**
     * Met à jour la catégorie d'une transaction et optionnellement crée une règle.
     * @returns {Promise<void>}
     */
    async setBankImportTransactionCategory(txId, categoryId, ruleKeyword) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/transactions/' + encodeURIComponent(txId) + '/category', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ categoryId, ruleKeyword })
          });
          if (res && res.ok) {
            app().BankImportService.setBankImportTransactionCategory(txId, categoryId, ruleKeyword);
            return;
          }
        } catch (e) {
          console.error("Échec catégorisation transaction sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.setBankImportTransactionCategory(txId, categoryId, ruleKeyword));
    },

    /**
     * Met à jour la ventilation (splits) d'une transaction bancaire.
     * @param {string} txId - Identifiant de la transaction
     * @param {Array} splits - Liste des sous-lignes ventilées
     * @returns {Promise<void>}
     */
    async updateBankTransactionSplits(txId, splits) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank/transactions/' + encodeURIComponent(txId) + '/splits', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(splits || [])
          });
          if (res && res.ok) {
            app().BankImportService.updateBankTransactionSplits(txId, splits);
            return;
          }
        } catch (e) {
          console.error("Failed to update transaction splits on backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.updateBankTransactionSplits(txId, splits));
    },

    /**
     * Force l'import d'une transaction marquée comme doublon.
     * @returns {Promise<void>}
     */
    async forceImportBankTransaction(tx) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/transactions/force', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(tx || {})
          });
          if (res && res.ok) {
            app().BankImportService.forceImportBankTransaction(tx);
            return;
          }
        } catch (e) {
          console.error("Échec import forcé de la transaction sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.forceImportBankTransaction(tx));
    },

    /**
     * Convertit un fichier Excel (.xls ou .xlsx) en texte CSV via le backend.
     * @param {File} file - Fichier Excel à convertir
     * @returns {Promise<string>} Contenu CSV converti
     */
    async convertExcelToCsv(file) {
      if (typeof fetch !== 'undefined') {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(API_BASE_URL + '/bank/convert-excel', {
          method: 'POST',
          body: formData
        });
        if (!res.ok) {
          const msg = await res.text();
          throw new Error(msg || res.statusText || 'Erreur de conversion Excel');
        }
        return await res.text();
      }
      throw new Error("Conversion Excel non supportée dans cet environnement");
    },

    /**
     * Importe des transactions depuis un fichier CSV traité.
     * @returns {Promise<Object>} Résumé de l'import
     */
    async importBankTransactions(rawRows, colRoles, mapping) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await safeFetch(API_BASE_URL + '/bank-import/import', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rawRows, colRoles, mapping })
          });
          if (res && res.ok) {
            const summary = await res.json();
            // Important : on ne rejoue PAS l'import côté local (app().BankImportService.importBankTransactions)
            // ici. Ce service génère ses propres identifiants via uid(), indépendamment des identifiants
            // générés côté serveur pour les mêmes lignes. Les deux copies (locale et backend) portant des IDs
            // différents pour les mêmes transactions, tout écran qui fusionne apiData.transactions et
            // data.bankImport.transactions par id (ex: pending-view.js) comptait deux fois chaque transaction
            // importée sur l'appareil ayant fait l'import, faussant le solde. L'écran d'import se resynchronise
            // de toute façon juste après via BudgetApi.getBankImport(), donc ce miroir local était à la fois
            // inutile et nuisible.
            return summary;
          }
        } catch (e) {
          console.error("Échec import bancaire sur le backend", e);
        }
      }
      return Promise.resolve(app().BankImportService.importBankTransactions(rawRows, colRoles, mapping));
    },

    /**
     * S'abonne aux changements des données d'Import Bancaire (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onBankImportChanged(listener) {
      return app().BankImportService.subscribeBankImport(listener);
    },

    /**
     * Récupère le modèle de lecture des opérations en cours (chèques émis, CB différées & rapprochement).
     * @returns {Promise<Object>} Modèle de lecture
     */
    async getPendingOperations() {
      const localFallback = (typeof app === 'function' && app().PendingOperationsService)
        ? app().PendingOperationsService.buildPendingOperations()
        : {};
      try {
        const res = await safeFetch(API_BASE_URL + '/pending-operations');
        if (res && res.ok) {
          const data = await res.json();
          const backendOps = Array.isArray(data?.pendingOperations) ? data.pendingOperations : [];
          const localOps = Array.isArray(localFallback?.pendingOperations) ? localFallback.pendingOperations : [];
          const mergedOpsMap = new Map();
          localOps.forEach(op => { if (op && op.id) mergedOpsMap.set(op.id, op); });
          backendOps.forEach(op => { if (op && op.id) mergedOpsMap.set(op.id, op); });
          return {
            ...localFallback,
            ...data,
            pendingOperations: Array.from(mergedOpsMap.values())
          };
        }
      } catch (e) {}
      return Promise.resolve(localFallback);
    },

    /**
     * Enregistre ou met à jour une opération en cours.
     * @param {Object} opData Données de l'opération
     * @param {string} [opId] Identifiant existant en cas de modification
     * @returns {Promise<Object>} Opération enregistrée
     */
    async savePendingOperation(opData, opId) {
      const localResult = app().PendingOperationsService.savePendingOperation(opData, opId);
      try {
        await safeFetch(API_BASE_URL + '/pending-operations/force', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(localResult)
        });
      } catch (e) {}
      return Promise.resolve(localResult);
    },

    /**
     * Supprime une opération en cours.
     * @param {string} opId Identifiant de l'opération
     * @returns {Promise<Array>} Liste mise à jour des opérations en cours
     */
    async deletePendingOperation(opId) {
      try {
        await safeFetch(API_BASE_URL + '/pending-operations/ignore', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: opId, operationId: opId })
        });
      } catch (e) {}
      return Promise.resolve(app().PendingOperationsService.deletePendingOperation(opId));
    },

    /**
     * Remet une opération rapprochée en circulation (déliaison).
     * @param {string} opId Identifiant de l'opération
     * @returns {Promise<Array>} Liste mise à jour des opérations en cours
     */
    async unlinkPendingOperation(opId) {
      return Promise.resolve(app().PendingOperationsService.unlinkPendingOperation(opId));
    },

    /**
     * Lie manuellement une opération en cours à une transaction bancaire (rapprochement).
     * @param {string} opId Identifiant de l'opération
     * @param {string} txId Identifiant de la transaction bancaire
     * @param {string} txDate Date de la transaction bancaire
     * @returns {Promise<Array>} Liste mise à jour des opérations en cours
     */
    async linkPendingOperation(opId, txId, txDate) {
      return Promise.resolve(app().PendingOperationsService.linkPendingOperation(opId, txId, txDate));
    },

    /**
     * Déclenche l'algorithme de rapprochement automatique.
     * @returns {Promise<{matchCount: number, updatedOperations: Array}>}
     */
    async autoMatchPendingOperations() {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await fetch(API_BASE_URL + '/pending-operations/reconcile', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
          });
          if (res.ok) {
            const updated = await this.getPendingOperations();
            return {
              matchCount: 0,
              updatedOperations: updated.pendingOperations || []
            };
          }
        } catch (e) {}
      }
      return Promise.resolve(app().PendingOperationsService.autoMatchPendingOperations());
    },

    /**
     * Importe des opérations CB différées depuis un relevé CSV.
     * @param {Array} rawRows Lignes CSV
     * @param {Array} colRoles Rôles assignés aux colonnes
     * @param {Object} config Paramètres de format de date et d'extraction de date d'achat
     * @returns {Promise<Object>} Résumé de l'import
     */
    async importPendingCB(rawRows, colRoles, config) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await fetch(API_BASE_URL + '/pending-operations/import-cb', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rawRows, colRoles, config })
          });
          if (res.ok) return await res.json();
        } catch (e) {}
      }
      return Promise.resolve(app().PendingOperationsService.importPendingCB(rawRows, colRoles, config));
    },

    /**
     * Fusionne une opération manuelle avec une opération issue du relevé bancaire.
     * @param {string} manualOpId Identifiant de l'opération manuelle
     * @param {Object} bankOp Données de l'opération bancaire
     * @returns {Promise<void>}
     */
    async mergePendingOperation(manualOpId, bankOp) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await fetch(API_BASE_URL + '/pending-operations/merge', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ manualOpId, bankOp })
          });
          if (res.ok) return;
        } catch (e) {}
      }
      if (app().PendingOperationsService && app().PendingOperationsService.mergePendingOperation) {
        return Promise.resolve(app().PendingOperationsService.mergePendingOperation(manualOpId, bankOp));
      }
      return Promise.resolve();
    },

    /**
     * Force l'import d'une opération en doublon.
     * @param {Object} op Opération
     * @returns {Promise<Object>} Opération importée
     */
    async forceImportPendingOperation(op) {
      if (typeof fetch !== 'undefined') {
        try {
          const res = await fetch(API_BASE_URL + '/pending-operations/force', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(op)
          });
          if (res.ok) return;
        } catch (e) {}
      }
      return Promise.resolve(app().PendingOperationsService.forceImportPendingOperation(op));
    },

    /**
     * S'abonne aux changements des opérations en cours.
     * @param {Function} listener
     * @returns {Function} fonction de désabonnement
     */
    onPendingOperationsChanged(listener) {
      return app().PendingOperationsService.subscribePendingOperations(listener);
    },

    /**
     * Récupère les données de Pointage.
     * @returns {Promise<Object>}
     */
    async getPointage() {
      return Promise.resolve(app().PointageService.buildPointage());
    },

    /**
     * Sauvegarde les rapprochements de pointage pour un mois donné.
     * @returns {Promise<Array>}
     */
    async savePointageMatching(monthISO, newLinks) {
      return Promise.resolve(app().PointageService.savePointageMatching(monthISO, newLinks));
    },

    /**
     * S'abonne aux changements de pointage.
     * @param {Function} listener
     * @returns {Function} fonction de désabonnement
     */
    onPointageChanged(listener) {
      return app().PointageService.subscribePointage(listener);
    },

    /**
     * Récupère les données d'Analyse (Réel vs Prévisionnel, atterrissage, dérives).
     * @returns {Promise<Object>}
     */
    async getAnalyse(monthsBack) {
      const query = monthsBack !== undefined && monthsBack !== null ? '?monthsBack=' + monthsBack : '';
      return fetchJsonOrFallback('/analyse', () => app().AnalyseService.buildAnalyse(), query);
    },

    /**
     * S'abonne aux changements d'Analyse.
     * @param {Function} listener
     * @returns {Function} fonction de désabonnement
     */
    onAnalyseChanged(listener) {
      return app().AnalyseService.subscribeAnalyse(listener);
    },

    /**
     * Exporte l'intégralité du modèle de données (Sauvegarde).
     * @returns {Promise<Object>}
     */
    async getBudgetFull() {
      return Promise.resolve(app().BudgetStore.getData());
    },

    /**
     * Importe un jeu de données JSON complet dans le store front et vers le back-end PersistenceManager.
     * @param {Object|string} jsonData
     * @returns {Promise<Object>}
     */
    async importJSON(jsonData) {
      const data = typeof jsonData === 'string' ? JSON.parse(jsonData) : jsonData;
      app().BudgetStore.setData(data);
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/budget/import', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
          });
        } catch (e) {
          // Mode client seul / hors-ligne
        }
      }
      return data;
    },

    /**
     * Réinitialise l'ensemble des données aux valeurs par défaut.
     * @returns {Promise<Object>}
     */
    async resetData() {
      const defaultData = JSON.parse(JSON.stringify(app().DEFAULT_DATA || {}));
      app().BudgetStore.setData(defaultData);
      if (typeof fetch !== 'undefined') {
        try {
          await fetch(API_BASE_URL + '/budget/reset', { method: 'POST' });
        } catch (e) {
          // Mode client seul / hors-ligne
        }
      }
      return defaultData;
    }
  };

  // Export de computeRetirementProjection pour compatibilité avec retraite-view.js
  exports.computeRetirementProjection = exports.computeRetirementProjection || (typeof window !== 'undefined' ? window.BudgetApp?.computeRetirementProjection : undefined);
  
  exports.BudgetApi = BudgetApi;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
