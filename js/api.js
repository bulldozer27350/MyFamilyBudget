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
    },

    /**
     * Paramètres : settings généraux, date pivot, virement auto et catégories d'actifs.
     * @returns {Promise<Object>} modèle de lecture de l'onglet Paramètres
     */
    async getSettings() {
      return Promise.resolve(app().SettingsService.buildSettings());
    },

    /**
     * Met à jour un champ des settings.
     * @returns {Promise<void>}
     */
    async updateSettingsField(field, value) {
      return Promise.resolve(app().SettingsService.updateSettingsField(field, value));
    },

    /**
     * Met à jour une cellule d'une catégorie d'actif.
     * @returns {Promise<void>}
     */
    async updateAssetCategory(id, field, value) {
      return Promise.resolve(app().SettingsService.updateAssetCategory(id, field, value));
    },

    /**
     * Ajoute une nouvelle catégorie d'actif.
     * @returns {Promise<void>}
     */
    async addAssetCategory(row) {
      return Promise.resolve(app().SettingsService.addAssetCategory(row));
    },

    /**
     * Supprime une catégorie d'actif.
     * @returns {Promise<void>}
     */
    async removeAssetCategory(id) {
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
      return Promise.resolve(app().BankImportService.buildBankImport());
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
      return Promise.resolve(app().BankImportService.addBankImportCategory(category));
    },

    /**
     * Met à jour une catégorie d'import bancaire.
     * @returns {Promise<void>}
     */
    async updateBankImportCategory(id, field, value) {
      return Promise.resolve(app().BankImportService.updateBankImportCategory(id, field, value));
    },

    /**
     * Supprime une catégorie d'import bancaire.
     * @returns {Promise<void>}
     */
    async removeBankImportCategory(id) {
      return Promise.resolve(app().BankImportService.removeBankImportCategory(id));
    },

    /**
     * Ajoute une nouvelle règle de catégorisation.
     * @returns {Promise<void>}
     */
    async addBankImportRule(rule) {
      return Promise.resolve(app().BankImportService.addBankImportRule(rule));
    },

    /**
     * Met à jour une règle de catégorisation.
     * @returns {Promise<void>}
     */
    async updateBankImportRule(id, field, value) {
      return Promise.resolve(app().BankImportService.updateBankImportRule(id, field, value));
    },

    /**
     * Supprime une règle de catégorisation.
     * @returns {Promise<void>}
     */
    async removeBankImportRule(id) {
      return Promise.resolve(app().BankImportService.removeBankImportRule(id));
    },

    /**
     * Réapplique les règles aux transactions non catégorisées.
     * @returns {Promise<void>}
     */
    async recalculateBankImportRules() {
      return Promise.resolve(app().BankImportService.recalculateBankImportRules());
    },

    /**
     * Met à jour la catégorie d'une transaction et optionnellement crée une règle.
     * @returns {Promise<void>}
     */
    async setBankImportTransactionCategory(txId, categoryId, ruleKeyword) {
      return Promise.resolve(app().BankImportService.setBankImportTransactionCategory(txId, categoryId, ruleKeyword));
    },

    /**
     * Force l'import d'une transaction marquée comme doublon.
     * @returns {Promise<void>}
     */
    async forceImportBankTransaction(tx) {
      return Promise.resolve(app().BankImportService.forceImportBankTransaction(tx));
    },

    /**
     * Importe des transactions depuis un fichier CSV traité.
     * @returns {Promise<Object>} Résumé de l'import
     */
    async importBankTransactions(rawRows, colRoles, mapping) {
      return Promise.resolve(app().BankImportService.importBankTransactions(rawRows, colRoles, mapping));
    },

    /**
     * S'abonne aux changements des données d'Import Bancaire (saisie, autre onglet, import…).
     * Sera remplacé par du polling ou du websocket côté back-end.
     * @returns {Function} fonction de désabonnement
     */
    onBankImportChanged(listener) {
      return app().BankImportService.subscribeBankImport(listener);
    }
  };

  // Export de computeRetirementProjection pour compatibilité avec retraite-view.js
  exports.computeRetirementProjection = exports.computeRetirementProjection || window.BudgetApp?.computeRetirementProjection;
  
  exports.BudgetApi = BudgetApi;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
