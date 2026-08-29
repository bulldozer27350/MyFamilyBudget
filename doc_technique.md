# MyFamilyBudget — Documentation d'architecture

> **But de ce document** : donner à un humain (toi, dans 2 ans) ou à un agent IA de quoi comprendre en quelques minutes comment le code est organisé, pourquoi il est organisé ainsi, où chercher pour corriger un bug ou ajouter une fonctionnalité, et quels pièges connus éviter. Ce n'est pas un manuel utilisateur (celui-ci existe déjà : `view/Manuel utilisateur.md`) — c'est un document de conception.
>
> Dernière mise à jour : reflète l'état du dépôt après le correctif du bug de duplication `budget_data` (voir §7.2).

---

## 1. Vue d'ensemble fonctionnelle

MyFamilyBudget est une application de gestion de budget familial multi-onglets :

| Onglet | Fichier de vue | Rôle métier |
|---|---|---|
| Vue d'ensemble | `overview-view.js` | KPIs, projections combinées, jauges FIRE |
| Trésorerie | `cashflow-view.js` | Revenus, charges, dépenses ponctuelles, primes, courbe de trésorerie |
| Patrimoine | `patrimoine-view.js` | Placements, transferts, crédits, immobilier, courbe à 3 scénarios (pessimiste/correct/optimiste) |
| Retraite | `retraite-view.js` | Projection de pension par personne |
| Impôts | `impots-view.js` | Foyer fiscal, barème progressif, quotient familial, simulateur PAS |
| Paramètres | `settings-view.js` | Réglages généraux, catégories d'actifs, date pivot |
| Import bancaire | `import-view.js` | Import de relevés CSV, catégorisation automatique, règles |
| Opérations en cours | `pending-view.js` | Chèques émis, CB différées, rapprochement bancaire |
| Pointage | `pointage-view.js` | Rapprochement mensuel budget prévisionnel / réel |
| Analyse | `analyse-view.js` | Comparaisons mensuelles, dérive budgétaire |

Chaque onglet est une page HTML statique indépendante (`view/*.html`) qui charge un jeu de scripts communs puis sa propre vue React.

---

## 2. Architecture générale

### 2.1 Les deux modes de déploiement

**Mode principal (production / `.bat` / `.sh`)** : un unique JAR Spring Boot autonome. Le plugin Maven `maven-resources-plugin` copie le contenu de `view/` dans `back/server/src/main/resources/static/` au build ; Spring Boot sert donc lui-même les pages HTML/JS/CSS **et** l'API REST, sur le même port (8080), sous les chemins `/` (statique) et `/api/v1/*` (API, via `server.servlet.context-path`). C'est le mode lancé par `MyFamilyBudget.bat` / `MyFamilyBudget.sh`, qui embarque même une JRE portable.

**Mode dev alternatif (`view/server.js`)** : un petit serveur Express (Bun/Node) qui sert les mêmes fichiers statiques et proxy les requêtes API vers un backend Spring Boot distant (adresse IP en dur, voir `TARGET_SPRINGBOOT`). Utile pour développer le frontend seul, ou pour tester l'app depuis un téléphone sur le même réseau sans reconstruire le JAR. **Ce n'est pas le chemin utilisé en usage normal.**

Dans les deux cas, le frontend est piloté par `window.API_BASE_URL`, défini dans `view/config.js` (actuellement en dur, plusieurs adresses possibles en commentaire selon le réseau).

### 2.2 Schéma du flux de données (mode principal)

```
Navigateur (React, in-browser Babel, pas de build step)
   │
   │  fetch(API_BASE_URL + '/xxx')
   ▼
Spring Boot (JAR unique, port 8080, context-path /api/v1)
   │
   │  interface générée par openapi-generator (ex: PatrimoineApi)
   ▼
*ServiceImpl (ex: PatrimoineServiceImpl) — @RestController
   │
   │  Mapper (ex: PatrimoineMapper) : Dto <-> Model interne
   ▼
PersistenceManager (façade unique de persistance, ~1300 lignes)
   │
   │  EntityModelConverter : Model <-> Entity JPA
   ▼
Repositories Spring Data JPA
   │
   ▼
H2 (fichier local, ./data/myfamilybudget.mv.db)
```

**Point important** : `PersistenceManager` garde aussi une copie en mémoire de l'état courant (`AtomicReference<BudgetDataModel> currentBudget`). La plupart des lectures se font depuis ce cache mémoire, pas depuis une requête SQL à chaque appel — la base de données n'est vraiment sollicitée qu'à l'écriture et au tout premier chargement (`@PostConstruct init()`). C'est une source fréquente de confusion : un bug de persistance peut être invisible tant que le process tourne (le cache mémoire masque le problème) et n'apparaître qu'après un redémarrage.

---

## 3. Frontend (`view/`)

### 3.1 Pas de build step

Le frontend est du React chargé directement dans le navigateur via Babel in-browser (`<script type="text/babel">` implicite dans les fichiers `-view.js`) et Chart.js pour les graphiques. Il n'y a pas de webpack/vite : chaque page HTML charge une liste de scripts dans un ordre précis (voir `<script src="...">` dans n'importe quel `view/*.html`) :

```
React / ReactDOM / Chart.js (CDN)
  → config.js                  (API_BASE_URL, flags)
  → js/tokens.js                (design tokens : couleurs, espacements)
  → js/help-content.js
  → js/models.js
  → js/csv-parser.js
  → js/calculations.js          (fonctions de calcul pur, réutilisées aussi par service-metier.js)
  → js/data-store.js            (BudgetStore : état centralisé + localStorage)
  → js/service-metier.js        (logique métier "locale" : Overview/Tresorerie/Patrimoine/...Service)
  → js/api.js                   (BudgetApi : façade unique appelée par les vues)
  → js/components/*.js          (composants UI réutilisables)
  → js/views/xxx-view.js        (la vue de la page courante)
```

**L'ordre compte** : `service-metier.js` doit être chargé avant `api.js`, qui doit être chargé avant la vue.

### 3.2 `data-store.js` — le store local (localStorage)

`BudgetStore` est un store centralisé très simple (pas de Redux) qui lit/écrit dans `localStorage` (clé `budget_familial_data_v1`) et notifie les composants abonnés. Il gère aussi la synchronisation entre onglets du navigateur via `BroadcastChannel` + l'évènement `storage`. **Ce store existait avant l'introduction du backend Spring Boot** et reste la seule source de données pour les onglets qui n'ont pas encore été migrés (voir §3.4).

### 3.3 `api.js` — le pattern "Strangler Fig"

`BudgetApi` est le point d'entrée unique utilisé par toutes les vues React. C'est une façade conçue pour permettre une migration progressive vers le backend Java sans jamais changer le code des vues. Trois familles de méthodes cohabitent aujourd'hui :

1. **Entièrement locales** (`getImpots`, `getRetraiteData`, `getBankImport`, `getPointage`, `getAnalyse`…) : aucun appel réseau, tout passe par `service-metier.js` → `BudgetStore` → `localStorage`. Ces domaines n'ont pas encore de backend équivalent implémenté côté Java.
2. **Backend avec fallback local silencieux** (`getVueDensemble`, `getTresorerie`, `getPatrimoine`, `getSettings`) : tentative de `fetch` vers le backend ; en cas d'échec (réseau, timeout, HTTP non-2xx), bascule silencieuse vers le service JS local. Ce pattern est factorisé dans le helper `fetchJsonOrFallback()`, qui respecte le flag de diagnostic `window.DISABLE_JS_FALLBACK` (voir §7.1).
3. **Fusion local + backend** (`getPendingOperations`) : lit les deux sources et fusionne les résultats par id. Zone plus fragile, à traiter avec précaution (voir §8).

**Piège pour un agent IA (ou pour toi) qui chercherait "pourquoi telle donnée ne remonte pas"** : il faut d'abord identifier dans laquelle des trois catégories tombe la méthode `BudgetApi` concernée avant de chercher côté Java — pour les méthodes de la famille 1, le bug est presque certainement dans `service-metier.js`, pas dans le backend.

### 3.4 Où se trouve la logique métier "locale" ?

`view/js/service-metier.js` contient les fonctions `buildXxx()` / `updateXxxLigne()` / etc. pour chaque domaine (`OverviewService`, `TresorerieService`, `PatrimoineService`, `ImpotsService`, `SettingsService`, `BankImportService`, `PendingOperationsService`, `PointageService`, `AnalyseService`). C'est la même logique de calcul que celle réimplémentée côté Java (voir §5.4) — c'est volontairement le cas : ce fichier sert d'« oracle » de comportement attendu pendant la migration vers le backend.

---

## 4. Backend (`back/server/`)

### 4.1 Génération de l'API depuis `openapi.yaml`

Le contrat d'API est défini une seule fois, à la racine du dépôt : `openapi.yaml`. Le plugin `openapi-generator-maven-plugin` (configuré dans `back/server/pom.xml`) génère à la compilation :

- des interfaces Java (`com.moe.myfamilybudget.api.controller.*Api`, ex. `PatrimoineApi`), que les `*ServiceImpl` implémentent,
- des DTOs (`com.moe.myfamilybudget.api.model.*Dto`).

**Conséquence pratique** : ces classes générées n'existent pas dans le code source versionné — elles apparaissent seulement après `mvn compile` (généralement dans `target/generated-sources`). Si un IDE affiche des erreurs "classe introuvable" sur `PatrimoineApi` ou consorts, il faut d'abord compiler une fois.

**Pour ajouter ou modifier un endpoint** : toujours commencer par éditer `openapi.yaml`, recompiler, puis adapter le `*ServiceImpl` concerné — jamais l'inverse.

### 4.2 Les 4 couches de représentation d'une donnée

Une même information (un placement, par exemple) existe sous 3 à 4 formes selon la couche :

| Couche | Type | Exemple |
|---|---|---|
| Contrat API (généré) | `PlacementDto` | reçu/envoyé en JSON |
| Modèle métier interne | `PlacementModel` (record Java) | manipulé par `PersistenceManager` et les calculateurs |
| Entité JPA | `PlacementEntity` | mappée sur la table H2 |
| (frontend) | objet JS brut | manipulé dans `service-metier.js` / React |

Les conversions se font via des classes `*Mapper` (Dto ↔ Model) et `EntityModelConverter` (Model ↔ Entity). C'est un peu verbeux, mais ça isole complètement le contrat d'API des détails de persistance — un changement de moteur de base de données (H2 → PostgreSQL, cf. §9) ne devrait toucher **que** `EntityModelConverter` et les `*Entity`/`*Repository`, jamais les Mappers ni les DTOs.

### 4.3 `PersistenceManager` — la façade unique de persistance

Fichier central (~1300 lignes) : toute lecture/écriture de données passe par lui. Points clés :

- **Cache mémoire** : `AtomicReference<BudgetDataModel> currentBudget`. Chargé au démarrage (`@PostConstruct init()`) depuis la base, puis mis à jour en mémoire à chaque écriture. La base n'est relue depuis le disque qu'au redémarrage du process.
- **`saveToDatabase(model)`** : méthode privée appelée par ~20 méthodes publiques différentes (une par type d'écriture : ajout/édition/suppression de ligne, import JSON, reset...). Elle supprime la ligne `budget_data` existante puis réinsère l'état complet (voir §7.2 pour l'historique de cette décision).
- Les collections enfants (incomes, charges, placements, transferts, prêts...) sont gérées "à la main" : suppression par `deleteByBudgetDataId()` puis réinsertion complète à chaque sauvegarde, plutôt que des diffs. Simple et fiable, mais signifie qu'on ne peut pas facilement suivre un historique de modifications au niveau SQL — si un jour un historique fin est nécessaire, il faudra le construire explicitement (table d'audit), pas le déduire de l'existant.

### 4.4 Structure des packages

```
back/server/src/main/java/com/moe/myfamilybudget/
├── config/                    → WebCorsConfig (CORS + routing des ressources statiques)
└── server/internal/
    ├── controller/             → HeartbeatController (le seul contrôleur "manuel", hors génération openapi)
    ├── impl/                   → *ServiceImpl : un par domaine métier, implémente l'interface générée, fait aussi
    │                             office de calculateur (beaucoup de logique de projection vit ici, ex.
    │                             PatrimoineServiceImpl.computePatrimoineProjections())
    ├── mapper/                 → Dto <-> Model
    ├── model/                  → records Java, + certains "Calculator" (logique de calcul pure, ex. TaxCalculator)
    └── persistence/
        ├── PersistenceManager.java   → façade unique
        ├── converter/                → Model <-> Entity
        ├── entity/                   → entités JPA
        └── repository/               → interfaces Spring Data JPA
```

### 4.5 Le pattern "oracle JS" pour les tests d'intégration

Les tests dans `back/server/src/test/java/.../integration/` (ex. `BusinessLogicIntegrationTest`, nommé explicitement *"Oracle JS vs Backend Java"*) chargent un jeu de données de référence (`mock-budget.json`) et vérifient que les réponses de l'API Java correspondent aux valeurs produites par `service-metier.js` / `calculations.js` côté frontend. Le JS fait foi comme définition du comportement attendu — **y compris ses éventuels comportements par défaut non idéaux** : un test qui échoue doit d'abord faire suspecter une valeur de référence incorrecte dans le test avant de suspecter un bug côté Java (c'est déjà arrivé, voir §8).

---

## 5. Comment lancer le projet

**Backend seul (dev)** :
```
cd back/server
mvn spring-boot:run
```
Sert l'API **et** le frontend statique sur `http://localhost:8080` (context-path `/api/v1` pour l'API).

**Frontend seul avec proxy vers un backend distant** :
```
npm run dev   # lance view/server.js sur le port 3000
```
Nécessite d'éditer `TARGET_SPRINGBOOT` dans `view/server.js` avec l'adresse du backend.

**Build production (JAR autonome)** :
```
cd back/server
mvn clean package -DskipTests
```
Produit `target/server-1.0.0-SNAPSHOT.jar`, lancé ensuite par `MyFamilyBudget.bat`/`.sh`.

**Tests** :
- Backend : `mvn test` (unitaires + intégration contre l'oracle JS)
- E2E : `npm run test:e2e` (Playwright, `tests/e2e/`)

---

## 6. Base de données

Actuellement H2 en mode fichier (`./data/myfamilybudget`), avec `ddl-auto: update` (Hibernate modifie le schéma automatiquement à chaque démarrage si le code a changé). Pas de Flyway/Liquibase à ce jour — voir §9 pour la trajectoire de migration vers PostgreSQL, où ce point devra être adressé.

Pour repartir d'une base vide : arrêter le serveur puis supprimer le dossier `back/server/data/`.

---

## 7. Bugs connus corrigés — pourquoi ils existaient

Cette section documente volontairement des bugs déjà réglés : comprendre *pourquoi* ils existaient aide à repérer des symptômes similaires ailleurs dans le code.

### 7.1 Ambiguïté sur l'origine des données affichées (frontend)

**Symptôme observé** : impossible de savoir si une donnée affichée provenait du backend (H2) ou du fallback JS local (`localStorage`), car l'échec d'un `fetch` bascule silencieusement sur le service local sans aucune trace visible.

**Fix** : introduction de `window.DISABLE_JS_FALLBACK` (dans `config.js`) et du helper `fetchJsonOrFallback()` (dans `api.js`). Quand le flag est à `true`, tout échec de `fetch` remonte une erreur explicite en console au lieu de basculer en silence — utile pour un diagnostic ponctuel, à repasser à `false` ensuite (sinon les onglets restent bloqués sur "Chargement…" si le backend n'est pas joignable).

**Portée du fix** : uniquement les 4 méthodes de la catégorie 2 du §3.3 (`getVueDensemble`, `getTresorerie`, `getPatrimoine`, `getSettings`). Les méthodes purement locales (Impôts, Retraite...) ne sont pas concernées — il n'y a rien à "désactiver" pour elles tant qu'aucun backend équivalent n'existe.

### 7.2 Duplication de la ligne `budget_data` à chaque sauvegarde

**Symptôme observé** : après import d'un fichier JSON, les données sont bien visibles dans l'application — mais disparaissent après un redémarrage du serveur.

**Cause racine** : `EntityModelConverter.toEntity(BudgetDataModel model)` construit toujours une entité neuve avec `id = null`. Comme `saveToDatabase()` (appelée par ~20 chemins d'écriture différents : import, édition d'une ligne, etc.) sauvegardait directement cette entité sans réutiliser l'id existant, Hibernate faisait un **INSERT** à chaque sauvegarde au lieu d'un **UPDATE** — créant une nouvelle ligne `budget_data` à chaque fois. Au redémarrage, `@PostConstruct init()` relit via `findFirstByOrderByIdAsc()`, qui renvoie l'id le plus petit — donc la toute première ligne (généralement vide), pas la dernière sauvegardée.

Ce bug touchait en réalité **toutes** les écritures de l'application (pas seulement l'import JSON) — il restait simplement invisible en usage courant tant que le process ne redémarrait pas, grâce au cache mémoire `currentBudget` (voir §4.3).

**Fix** : `saveToDatabase()` supprime désormais la ligne `budget_data` existante (et ses entités enfants, via `cascade = ALL`) avant de réinsérer — même mécanisme que celui déjà utilisé (et fonctionnel) dans `resetData()`.

**Point de vigilance résiduel** : une base H2 qui a subi ce bug avant le correctif contient probablement plusieurs lignes `budget_data` orphelines. Le comportement redevient cohérent dès la première écriture suivant le correctif (qui nettoie tout), mais avant cette première écriture, `findFirstByOrderByIdAsc()` continue de charger l'ancienne première ligne.

---

## 8. Ambiguïtés ouvertes / zones fragiles (au moment de la rédaction)

- **`loans` vs `credits`** : incohérence de nommage entre `service-metier.js` (JS) et `openapi.yaml` (le DTO utilise un nom, le JS local un autre) sur les listes de prêts. Non résolu — vérifier lequel fait foi avant de toucher au code des prêts.
- **`GET /patrimoine/placements/{id}/historique`** : endpoint présent côté OpenAPI/backend, sans équivalent dans le service JS local. Un test de comportement croisé (§4.5) n'est donc pas possible pour cette route.
- **`/pending-operations/reconcile` et `/pending-operations/ignore`** : mapping entre comportement attendu et implémentation pas totalement clarifié — zone à traiter avec prudence, en lien avec la logique de fusion mentionnée au §3.3 point 3.
- **`getPendingOperations` (fusion local + backend)** : seule méthode de `api.js` qui mélange les deux sources plutôt que de choisir l'une ou l'autre. Une régression sur l'affichage des opérations créées manuellement a été observée après un correctif sur cette zone — à investiguer en premier si un nouveau bug apparaît sur l'onglet "Opérations en cours".

---

## 9. Trajectoire d'infrastructure (prévue, pas encore implémentée)

Pour donner le contexte à un futur lecteur (ou agent) qui trouverait des incohérences entre ce document et l'état du code sur ce point précis :

1. **Docker** : conteneurisation du JAR Spring Boot + d'une base PostgreSQL (aucun `Dockerfile`/`docker-compose.yml` dans le dépôt à ce jour).
2. **PostgreSQL en remplacement de H2** : supprime les soucis propres à H2 en mode fichier (verrous, `AUTO_SERVER=TRUE`, paramètres `DB_CLOSE_DELAY`/`DB_CLOSE_ON_EXIT`). Nécessitera d'introduire un outil de migration de schéma (Flyway/Liquibase) plutôt que de garder `ddl-auto: update` une fois la base considérée comme source de vérité de production.
3. **Hébergement sur un mini-PC personnel**, accessible à distance via **Tailscale** (VPN maillé chiffré, pas d'exposition sur l'internet public) — pour permettre la saisie depuis un téléphone au plus près des achats.
4. **Sauvegardes** : stratégie 3-2-1 sans cloud commercial — `pg_dump` planifié en local + réplication vers un second poste via Syncthing (ou tâche planifiée), copie hors-site en dernier recours.

---

## 10. Guide rapide pour un agent IA intervenant sur ce dépôt

- **"Une donnée ne s'affiche pas / semble incohérente"** → identifier d'abord si le domaine concerné est purement local, hybride avec fallback, ou fusionné (§3.3), avant de chercher côté Java.
- **"Ajouter un champ / un endpoint"** → toujours commencer par `openapi.yaml`, recompiler, puis modifier `*ServiceImpl` + `*Mapper` + `*Entity`/`*Repository` si persistant. Ne pas oublier l'équivalent côté `service-metier.js` si le domaine est encore couvert par un test "oracle JS" (§4.5).
- **"Un test d'intégration échoue après un changement de logique de calcul"** → vérifier en premier si la valeur de référence dans le test reflète bien le comportement réel de `service-metier.js`/`calculations.js`, avant de modifier le code Java pour "corriger" un écart.
- **"Une donnée disparaît après un redémarrage du serveur"** → symptôme historique du bug §7.2. Vérifier qu'aucun nouveau chemin d'écriture ne contourne `saveToDatabase()`.
- **Fichiers à ne quasiment jamais modifier sans comprendre leur portée globale** : `PersistenceManager.java` (façade unique, ~20 points d'appel), `api.js` (façade unique frontend), `service-metier.js` (oracle de comportement pour les tests d'intégration Java).