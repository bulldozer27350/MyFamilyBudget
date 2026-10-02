# 14 — Checklist « prêt pour les modules Maven »

Statut : 🟡 à valider

Le découpage Maven ne doit démarrer que lorsque les conditions ci-dessous sont réunies. Cases à
cocher au fur et à mesure, domaine par domaine (ne pas attendre d'avoir tout coché une seule fois
à la fin).

## Modèles

- [ ] `BudgetDataModel` n'est plus un argument des calculateurs métier (voir l'indicateur de fin
      de chantier dans [11-domaine-overview.md](11-domaine-overview.md)).
- [ ] Les `SettingsModel` composites ont été séparés conceptuellement (voir
      [12-settings.md](12-settings.md)).
- [x] Les paramètres retraite dupliqués dans Settings (`pass2026`, `passGrowthRate`) ont été
      tranchés (SET-040 : `RetirementModel` est la seule source ; `SettingsDto` les expose comme vue composite).
- [ ] Chaque modèle persistant possède un propriétaire explicite.

## Entrées

- [ ] Chaque use case dispose d'un Input dédié.
- [ ] Aucun Input n'est un simple alias de `BudgetDataModel`.
- [ ] Les projections inter-domaines sont définies (graphe de
      [00-principes.md](00-principes.md)).
- [ ] Les assemblers responsables des projections sont identifiés.

## Sorties

- [ ] Les résultats métier ne transportent plus `BudgetDataModel` (`OverviewResultModel.data`,
      `AnalyseResultModel.data`).
- [ ] Les résultats métier ne transportent plus de modèles sources inutiles.
- [ ] Les ViewModels API sont clairement séparés des résultats de calcul.

## Calculs

- [ ] Un seul moteur retraite existe (plus de triplication Overview/Trésorerie/Retraite).
- [ ] La fiscalité consomme un revenu de retraite déjà projeté.
- [ ] Patrimoine ne possède plus de mini-moteur de trésorerie caché.
- [ ] Trésorerie est séparée conceptuellement en plusieurs use cases (projection, moyennes
      réelles, suggestions).
- [ ] Notifications ne dépendent plus d'un contexte budget global.

## Persistance (palier 1 uniquement — voir [13-persistance.md](13-persistance.md))

- [ ] Les moteurs métier ne connaissent plus `PersistenceManager`.
- [ ] Les ports de lecture/écriture par domaine sont identifiés (implémentation encore adossée à
      `PersistenceManager` acceptée à ce stade).
- [ ] `PersistenceManager` est clairement identifié comme façade de transition.
- [ ] Les mutations génériques (`listKey`/`field`/`value`) sont progressivement remplacées par des
      commandes métier.

*Le palier 2 (entités JPA séparées par domaine) n'est délibérément pas une condition de cette
checklist — voir [13-persistance.md](13-persistance.md).*

## API

- [ ] Les tags OpenAPI correspondent aux capabilities définies.
- [ ] Les contrats composites hérités ont été identifiés.
- [ ] Le split OpenAPI est planifié.
- [ ] Les futurs modules peuvent posséder leurs contrats sans dépendre d'un contrat monolithique.

## Tests et garde-fous

- [ ] Les calculs critiques disposent de tests unitaires indépendants de la persistance.
- [ ] Les tests de mapping sont séparés des tests métier.
- [ ] Les tests d'intégration couvrent les assemblers/orchestration.
- [ ] Les scénarios de référence (fiscal, retraite, trésorerie, patrimoine, prêts) de l'étape 0
      sont conservés et toujours verts.
- [ ] Les règles de notification ont des tests unitaires indépendants.
- [ ] Une règle ArchUnit existe et passe pour chaque domaine migré (pas seulement en fin de
      chantier — voir [01-sequencement.md](01-sequencement.md)).

## Gate de validation des tests avant Maven (VT-600)

Le découpage Maven ne démarre que si **toutes** les commandes ci-dessous passent sur la même révision, en CI
(`.github/workflows/ci-cd.yml`, job `build-and-test`) puis en local. Les cases se cochent après une exécution
verte de ces commandes ; elles reflètent des patchs livrés dans [17-backlog-tests-patchs.md](17-backlog-tests-patchs.md),
dont plusieurs restent « à confirmer en CI » (compilation non vérifiée à leur rédaction).

### Commandes exactes

| Étape | CI (Linux) | Local (Windows, PowerShell, à la racine du dépôt) |
|---|---|---|
| Backend H2 + ArchUnit | `mvn -f back/server/pom.xml -B test` | `mvn -f back/server/pom.xml -B test` |
| Variante PostgreSQL (VT-320) | même commande ; service `postgres:16-alpine` et variables `MFB_TEST_POSTGRES_URL`, `_USER`, `_PASSWORD` | `docker compose up -d db`, puis `$env:MFB_TEST_POSTGRES_URL="jdbc:postgresql://localhost:5432/myfamilybudget"` (utilisateur et mot de passe par défaut `myfamilybudget`) avant la commande Maven. **Écrase le contenu de la base ciblée.** |
| Smoke JS | `bun run test` | `bun run test` (ou `npm run test`) |
| Playwright (F1 à F6) | `bunx playwright install --with-deps chromium` puis `bun run test:e2e` | `bunx playwright install chromium` puis `bun run test:e2e` (démarre Spring Boot et `view/server.js` s'ils ne tournent pas) |
| Aucun test ignoré | `node tests/gate/check-gate.js all` | `$env:MFB_REQUIRE_POSTGRES="true"; node tests/gate/check-gate.js all` |

`check-gate.js static` peut être lancé seul sans rien exécuter ; `reports` lit `back/server/target/surefire-reports`
et exige, en CI ou si `MFB_REQUIRE_POSTGRES=true`, que la variante PostgreSQL ait réellement tourné.

### Ce que le script de garde vérifie

- aucune annotation d'ignorance ou de condition (`@Disabled`, `@Ignore`, `@EnabledIf*`, `@DisabledIf*`, `assume*`) dans
  `back/server/src/test`, à l'exception de `@EnabledIfEnvironmentVariable("MFB_TEST_POSTGRES_URL")` de `RestartPersistenceTest` ;
- aucun `test.skip`, `test.fixme`, `test.fail`, `test.slow`, `.only(` dans `tests/e2e`, et `retries: 0` dans `playwright.config.js` ;
- ni `continue-on-error: true`, `-DskipTests`, `|| true` dans `ci-cd.yml`, ni `skipTests` / `testFailureIgnore` dans `pom.xml` ;
- aucun test marqué `<skipped>` dans les rapports Surefire (hors variante PostgreSQL sur un poste local sans PostgreSQL).

### Points de vigilance constatés

- La variante PostgreSQL est la seule exécution conditionnelle : sans `MFB_TEST_POSTGRES_URL`, elle est ignorée sur un poste
  local. Le gate local doit donc définir la variable ; en CI elle l'est toujours.
- Les règles ArchUnit n'utilisent plus `FreezingArchRule` (VT-400) : `archunit_store/` est vide et aucune violation n'est gelée donc tolérée.
- Distinction à respecter : « patch VT terminé » (le test demandé est livré) n'est pas « défaut résiduel corrigé ».
  Les deux constats hors périmètre relevés par VT-330 et VT-340 étaient des défauts réels, traités par des patchs
  distincts du backlog 19 et désormais **corrigés** : FIX-010 (`BudgetPersistenceGateway.saveBankImport` propage les
  erreurs de persistance) et FIX-020 (`BudgetMutationService.updateTaxSettings` ne dépend plus de `sweepEnabled`).
  VT-330 et VT-340 restent `Terminé` sans réserve ouverte.
- Le contournement `sweepEnabled: false` dans les datasets de VT-230 (`tests/e2e/settings-multi-domain.spec.js`) et de
  `MultiDomainAtomicityTest` est conservé mais n'est plus nécessaire ; son retrait éventuel n'est pas un prérequis.
- `bun run test` (`view/scratch/test-all-apis.js`) est un smoke test JS, pas un test de contrat ; il ne remplace aucune étape ci-dessus.

### Critères (un identifiant VT par ligne)

- [ ] VT-000 socle commun et dataset de référence ; VT-100 caractérisation backend ; VT-110 et VT-120 scénarios backend transversaux.
- [ ] VT-200, VT-210, VT-220, VT-230, VT-240 : scénarios frontend sans fallback JS, contexte vierge, relus après reload.
- [ ] VT-300 tests de mapping isolés ; VT-310 adaptateurs de persistance.
- [ ] VT-320 persistance après redémarrage Spring, **variante PostgreSQL exécutée**.
- [ ] VT-330 échec d'écriture sans modification de la mémoire ; VT-340 atomicité multi-domaines ; VT-350 concurrence
      (patchs livrés ; défauts résiduels corrigés par FIX-010 et FIX-020 ; case à cocher par GATE-010).
- [ ] VT-400 ArchUnit : aucun `BudgetDataModel`, `PersistenceManager` ni DTO OpenAPI dans les couches pures.
- [ ] VT-500 suite E2E de référence F1 à F6, sans donnée locale.
- [ ] VT-600 gate : `node tests/gate/check-gate.js all` vert en CI, aucun test obligatoire ignoré ou désactivé.

## Séparation physique des bases (hors périmètre de cette checklist)

Une éventuelle base physique par domaine reste une décision d'infrastructure ultérieure et
optionnelle, à évaluer seulement après stabilisation des modules Maven, et seulement si un
bénéfice concret apparaît (déploiement indépendant, montée en charge indépendante, contrainte de
sécurité/disponibilité). Elle n'est **pas** un objectif de ce chantier.
