/**
 * Façade API (Pattern Strangler Fig)
 *
 * Point d'entrée asynchrone unique des composants React vers les règles métier et le
 * stockage. Toutes les méthodes retournent une Promise : le jour où le traitement passera
 * sur un back-end Java Spring Boot, seule l'implémentation interne changera (fetch),
 * pas les composants.
 *
 * Fonctionnalités déjà migrées : Vue d'ensemble, Trésorerie, Patrimoine, Retraite, Impôts.
 */
(function (exports) {
  'use strict';

  function app() {
    return typeof window !== 'undefined' ? window.BudgetApp || exports : exports;
  }

  const BudgetApi = {
    /**
     * Vue d'ensemble : KPIs, projections (trésorerie, patrimoine) et jauges FIRE.
     * @param {{useConstantEuros?: boolean}} [options]
     * @returns {Promise<Object>} modèle de lecture de la Vue d'ensemble
     */
    async getVueDensemble(options) {
      return Promise.resolve(app().OverviewService.buildOverview(options));
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
      return Promise.resolve(app().TresorerieService.buildTresorerie(options));
    },

    /**
     * Met à jour une cellule d'une ligne de Trésorerie.
     * @returns {Promise<void>}
     */
    async updateTresorerieLigne(listKey, id, field, value) {
      return Promise.resolve(app().TresorerieService.updateTresorerieLigne(listKey, id, field, value));
    },

    /**
     * Ajoute une ligne vide du type demandé (incomes, charges, oneoff, variableIncomes, variableOverrides).
     * @returns {Promise<void>}
     */
    async addTresorerieLigne(listKey, options) {
      return Promise.resolve(app().TresorerieService.addTresorerieLigne(listKey, options));
    },

    /**
     * Supprime une ligne de Trésorerie.
     * @returns {Promise<void>}
     */
    async removeTresorerieLigne(listKey, id) {
      return Promise.resolve(app().TresorerieService.removeTresorerieLigne(listKey, id));
    },

    /**
     * Applique une suggestion d'ajustement du montant mensuel (dérive budget vs réel).
     * @returns {Promise<void>}
     */
    async applyTresorerieAjustement(lineId, kind, newMonthly) {
      return Promise.resolve(app().TresorerieService.applyTresorerieAjustement(lineId, kind, newMonthly));
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
      return Promise.resolve(app().PatrimoineService.buildPatrimoine(options));
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
      return Promise.resolve(app().PatrimoineService.updatePatrimoineLigne(listKey, id, field, value));
    },

    /**
     * Ajoute une ligne (placements, transfers, loans, realEstate).
     * Si `row` est fourni, il est persisté tel quel (création depuis le tiroir).
     * @returns {Promise<void>}
     */
    async addPatrimoineLigne(listKey, row) {
      return Promise.resolve(app().PatrimoineService.addPatrimoineLigne(listKey, row));
    },

    /**
     * Supprime une ligne de Patrimoine.
     * @returns {Promise<void>}
     */
    async removePatrimoineLigne(listKey, id) {
      return Promise.resolve(app().PatrimoineService.removePatrimoineLigne(listKey, id));
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
     * S'abonne aux changements des données d'Impôts (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onImpotsChanged(listener) {
      return app().ImpotsService.subscribeImpots(listener);
    }
  };

  // Export de computeRetirementProjection pour compatibilité avec retraite-view.js
  exports.computeRetirementProjection = exports.computeRetirementProjection || window.BudgetApp?.computeRetirementProjection;
  
  exports.BudgetApi = BudgetApi;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
