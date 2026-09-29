'use strict';

// Constantes communes aux scenarios Playwright (VT-000).
// Objectif : une seule definition des URLs et du dataset de reference, afin que
// les futurs fichiers tests/e2e/*.spec.js (VT-2xx, VT-5xx) ne dupliquent rien.

const fs   = require('fs');
const path = require('path');

const API   = process.env.E2E_API_URL   || 'http://localhost:8080/api/v1';
const FRONT = process.env.E2E_FRONT_URL || 'http://localhost:3000';

const ROOT = path.resolve(__dirname, '..', '..', '..');

// Dataset canonique versionne : seul jeu de donnees autorise pour les scenarios
// de reference (determinisme, cf. 16-tests.md, contrainte C3). Documente dans
// tests/e2e/README.md.
const REFERENCE_DATASET = path.resolve(__dirname, '..', 'fixtures', 'budget-familial.json');

/**
 * Comportement historique de functional.spec.js : priorite a un jeu de donnees
 * personnel local (data/ ou racine, jamais commite) pour des essais manuels,
 * repli sur le dataset de reference. A NE PAS utiliser dans les scenarios de
 * reference : utiliser REFERENCE_DATASET.
 */
function resolveLocalOrReferenceDataset() {
  const candidates = [
    path.join(ROOT, 'data', 'budget-familial.json'),
    path.join(ROOT, 'budget-familial.json'),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  return REFERENCE_DATASET;
}

// Cles localStorage utilisees par le front (view/js/models.js, data-store.js,
// sync-status.js, app-layout.js, rate-suggestion.js). Ce sont les etats locaux
// susceptibles de masquer une regression backend : voir clearBrowserState().
const BROWSER_STORAGE_KEYS = [
  'budget_familial_data_v1',
  'budget_familial_constant_euros',
  'budgetapp.syncQueue.v1',
  'budgetapp.sidebarCollapsed',
  'mfb.rateSuggestion.amplitudePt',
];

module.exports = {
  API,
  FRONT,
  ROOT,
  REFERENCE_DATASET,
  BROWSER_STORAGE_KEYS,
  resolveLocalOrReferenceDataset,
};
