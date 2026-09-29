# 16 — Stratégie de tests avant séparation Maven

Statut : 🟡 à valider

## Pourquoi ce fichier existe

La création des modules Maven va transformer des dépendances aujourd'hui tolérées par le compilateur
monolithique en dépendances explicitement interdites. Le risque principal n'est donc pas seulement de
casser la compilation : c'est de modifier subtilement le comportement lors du déplacement des frontières
entre calcul, orchestration, API et persistance.

Ce document fixe le **dispositif de validation fonctionnelle et technique** à mettre en place avant de
commencer la séparation physique en modules Maven. Le détail des travaux est dans
[`17-backlog-tests-patchs.md`](17-backlog-tests-patchs.md).

L'objectif n'est pas d'augmenter artificiellement la couverture. Il est de disposer de trois filets
complémentaires :

1. des tests unitaires rapides pour les moteurs purs ;
2. des tests d'intégration backend pour les assemblages et la persistance ;
3. des scénarios Playwright traversant plusieurs écrans et opérations, capables de détecter une rupture
de comportement qu'un test unitaire ne voit pas.

## Principe directeur : la base reste l'autorité

Le navigateur peut encore contenir des mécanismes de secours et un état local historique. Pour les tests
préparatoires à la modularisation, ils ne doivent pas pouvoir masquer une régression backend/persistance.

Le dépôt contient déjà `window.DISABLE_JS_FALLBACK` dans `view/config.js`. `view/js/api.js` l'utilise pour
faire remonter une erreur au lieu d'exécuter le fallback JS lorsqu'un appel backend échoue.

Cette capacité est un **mode de test**, pas un nouveau comportement fonctionnel par défaut.

La règle de validation est :

```text
Navigateur sans état local utile
        ↓
API / Spring Boot
        ↓
Base H2 ou PostgreSQL
        ↓
réponse API
        ↓
IHM
```

Pour les tests critiques, un résultat affiché après une écriture doit être confirmé par une relecture
backend, puis, pour les scénarios de persistance, après redémarrage de Spring Boot.

## Constat sur l'existant

### Backend

Le dépôt possède déjà un socle significatif :

- `BusinessLogicIntegrationTest` avec `@SpringBootTest` + `MockMvc` ;
- un grand scénario de caractérisation autour de `budget-familial.json` ;
- des tests de composants pour plusieurs moteurs (`RetirementCalculationServiceTest`,
  `PatrimoineProjectionServiceTest`, `PlacementEvolutionServiceTest`,
  `TresorerieCalculationServiceComponentTest`, `OverviewCalculationServiceComponentTest`,
  `LoanAdviceCalculationServiceComponentTest`, `PlacementRateSuggestionServiceTest`,
  `AnalyseInputTest`, `NotificationInputsTest`, etc.) ;
- `RatePrecisionPersistenceTest` sous `@DataJpaTest` ;
- `PersistenceAdaptersTest`, dont les assertions sont aujourd'hui encore légères ;
- des garde-fous ArchUnit couvrant déjà les calculateurs principaux.

Le travail à faire est donc principalement un travail de **resserrement des garanties et d'orchestration des
scénarios**, pas une réécriture complète de la stratégie de tests. Le palier de ports de lecture historique
(`RF-B00`/`RF-B01`) est considéré comme déjà acquis ; les nouveaux tests doivent empêcher son contournement.

### Frontend

`tests/e2e/functional.spec.js` vérifie déjà la présence d'appels backend pour plusieurs écrans et opérations.
Le helper `expectBackendCall(...)` est particulièrement utile : si le fallback JS prend le relais, le test
échoue car la réponse HTTP attendue n'arrive jamais.

En revanche, ces tests ne suffisent pas encore à démontrer systématiquement que :

- une écriture visible dans l'IHM est réellement persistée en base ;
- un reload/restart ne reconstruit pas la donnée depuis `localStorage` ;
- plusieurs écrans consécutifs restent cohérents après une même mutation ;
- un backend arrêté en mode `DISABLE_JS_FALLBACK=true` fait bien échouer l'opération au lieu de produire
  silencieusement une donnée locale.

### Point important sur le service worker

`service-worker.js` concerne les notifications push et n'est pas le mécanisme qui sert de cache aux données
métier de l'application. Le dispositif de preuve doit donc viser principalement `localStorage`, `BudgetStore`
et les fonctions de fallback de `view/js/api.js`.

## Objectifs

### O1 — Stabiliser une référence fonctionnelle

Conserver un ou plusieurs jeux de données déterministes et des assertions de résultat pour les domaines
Retraite, Fiscalité, Patrimoine, Trésorerie, Banque/Pointage, Prêts et Overview/Analyse.

### O2 — Prouver les assemblages inter-domaines

Vérifier des chaînes de calcul complètes, par exemple :

```text
revenu / paramètres retraite
    → projection retraite
    → fiscalité
    → trésorerie
    → overview
```

et :

```text
transaction bancaire
    → pointage / matching
    → analyse
    → KPIs / landing data
```

### O3 — Prouver la persistance réelle

Pour une mutation critique :

```text
POST/PATCH
→ GET
→ reload navigateur
→ GET
→ redémarrage Spring
→ GET
```

Le dernier GET doit restituer la même information métier attendue.

### O4 — Prouver l'absence de faux positif frontend

En environnement de validation :

- `DISABLE_JS_FALLBACK=true` ;
- contexte Playwright neuf ou `localStorage` nettoyé ;
- backend arrêté pour les tests diagnostiques négatifs ;
- aucune assertion ne doit accepter une donnée provenant uniquement du store JS.

Pour Playwright, le mécanisme recommandé est un override réservé au contexte de test (par exemple via `sessionStorage`
ou un paramètre de configuration lu par `view/config.js`), afin de ne jamais devoir modifier manuellement la valeur
production. Le contexte doit installer cet override **avant** le chargement des scripts de la page.

### O5 — Protéger les frontières d'architecture

Les tests d'architecture doivent continuer de jouer leur rôle pendant les refactorings : un moteur migré ne
peut pas réintroduire une dépendance vers `BudgetDataModel`, `PersistenceManager` ou les DTO/API par simple
commodité.

## Périmètre des tests à obtenir

| Couche | Finalité | Niveau attendu avant Maven |
|---|---|---|
| Moteurs purs | invariants mathématiques / règles métier | obligatoire |
| Factories / assemblers | composition des Inputs | obligatoire |
| Mapping | contrat domaine ↔ API / persistance | obligatoire sur les chemins migrés |
| Intégration backend | Spring + DB + transactions | obligatoire |
| Persistance PostgreSQL | comportement ORM réel | obligatoire pour les mutations migrées |
| Playwright | parcours utilisateur multi-opérations | obligatoire sur les scénarios de référence |
| ArchUnit | dépendances interdites | obligatoire |

## Scénarios frontend de référence

Le but n'est pas de tester chaque bouton. Les parcours ci-dessous doivent traverser suffisamment de couches
pour servir de tests de contrat fonctionnel.

### F1 — Import → Overview → modification → Overview

1. Charger un contexte navigateur neuf.
2. Activer `DISABLE_JS_FALLBACK`.
3. Importer `budget-familial.json` via `/budget/import`.
4. Contrôler la réponse HTTP 2xx.
5. Afficher Overview et vérifier les KPIs de référence.
6. Modifier une donnée représentative d'un domaine (par exemple une ligne de trésorerie ou un paramètre).
7. Relire l'API correspondante.
8. Recharger la page.
9. Vérifier que l'Overview reflète la valeur persistée.

### F2 — Mutation patrimoine → Trésorerie → Overview

Créer/modifier un placement ou une valeur patrimoniale dont l'effet est observable dans la projection.
Relire ensuite les trois surfaces : Patrimoine, Trésorerie et Overview. Le but est de détecter un branchement
partiellement migré où un écran utiliserait encore une autre source de données.

### F3 — Banque → Pointage → Analyse

1. Importer un état bancaire contenant transactions, catégories et matchings.
2. Vérifier la réponse `GET /pointage`.
3. Exécuter la séquence de ventilation/matching représentative.
4. Relire `GET /analyse`.
5. Vérifier catégories, montants réels, KPIs et lignes d'atterrissage.

### F4 — Opération engagée → modification → reload

Le scénario déjà présent dans `functional.spec.js` doit devenir un test explicite de persistance : création,
modification (note, catégorie, ventilation), lecture backend, reload et lecture backend.

### F5 — Paramètres multi-domaines

Modifier plusieurs familles de paramètres qui appartiennent à des domaines différents. Vérifier que :

- la façade `/settings` continue de fournir une vue cohérente ;
- la commande d'écriture touche chaque propriétaire ;
- une relecture après reload restitue toutes les valeurs ;
- une erreur sur un champ invalide ne laisse pas une partie de la mutation appliquée.

### F6 — Diagnostic sans backend

Avec `DISABLE_JS_FALLBACK=true` et un backend volontairement indisponible :

- une lecture doit échouer explicitement ;
- une écriture doit échouer explicitement ;
- aucune donnée de fallback ne doit être affichée comme si elle venait du serveur.

Ce scénario est **négatif et diagnostique** : il ne doit jamais être activé comme mode de fonctionnement normal
de l'application.

## Persistance : ce qu'un test doit prouver

Un test de persistance utile doit distinguer quatre événements :

```text
A. mutation acceptée par l'API
B. lecture immédiate réussie
C. nouvelle instance de navigateur réussie
D. nouvelle instance de Spring réussie
```

Une réussite en A/B sans C/D n'est pas suffisante pour valider une migration JPA.

Lorsque possible, le scénario doit fonctionner contre PostgreSQL et non uniquement H2. H2 reste très utile
pour la boucle locale rapide, mais les différences de dialecte, de transactions et de LOB peuvent masquer un
problème de production.

## Tests négatifs à privilégier

Quelques tests négatifs ont un meilleur rendement que beaucoup de tests nominaux :

- backend indisponible + fallback désactivé ;
- erreur d'écriture : l'ancienne valeur reste observable après échec ;
- mutation multi-domaines invalide : absence d'état partiellement appliqué ;
- concurrence sur une même ressource : pas de corruption ou de perte silencieuse ;
- données inconnues / optionnelles : pas de `NullPointerException` lors d'une projection ;
- réimport d'un dataset connu : état final déterministe.

## Contraintes

### C1 — Les tests ne doivent pas devenir dépendants du refactoring

Les assertions doivent viser le comportement métier ou le contrat API, pas les noms de classes/packages internes
que les futurs modules Maven feront évoluer. Pour faciliter le travail parallèle, les nouveaux parcours E2E doivent
être ajoutés dans des fichiers `tests/e2e/*.spec.js` distincts par famille de scénario ; `functional.spec.js` ne doit
être modifié qu'en cas de besoin de helper commun.

### C2 — Les tests de calcul doivent rester rapides

Aucun calcul pur ne doit nécessiter Spring, JPA ou une base.

### C3 — Les tests E2E doivent être déterministes

Le dataset de référence doit être versionné, stable et isolé. Les données locales présentes dans `data/` ne
devraient jamais être nécessaires à la CI.

### C4 — Les tests ne doivent pas partager un état implicite

Chaque scénario doit pouvoir réinitialiser son état via l'API de reset/import ou via un contexte de test dédié.

### C5 — Le test de redémarrage doit contrôler la bonne frontière

Le redémarrage doit concerner Spring Boot / sa connexion DB, pas uniquement un reload de page.

## Limites et décisions reportées

### L1 — Plusieurs bases physiques

Ce document ne demande pas une base par module. La preuve recherchée est l'autorité de la persistance et la
bonne séparation des responsabilités, pas une topologie distribuée.

### L2 — Offline complet

Le chantier ne cherche pas à supprimer `BudgetStore` ni à réimplémenter tout le mode offline. Le mode
`DISABLE_JS_FALLBACK` sert uniquement à rendre les tests non ambigus.

### L3 — Tests de snapshots exhaustifs

Un snapshot intégral de chaque réponse API serait fragile. Les snapshots sont réservés aux scénarios de
caractérisation où ils apportent une protection réelle contre une régression globale.

### L4 — Tests de charge

La modularisation n'est pas un chantier de performance. Les tests de charge sont hors périmètre sauf problème
mesuré pendant la migration.

## Critères de sortie

Le chantier de tests est considéré comme prêt lorsque :

- les scénarios backend de référence sont verts ;
- au moins les parcours frontend F1 à F5 sont verts avec fallback désactivé ;
- F6 démontre explicitement l'échec en absence de backend ;
- au moins un scénario de mutation critique prouve la persistance après redémarrage Spring ;
- les tests de mapping critiques sont isolés ;
- ArchUnit protège les domaines déjà migrés ;
- aucun test critique ne dépend d'une donnée locale non versionnée ;
- le passage PostgreSQL est identifié et exécuté pour les mutations JPA migrées.

## Ordonnancement global

Le détail opérationnel est dans [`17-backlog-tests-patchs.md`](17-backlog-tests-patchs.md).

Le principe retenu est :

```text
                   ┌─ VT-100 Backend caractérisation ─┐
VT-000 ────────────┼─ VT-200 Front fallback diagnostique ─┤
                   ├─ VT-300 Mapping ────────────────────┤
                   └─ VT-400 ArchUnit/tests architecture ┘
                                      ↓
                         VT-500 Scénarios E2E
                                      ↓
                         VT-600 Persistance restart
                                      ↓
                              VT-700 Gate Maven
```

Les branches VT-100/200/300/400 peuvent démarrer en parallèle. Les scénarios lourds et les tests de restart
ne sont lancés qu'après stabilisation du socle correspondant.
