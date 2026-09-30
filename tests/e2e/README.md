# Tests E2E Playwright — socle commun (VT-000)

Ce dossier contient les scénarios Playwright. La stratégie est décrite dans
`back/server/doc/architecture/16-tests.md` et le backlog dans `17-backlog-tests-patchs.md`.

## Lancer les tests

```bash
bun run test:e2e      # ou : npm run test:e2e
```

`setup.js` démarre Spring Boot (`mvn spring-boot:run`) et `view/server.js` s'ils ne tournent pas déjà,
puis lance Playwright. URLs par défaut : API `http://localhost:8080/api/v1`, front `http://localhost:3000`
(surcharge possible par `E2E_API_URL` / `E2E_FRONT_URL` pour les helpers ; `playwright.config.js`
garde son `baseURL`).

## Organisation

| Fichier | Rôle |
|---|---|
| `functional.spec.js` | Tests fonctionnels historiques. À ne modifier que pour un besoin de helper commun. |
| `socle.spec.js` | Vérifie le socle commun (reset/import, nettoyage navigateur). Aucun test métier. |
| `helpers/constants.js` | `API`, `FRONT`, `REFERENCE_DATASET`, `BROWSER_STORAGE_KEYS`. |
| `helpers/backend.js` | `expectBackendCall`, `waitForReactMount`. |
| `helpers/state.js` | `resetBackendState`, `importDataset`, `resetToReferenceState`, `clearBrowserState`. |
| `helpers/browser.js` | `disableJsFallback`, `expectFreshBrowserState`, `gotoAndExpectBackend`, `actAndExpectBackend`, `readJsFallbackFlag` (VT-200). |
| `no-fallback.spec.js` | Vérifie le mode sans fallback JS (VT-200). |
| `read-reload.spec.js` | Lecture + reload sans fallback pour Overview, Trésorerie, Patrimoine, Paramètres et Analyse (VT-210). |
| `mutation-patrimoine.spec.js` | Mutation d'un placement depuis Patrimoine, relue par Trésorerie et Overview, puis après reload (VT-220). |
| `settings-multi-domain.spec.js` | Paramètres de trois propriétaires (Retraite, Trésorerie, Objectifs) relus après reload (VT-230). |
| `helpers/index.js` | Point d'entrée : `require('./helpers')`. |
| `fixtures/budget-familial.json` | Dataset canonique de référence. |

Règle : les nouveaux parcours E2E vont dans des fichiers `tests/e2e/<famille>.spec.js` distincts
(contrainte C1 de `16-tests.md`) et importent leurs constantes et helpers depuis `./helpers`.
Un helper commun n'est ajouté que s'il réduit réellement les conflits entre patchs parallèles.

## Dataset canonique

`fixtures/budget-familial.json` est le **seul** dataset autorisé pour les scénarios de référence :
il est versionné, synthétique et indépendant de toute donnée locale (contrainte C3).
`REFERENCE_DATASET` pointe toujours dessus. `resolveLocalOrReferenceDataset()` (préférence pour
`data/budget-familial.json` ou `budget-familial.json` à la racine, non versionnés) ne subsiste que pour
`functional.spec.js` et ne doit pas être utilisé dans un scénario de référence.

Contenu (un élément par liste, valeurs stables) :

| Section | Contenu |
|---|---|
| `settings` | naissance 1990, retraite à 64 ans, simulation jusqu'à 85 ans, inflation 2 %, pivot manuel au 2026-01-01, solde de départ 5 000, PASS 2026 47 100 (croissance 1,5 %) |
| `incomes` | `inc_1` « Salaire » : 3 000 €/mois, 2026-01-01 → 2053-12-31, croissance 1 % |
| `charges` | `chg_1` « Loyer » : 900 €/mois, même période, croissance 2 % |
| `placements` | `plc_1` « PEA » (Actions) : solde 10 000 au 2026-01-01, 200 €/mois, taux 2 % / 4 % / 7 % |
| `realEstate` | `re_1` « Residence Principale » : 250 000 (2026), croissance 1,5 % |
| `retirement` | une personne `p_1` « Alice » (1990) : 140 trimestres validés, historique salaires 2023-2025, 2 500 points AGIRC |
| `oneoff` | `oo_1` « Achat voiture » : 15 000 au 2026-06-01 |
| `variableIncomes` / `variableOverrides` | `vi_1` « Prime annuelle » (10 % du Salaire, 2026-2053) ; `vo_1` : 5 000 en 2026 |
| `taxChildren`, `taxBrackets`, `taxRateOverrides`, `taxActualOverrides`, `transfers`, `assetCategories` | vides |
| `bankImport` | `transactions`, `categories`, `matchings` vides |

Limites connues (à traiter dans les patchs dédiés, pas ici) :

- `bankImport` est vide : les scénarios Banque → Pointage → Analyse (VT-120, F3) auront besoin d'un
  dataset complémentaire versionné à côté de celui-ci.
- Les valeurs de sortie attendues (KPIs, projection retraite, impôts…) ne sont pas figées ici :
  elles seront capturées par les tests de caractérisation VT-100.

## Remise à l'état de départ

**Serveur.** `POST /budget/reset` puis `POST /budget/import` (endpoints déjà exposés, aucun contrat modifié) :

```js
const { resetToReferenceState } = require('./helpers');

test.beforeEach(async ({ request }) => {
  await resetToReferenceState(request);
});
```

`resetBackendState(request)` et `importDataset(request, chemin?)` sont disponibles séparément.

**Navigateur.** Chaque test Playwright reçoit un contexte neuf : `localStorage` et `sessionStorage`
sont vides au départ. `clearBrowserState(page)` n'est nécessaire que si la page de l'application a déjà
été chargée dans ce contexte. Il vide les clés listées dans `BROWSER_STORAGE_KEYS`
(`budget_familial_data_v1`, `budget_familial_constant_euros`, `budgetapp.syncQueue.v1`,
`budgetapp.sidebarCollapsed`, `mfb.rateSuggestion.amplitudePt`), puis `localStorage.clear()` et
`sessionStorage.clear()`.

Il ne remplace pas l'override `DISABLE_JS_FALLBACK` installé avant le chargement des scripts :
ce mécanisme est fourni par VT-200 (voir ci-dessous ; la valeur de production reste `false`).

## Mode sans fallback JS (VT-200)

`view/config.js` lit `sessionStorage["mfb.test.disableJsFallback"]` : si la valeur est `"true"`,
`window.DISABLE_JS_FALLBACK` passe à `true`. Sans cette clé, la valeur de production (`false`) est
inchangée. L'override doit être posé **avant** le chargement des scripts, ce que fait `disableJsFallback` :

```js
const { disableJsFallback, gotoAndExpectBackend, expectFreshBrowserState } = require('./helpers');

test('scénario critique', async ({ page, request }) => {
  await resetToReferenceState(request);
  await disableJsFallback(page);            // avant le premier page.goto()
  const res = await gotoAndExpectBackend(page, '/overview.html', '/overview');
  expect(res.status()).toBe(200);
  await expectFreshBrowserState(page);      // localStorage vide : aucun état local historique
});
```

- Contexte vierge : Playwright fournit un contexte neuf par test ; `expectFreshBrowserState` le prouve.
- `actAndExpectBackend(page, apiPath, method, action)` arme l'attente avant l'action (écritures critiques).
- Un appel backend en échec fait remonter `[DISABLE_JS_FALLBACK]` dans la console au lieu d'un repli local.

## Lecture + reload (VT-210)

`read-reload.spec.js` applique le même schéma à Overview, Trésorerie, Patrimoine, Paramètres et Analyse :
`resetToReferenceState` → `disableJsFallback` → `gotoAndExpectBackend` (lecture 2xx exigée) →
`expectFreshBrowserState` → assertions métier minimales sur la réponse et sur l'écran → `page.reload()` avec
nouvelle lecture backend exigée → mêmes assertions. Les valeurs attendues proviennent du dataset canonique
(`REFERENCE_DATASET`), jamais d'une donnée locale.

Le test Paramètres modifie en plus le dataset côté serveur (`startBalance`, via `importDataset` sur une copie
temporaire) entre les deux chargements : la nouvelle valeur ne peut apparaître après reload que si l'écran
relit le backend.

## Mutation Patrimoine → Trésorerie → Overview (VT-220)

`mutation-patrimoine.spec.js` déclenche une écriture depuis l'écran Patrimoine (tiroir d'un placement) et exige la
réponse 2xx de `POST /patrimoine/placements`. L'effet est ensuite relu **uniquement dans des réponses HTTP du
backend** : `GET /patrimoine`, puis l'épargne 2027 du `cashflow` de `GET /tresorerie` et de `GET /overview`
(PEA de référence : 200 €/mois, soit 2 400 € en année pleine), directement puis lue par les pages elles-mêmes
avant et après `page.reload()`. Deux cas : modification du versement mensuel du PEA (200 → 300) et création d'un
placement (50 €/mois dès 2026-01-01). L'IHM ne sert qu'à déclencher la mutation et à rouvrir le tiroir.

## Paramètres multi-domaines (VT-230)

`settings-multi-domain.spec.js` modifie depuis l'écran Paramètres un champ de chacun de trois propriétaires
(`retireAge` : Retraite, `startBalance` : Trésorerie, `goalSecureHorizonMonths` : Objectifs, store dédié), exige
`PUT /settings` 2xx pour chacun, puis relit `GET /settings`, l'Overview (année de retraite, trésorerie de départ)
et l'écran Paramètres avant et après `page.reload()`.

Le test importe une copie temporaire du dataset canonique avec `sweepEnabled: false` : tant que
`BudgetMutationService.updateTaxSettings` lève une `NullPointerException` sur `sweepEnabled` absent (constat
VT-340), toute modification de paramètre échoue sur le dataset partagé, qui reste inchangé.
