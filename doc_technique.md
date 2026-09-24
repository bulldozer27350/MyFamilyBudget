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

`BudgetStore` est un store centralisé très simple (pas de Redux) qui lit/écrit dans `localStorage` (clé `budget_familial_data_v1`) et notifie les composants abonnés. Il gère aussi la synchronisation entre onglets du navigateur via `BroadcastChannel` + l'évènement `storage`. **Ce store existait avant l'introduction du backend Spring Boot** et reste la seule source de données pour les onglets qui n'ont pas encore été migrés (voir §3.4). Les vues qui relisent le back-end (Patrimoine…) sont notifiées par le store dès la saisie locale, donc avant que l'écriture serveur n'ait abouti : `api.js` (`notifyServerSynced`) et `data-store.js` (`notifyRemoteChange`, import, réinitialisation) émettent donc une seconde notification une fois le POST/DELETE accepté, pour qu'elles relisent l'état à jour sans rechargement de page.

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
    ├── enablebanking/          → synchronisation bancaire DSP2 (Enable Banking) : JWT, client HTTP,
    │                             mapping des transactions, service d'orchestration, planificateur (voir §4.6bis)
    ├── mapper/                 → Dto <-> Model
    ├── marketdata/             → aide à la saisie des taux : client des sources publiques (Caisse des Dépôts), instantané
                                persisté et fraîcheur des données (voir §4.6)
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

### 4.6 Données de marché (taux publics, aide à la saisie)

Le package `marketdata` interroge des sources publiques pour **suggérer** des taux (jamais appliqués automatiquement) :

- `CdcRegulatedRatesClient` lit le jeu `flux-et-taux-la-ldds-lep` de la Caisse des Dépôts (Livret A, LDDS, LEP). La source publie des pourcentages, convertis en fractions comme partout dans l'application.
- `MarketDataService` garde le dernier instantané réussi (table `market_snapshot`, une seule ligne). Une source en échec n'écrase jamais l'instantané : l'erreur est exposée dans `lastRefreshError`.
- `RegulatedRateFreshness` marque une donnée `STALE` si elle est antérieure à la dernière révision légale (1er février / 1er août) : le jeu de données est publié avec retard, la valeur affichée peut donc ne plus être en vigueur.
- API : `GET /taux-marche` (sans appel réseau) et `POST /taux-marche/refresh`. Rafraîchissement automatique au démarrage puis toutes les 12 h.
- `EcbYieldCurveClient` lit la courbe des taux des emprunts d'État de la zone euro AAA (API SDMX de la BCE, sans clé) : spot 2 et 10 ans, forwards instantanés 1, 2, 5 et 10 ans. Taux en composition continue : `exp(taux) - 1` donne le taux annuel effectif comparable à un livret.
- `BdfMortgageRateClient` lit le taux moyen des nouveaux crédits à l'habitat hors renégociations (Webstat, série `MIR1.M.FR.B.A22HR.A.5.A.2254U6.EUR.N`). **Clé d'API requise**, fournie par la variable d'environnement `MYFAMILYBUDGET_BDF_API_KEY` (docker-compose : `environment:`) ; sans clé, la source est ignorée sans erreur. La clé n'est jamais journalisée ni exposée par l'API.
- Chaque source est rafraîchie indépendamment : une source en échec conserve sa donnée précédente, ses erreurs sont concaténées dans `lastRefreshError`.
- Configuration : `myfamilybudget.market-data.*` dans `application.yml` (`enabled`, `timeout-seconds`, `refresh.*`, `cdc.*`, `ecb.*`, `bdf.*`). Désactivé dans les tests.

### 4.6bis Synchronisation bancaire automatique (Enable Banking / DSP2)

Le package `enablebanking` récupère automatiquement les transactions bancaires via l'API DSP2 d'[Enable Banking](https://enablebanking.com/) et les importe en réutilisant directement `BankImportCalculator.importTransactions` — le même moteur, la même déduplication (date + libellé + montant), que l'import CSV manuel. Aucun appel HTTP vers la propre API de l'application n'est nécessaire : mapping et persistance se font dans le même processus.

- `EnableBankingConfig` centralise la configuration et détermine une seule fois au démarrage si la synchronisation peut être activée (`isConfigured()`) : `application-id`, un certificat **lisible** au chemin fourni, et au moins un compte déclaré. Absent, la synchronisation est simplement désactivée (log au démarrage), sans empêcher le reste de l'application de fonctionner — même logique que `BdfMortgageRateClient.isConfigured()` pour la Banque de France.
- **Le certificat privé n'est jamais embarqué dans l'image Docker ni commité dans le dépôt** : seul son chemin sur l'hôte est fourni, via un bind mount Docker (`docker-compose.prod.yml`, dossier `secrets/enable-banking/`, versionné vide). La distribution portable (`MyFamilyBudget.bat`, destinée à des tiers) ne reçoit jamais ces variables et reste donc désactivée par défaut, sans risque de fuite.
- `EnableBankingJwtSigner` signe le JWT (RS256) attendu par Enable Banking sans dépendance externe (uniquement `java.security`). Certificat attendu au format PKCS#8 (`-----BEGIN PRIVATE KEY-----`) ; un certificat PKCS#1 doit d'abord être converti (`openssl pkcs8 -topk8 -nocrypt`).
- `EnableBankingClient` (uniquement `java.net.http.HttpClient`, comme `MarketHttp`) récupère soldes et transactions, avec pagination (`continuation_key`). Seules les transactions `status = BOOK` (comptabilisées) sont importées.
- `EnableBankingTransactionMapper` déduit le type d'opération (`VIR SEPA`, `PRLV SEPA`, `CARTE`, ...) par heuristique sur le libellé (`remittance_information`), faute de `bank_transaction_code` exploitable pour toutes les banques — à ajuster selon les libellés réellement observés.
- État de synchronisation (dernière date de transaction importée par compte, recouvrement de sécurité de 3 jours) persisté en base (table `enable_banking_sync_state`), sur le modèle de `market_snapshot`.
- Deux déclencheurs : `EnableBankingSyncScheduler` (`@Scheduled`, tant que l'application tourne — configurable, désactivable dans les tests) et `POST /bank-import/enable-banking/sync` (bouton "Synchroniser mes comptes" de l'écran Import).
- Configuration : `myfamilybudget.enable-banking.*` dans `application.yml` (`application-id`, `private-key-path`, `accounts` au format `libellé|uid;...`, `refresh.*`).

### 4.7 Analyse des prêts (rembourser ? renégocier ?)

`LoanAdviceCalculationService` (package `calculation`, sans état) alimente `GET /analyse/prets?marketRate=0.032` :

- **Remboursement anticipé** : le coût du prêt (taux + assurance rapportée au capital) est comparé au meilleur rendement *net* d'un placement sans risque et liquide (catégories de bucket `cash` et `fondsEuros`, taux « correct », PFU appliqué hors livrets). Les actions, l'immobilier et l'épargne retraite ne sont pas des alternatives : solder un prêt rapporte son taux de façon certaine. Une indemnité (IRA) non amortie avant la fin du prêt ramène le verdict à « neutre ».
- **Renégociation** : uniquement si un taux de marché est connu, retenu dans cet ordre : paramètre `marketRate` (simulation), taux saisi dans les hypothèses, puis taux Banque de France (`marketRateSource` indique l'origine). Écart minimal, capital et durée restants minimaux, puis économie nette estimée à durée identique, après IRA et frais fixes. Les seuils sont des heuristiques, renvoyées dans `assumptions`.
- **Hypothèses modifiables** : `GET`/`PUT /analyse/prets/parametres` (table `loan_advice_settings`, une ligne JSON). `GET` renvoie les valeurs en vigueur et les valeurs par défaut ; `PUT` valide les plages (400 avec message explicite si hors plage). Par défaut : écart de remboursement 0,5 pt, écart de renégociation 0,7 pt, capital minimal 70 000 €, durée minimale 84 mois, frais fixes 1 500 €, PFU 30 %.
- IRA : plafond légal immobilier, le moindre de 6 mois d'intérêts et 3 % du capital restant dû (prêts supposés immobiliers).
- Le capital restant dû est projeté à aujourd'hui exactement comme `projectLoanCrdToDate()` du front.
- Limites (renvoyées dans `notes`) : plafonds de versement, épargne de précaution et offre réelle des banques ne sont pas modélisés.
- **Front** : `view/js/components/loan-advice-panel.js` (chargé par `analyse.html`) affiche ces trois sources dans Analyse › Fiscal & Prêts (bloc Marché, carte Hypothèses, verdicts par prêt). Fonctionnalités serveur uniquement : `api.js` renvoie `null` si le back-end est injoignable et la vue retombe alors sur l'ancienne carte « Prêts en cours » calculée localement (`computeFiscalPatrimonialAdvice`).

### 4.8 Suggestions de taux pour les placements

`PlacementRateSuggestionService` (package `calculation`, sans état) alimente `GET /patrimoine/suggestions-taux?amplitude=0.01` (lecture seule, aucun placement modifié) :

- **Livret A / LDDS / LEP** (repérés par libellé ou nom de catégorie, accents et casse ignorés) : taux « correct » = taux réglementé en vigueur (Caisse des Dépôts) ; pessimiste/optimiste = ± `amplitude` (défaut 1 pt, de 0 à 5 pt), le pessimiste étant plafonné par le bas à 0,5 % (minimum légal). L'amplitude est une **convention**, pas une prévision : les notes de la réponse le rappellent. Une donnée antérieure à la dernière révision est suggérée avec une réserve (`caveat`).
- **Fonds en euros, obligations** : `kind = REFERENCE`, taux à 10 ans zone euro AAA (courbe BCE) converti en taux annuel effectif, sans scénario.
- **Autres** (actions, immobilier, épargne salariale, comptes non réglementés, catégorie inconnue) : `kind = NONE`, avec l'explication.
- `AssetBucketResolver` retrouve la classe d'actif d'un placement (par id de catégorie puis par nom) ; il est partagé avec l'analyse des prêts.
- **Front** : `view/js/components/rate-suggestion.js` (chargé par `patrimoine.html`) affiche la suggestion dans la fiche d'un placement, sous « 3. Hypothèses de rendement annuel ». « Appliquer » écrit les trois taux séquentiellement via `handleCellChange` (qui renvoie désormais la promesse de mise à jour). L'amplitude choisie est mémorisée dans le navigateur (`localStorage`, clé `mfb.rateSuggestion.amplitudePt`). Back-end injoignable : aucun bloc n'est affiché.
- Ce qui n'est **pas** fait : scénarios par année future et projection des taux réglementés (il manque l'inflation prévue).

### 4.9 Tableau d'amortissement d'un prêt

- **Données** : trois champs optionnels sur le prêt (`initialAmount`, `totalInstallments`, `stepDate`), persistés comme les autres (`LoanModel`, `LoanEntity`, `LoanDto`). Le constructeur à 8 arguments de `LoanModel` est conservé. Les colonnes sont ajoutées par `ddl-auto: update` (nullables) : aucune migration SQL.
- **Moteur** : `view/js/amortization.js` (fonctions pures, testées par `node view/scratch/test-amortization.js`). Mode *complet* (capital + nombre d'échéances + date de fin : le tableau part de l'échéance 1, les dates se déduisent de la date de fin et du nombre d'échéances) ou *restant* (CRD + date du relevé, mois du relevé inclus, comme `projectLoanCrdToDate`). Intérêts du mois = CRD × taux / 12 arrondis au centime, assurance constante, dernière échéance ajustée.
- **Mensualité lissée** : `stepDate` est la dernière échéance à la mensualité actuelle ; ensuite la mensualité est recalculée (annuité sur le solde et les échéances restantes). `estimateStep` cherche la date pour laquelle la nouvelle mensualité vaut l'ancienne + celle du prêt lissé (un lissage garde la somme des mensualités constante).
- **Contrôle** : le CRD du relevé est comparé au CRD théorique du mois du relevé (avant ou après échéance). S'il est incohérent, `referenceCheck.message` l'explique une seule fois ; si le CRD saisi correspond à une autre échéance du tableau (à ±12 mois), `referenceCheck.shift` signale un décalage de rang, de date de fin ou de date de relevé.
- **Recalage sur le relevé** : en mode complet, si le CRD du relevé s'écarte légèrement (quelques euros, ≤ 0,2 % du capital) du CRD théorique, l'écart est imputé aux intérêts de la 1re échéance (`calibrateOnReference`, mensualité inchangée : la 1re période dure souvent plus d'un mois). Le tableau passe alors exactement par le CRD du relevé et la dernière échéance (reliquat) en découle. Un écart plus grand n'est jamais absorbé : c'est une erreur de saisie, signalée par `referenceCheck`.
- **Remboursement anticipé partiel** : `simulateEarlyRepayment` applique le montant à la 1re échéance dont la date est ≥ à la date choisie, après le paiement de celle-ci. Mode « durée » (mensualité conservée, prêt raccourci) ou « mensualité » (durée conservée, mensualité recalculée). Comparaison avec le tableau sans remboursement : intérêts et assurance économisés (assurance supposée constante), indemnité estimée au moindre de 6 mois d'intérêts et 3 % du montant remboursé (modifiable), gain net. La simulation repart du tableau recalé et n'est jamais enregistrée ; elle s'exporte en PDF avec un bandeau « Simulation ».
- **Export CSV** : `buildAmortizationCSV` (`amortization-report.js`) produit un CSV pour Excel français : UTF-8 avec BOM, CRLF, séparateur `;`, virgule décimale, dates JJ/MM/AAAA, colonne « Remboursement anticipé ». Il s'applique aussi au tableau simulé (`-simulation` dans le nom du fichier).
- **Front** : `components/loans-panel.js` (cartes + tiroir, remplace le tableau éditable ; repli sur celui-ci si le fichier n'est pas chargé) et `components/amortization-report.js` (impression navigateur, comme le bilan patrimonial), chargés par `patrimoine.html`.
- **Limites connues** : taux fixe uniquement (pas de taux variable, différé, remboursement anticipé) ; le CRD projeté, la trajectoire patrimoniale et l'analyse des prêts connaissent le palier de mensualité lissée : après sa fin, la mensualité est recalculée pour solder le prêt à sa date de fin (`paymentAfterStep` dans `calculations.js`, même logique dans `LoanAdviceCalculationService#projectCrd` et `#simulate`, durée et intérêts restants compris).

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

**Précision des taux.** Les taux sont stockés en fractions (`0,0251` pour 2,51 %) dans des colonnes `NUMERIC(19,8)` (`@Column(precision = 19, scale = 8)` sur chaque champ de taux des entités). Sans cette annotation, Hibernate crée `NUMERIC(38,2)` et arrondit `0,0251` à `0,03` : un taux de prêt de 2,5 % revenait 3 % après rechargement. `ddl-auto: update` ne modifie jamais une colonne existante : sur une base déjà créée, exécuter une fois `tools/sql/0012-precision-taux.sql` (idempotent, compatible PostgreSQL et H2), puis re-saisir les taux déjà arrondis.

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