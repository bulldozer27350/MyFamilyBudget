# MyFamilyBudget — Audit backend Java & plans de mitigation

Audit réalisé sur le dépôt `bulldozer27350/MyFamilyBudget` (branche `main`), focalisé sur le
module `back/server`. Classement par criticité : risques de bug (du plus au moins impactant),
puis maintenabilité (lisibilité, évolutivité), puis sécurité/configuration.

Statut des patchs livrés : **0001** (point 1), **0003** (point 3), **0004** (point 2), **0005**
(complément point 5 + incrément 1 du point 6), **0006** (incrément 2 du point 6), **0007**
(incrément 3 du point 6, clôture) et **0008** (point 10) sont fournis et validés
(`git apply --check` sur clone frais, chaque patch dépend des précédents). Les autres points sont
documentés avec un plan mais pas encore patchés.

---

## 🔴 Risques de bug — critiques

### 1. Désynchronisation cache mémoire / base en cas d'exception — ✅ patché (0001)

**Problème.** Dans `PersistenceManager`, chaque méthode de mutation faisait :
```java
BudgetDataModel updated = currentBudget.updateAndGet(current -> ...); // mémoire modifiée tout de suite
saveToDatabase(updated); // persistance séparée, peut échouer
```
Si `saveToDatabase` levait une exception, le `@Transactional` de classe annulait la transaction
SQL, mais la mémoire (`AtomicReference<BudgetDataModel>`) restait sur le nouvel état jamais
committé en base — désynchronisation durable jusqu'au redémarrage.

**Mitigation retenue.** Nouvelle méthode privée unique `applyAndPersist(UnaryOperator<BudgetDataModel>)`
qui persiste **avant** d'écrire en mémoire :
1. lit l'état courant (ou crée le budget par défaut) ;
2. calcule le nouvel état via la fonction de mutation fournie ;
3. appelle `saveToDatabase(...)` — si ça échoue, l'exception remonte, rien n'a été modifié en mémoire ;
4. seulement alors, écrit le nouvel état dans `currentBudget`.

Un verrou (`synchronized` sur un `Object` dédié) protège en plus toute la séquence contre les
pertes de mise à jour en cas de requêtes concurrentes (mitigation du point 5, incluse ici car
elle se branche naturellement sur le même point d'entrée).

Toutes les méthodes publiques de mutation (`updateTresorerieRow`, `removeTresorerieRow`,
`updateRetirement`, `updateTaxConfig`, etc. — 16 sites au total) ont été migrées vers ce nouveau
point d'entrée unique, sans changement de leur API publique ni de leur comportement fonctionnel.

**Fichier livré.** `0001-fix-cache-db-desync-applyAndPersist.patch`

---

### 2. Absence de gestion d'erreurs centralisée + `catch (Exception e) { return null; }` — ✅ patché (0004)

**Problème.** 25 blocs `catch (Exception)` dans le code, sans `@ControllerAdvice` global. Une
partie avale silencieusement l'erreur (`BankImportCalculator`, `TaxCalculator`,
`PointageCalculator`, `RetraiteServiceImpl`, `OverviewServiceImpl`, `PatrimoineServiceImpl`,
`TresorerieServiceImpl`...) : un champ mal formé devient un `null` propagé sans log dans les
calculs financiers, sans qu'aucune erreur ne remonte.

**Mitigation retenue.**
- **Étape 1 — nouveau package `server.internal.error`** :
  - `GlobalExceptionHandler` (`@RestControllerAdvice`), point d'entrée unique de gestion des
    erreurs pour tous les `@RestController` de l'application. Remplace la Whitelabel Error Page
    Spring par un corps JSON homogène (`ApiErrorResponse` : status, error, message, path,
    timestamp), journalise systématiquement (WARN pour les erreurs de requête, ERROR avec stack
    trace pour l'inattendu), et distingue désormais 400 (requête invalide) de 500 (erreur
    inattendue).
  - Mappe notamment `UnknownTresorerieFieldException` (introduite au patch 0003) vers un vrai 400
    Bad Request explicite — elle ne remontait auparavant qu'en 500 générique faute de handler.
  - `DataParsingException`, exception métier dédiée pour les cas où une donnée mal formée ne peut
    pas être silencieusement convertie en valeur par défaut sans risque pour un calcul financier
    ou un import de données.
- **Étape 2 — fichier par fichier**, remplacement des `catch (Exception e) { return null; }` (ou
  équivalent : valeur par défaut, `// ignore`) par une journalisation explicite (`log.warn`) juste
  avant le retour de la valeur par défaut documentée, sur les 24 sites concernés répartis dans
  `BankImportCalculator`, `PointageCalculator`, `TaxCalculator`, `FieldValueConverter`,
  `PersistenceManager`, `OverviewServiceImpl`, `RetraiteServiceImpl`, `PatrimoineServiceImpl`,
  `TresorerieServiceImpl`, `RetraiteMapper`, `TaxMapper`, `StatementBankImportMapper` et
  `PatrimoineMapper`. Le comportement fonctionnel (valeurs retournées) est inchangé partout où la
  valeur par défaut restait acceptable — seule la visibilité change : un champ mal formé laisse
  désormais une trace exploitable dans les logs au lieu de disparaître silencieusement.
- **Cas remonté en erreur explicite plutôt que loggé** : `StatementBankImportServiceImpl
  .importBankCSV` avalait une erreur de lecture du fichier CSV importé et poursuivait avec un texte
  vide, aboutissant à un faux succès (`200 OK`, 0 transaction importée, sans qu'aucun signal
  n'indique à l'utilisateur que son fichier n'a pas été traité). Le fichier envoyé ne pouvant pas
  être relu une seconde fois, la valeur par défaut ("continuer avec un texte vide") n'a plus de
  sens : l'erreur est désormais journalisée puis remontée via `DataParsingException`, traduite par
  le `GlobalExceptionHandler` en `400 Bad Request` explicite.

**Fichier livré.** `0004-error-handling-centralisee.patch` (dépend de 0001 et 0003).

---

### 3. Dispatch par chaînes de caractères dans `updateTresorerieRow` — ✅ patché (0003)

**Problème.** Une méthode unique reconstruisait chaque enregistrement (`incomes`, `charges`,
`oneoff`, `variableIncomes`, `variableOverrides`, `placements`) via une comparaison
`"champ".equals(field)` répétée pour chaque propriété × chaque type (~50 branches dupliquées).
Un `listKey`/`field` inconnu était silencieusement ignoré (aucune erreur, aucun log).

**Mitigation retenue.** Nouveau package `server.internal.updater`, développé isolément puis
branché en un seul point :
- `RecordFieldUpdater<T>` : contrat `Optional<T> update(record, field, value)` — `empty()` si
  champ inconnu, remplaçant le no-op silencieux par un signal explicite.
- `MapBackedFieldUpdater<T>` : implémentation générique portée par une `Map<String, BiFunction>`
  au lieu d'une chaîne de ternaires.
- `FieldValueConverter` : conversions `toBigDecimal`/`toInteger`/`toStringOrEmpty`/`toBoolean`
  extraites de `PersistenceManager` (réutilisables sans instance).
- Un registre par modèle : `IncomeFieldUpdaters`, `ChargeFieldUpdaters`, `OneOffFieldUpdaters`,
  `VariableIncomeFieldUpdaters`, `VariableOverrideFieldUpdaters`, `PlacementFieldUpdaters`.
- `TresorerieFieldUpdateDispatcher` : point d'entrée unique, remplace le if/else de `listKey`.
- `UnknownTresorerieFieldException` : levée sur `listKey`/`field` inconnu.

**⚠️ Bug additionnel découvert et corrigé au passage.** L'ancien code appelait, pour
`"placements"`, le constructeur de compatibilité à 18 arguments de `PlacementModel` (celui sans
le paramètre `history`), qui fixe silencieusement l'historique de valorisation à `List.of()`.
**Conséquence : modifier n'importe quel champ d'un placement via la grille de trésorerie
effaçait son historique de valorisation (`PlacementHistoryEntryModel`).** `PlacementFieldUpdaters`
utilise désormais systématiquement le constructeur complet à 19 arguments et transmet
`r.history()` explicitement, donc préservé sur toute mise à jour de champ.

**Fichier livré.** `0003-tresorerie-field-updaters.patch` (dépend de 0001 — à appliquer après).

---

### 4. `ddl-auto: update` en production, sans Flyway/Liquibase

**Problème.** Le schéma de la base de prod (profil `docker`, PostgreSQL) est géré par
l'auto-évolution Hibernate — pas de migrations versionnées, pas de rollback possible.

**Plan de mitigation.**
1. Ajouter la dépendance Flyway, générer une migration `V1__baseline.sql` à partir du schéma
   actuel (`flyway baseline` ou export du schéma existant).
2. Faire tourner Flyway avec `baseline-on-migrate: true` en environnement de test, en gardant
   `ddl-auto: validate` (vérifie la cohérence entité/schéma sans le modifier) — détecte déjà les
   dérives sans risque.
3. Une fois validé, passer `ddl-auto: none` en profil `docker` ; toute évolution future du
   schéma passe par une migration Flyway versionnée dans le repo.

*Non patché — nécessite une décision sur le calendrier de bascule (voir échange à venir).*

---

### 5. Course en lecture-modification-écriture malgré `AtomicReference` — ✅ patché (0001 + complément)

**Statut : mitigé en deux temps.** Le verrou `synchronized (mutationLock)` ajouté dans
`applyAndPersist` (patch 0001) protège les 16 méthodes de mutation qui passent par ce point
d'entrée unique. Une vérification a cependant révélé que trois autres méthodes écrivaient
directement dans `currentBudget` **sans passer par ce verrou**, en contradiction avec le contrat
documenté dans la javadoc d'`applyAndPersist` elle-même (*« seule cette méthode a le droit
d'écrire dans currentBudget »*) :

- `getBudgetData()` : pattern lazy-init (si `currentBudget` est `null`, recharge depuis la base ou
  crée un budget par défaut, sauvegarde, puis affecte) — deux appels concurrents tombant tous les
  deux sur `currentBudget == null` pouvaient chacun créer et persister un budget par défaut.
- `setBudgetData(...)` (import JSON complet) : sauvegarde puis affecte sans verrou, pouvant entrer
  en course avec une mutation `applyAndPersist` concurrente ayant lu un état désormais périmé.
- `resetData()` : `deleteAll()` + sauvegarde + affectation, également hors verrou.

**Complément livré (patch 0004, en même temps que le point 2 dont il partage le fichier).**
`getBudgetData()` utilise désormais un double-checked locking sur `mutationLock` : le chemin
rapide (budget déjà chargé — de très loin le cas le plus fréquent, `currentBudget` n'étant plus
jamais `null` après le démarrage) reste hors verrou pour ne pas pénaliser un getter appelé en
permanence ; seul le chemin lent (premier chargement, ou juste après un `resetData()`) prend le
verrou. `setBudgetData(...)` et `resetData()`, appelées rarement (import complet, réinitialisation
administrative), sont désormais intégralement synchronisées sur le même `mutationLock`.

Aucune action supplémentaire nécessaire tant que l'application reste mono-instance.

---

## 🟠 Maintenabilité

### 6. `PersistenceManager` God Class (1512 lignes) — ✅ patché (incréments 1, 2 et 3/3 : 0005, 0006, 0007)

**Plan.** Strangler Fig appliqué au backend, trois composants cibles :
- `BudgetPersistenceGateway` (delete/save des entités JPA) — **fait**
- `BudgetCacheStore` (gestion de l'`AtomicReference` / `applyAndPersist`) — **fait**
- `BudgetMutationService` (logique des `updateXxx`/`removeXxx`, dont le dispatcher du point 3) — **fait**

`PersistenceManager` est désormais une pure façade déléguant aux trois, API publique inchangée pour
les appelants. Migration composant par composant, validée par les tests existants.

*Note : le patch 0003 avait déjà commencé cette extraction pour la logique de mutation par champ
(`server.internal.updater`), ce qui a facilité ce chantier plus large.*

**Incrément 1 livré (patch 0005).** `BudgetPersistenceGateway` extrait : concentre tout l'accès
direct aux 16 repositories Spring Data utilisés (`save`, `loadExistingIfPresent`, et les 15
méthodes `saveXxx`/`loadBankImport`/`saveBankImport` par collection). Volontairement une classe
simple (pas un bean Spring), instanciée directement dans les deux constructeurs de
`PersistenceManager` — aucun changement pour les appelants ni pour les tests existants
(`new PersistenceManager()` fonctionne à l'identique).

En passant, déduplication d'un bloc de reconstruction du modèle complet depuis les entités JPA qui
était dupliqué entre `init()` et le chemin lent de `getBudgetData()` (patch du point 5) : les deux
appellent désormais `gateway.loadExistingIfPresent()`.

**Incrément 2 livré (patch 0006).** `BudgetCacheStore` extrait : concentre le cache mémoire
(`currentBudget`, `mutationLock`), le point d'entrée unique de mutation (`applyAndPersist`),
`init`, `getBudgetData`, `setBudgetData`, `resetData` et `createDefaultBudgetData`. Dépend de
`BudgetPersistenceGateway` (incrément 1) pour tout accès base — deux méthodes ajoutées à la
gateway à cette occasion (`hasDatabase()`, `deleteAll()`) pour que `BudgetCacheStore` n'ait plus
jamais besoin de référencer un repository JPA directement.

**Incrément 3 livré (patch 0007).** `BudgetMutationService` extrait : concentre toute la logique
métier des mutations (dispatcher du point 3, sections trésorerie/patrimoine, retraite, fiscalité,
catégories d'actifs, historique de placement, import bancaire — une cinquantaine de méthodes).
Dépend de `BudgetCacheStore` (incrément 2) pour `applyAndPersist`/`getBudgetData`/
`createDefaultBudgetData`. `PersistenceManager` devient une pure façade : chaque méthode publique
ne fait plus que déléguer à la méthode de même nom dans `mutationService` ; la méthode privée
`applyAndPersist` conservée à l'incrément 2 comme simple relais a été supprimée, tous ses
appelants ayant migré vers `BudgetMutationService`, qui appelle directement
`cacheStore.applyAndPersist`.

**Résultat final.** `PersistenceManager` : 1449 → 314 lignes (**-78 %**). Répartition sur les
quatre fichiers du package `server.internal.persistence` : `BudgetPersistenceGateway` (377
lignes), `BudgetCacheStore` (292 lignes), `BudgetMutationService` (865 lignes), `PersistenceManager`
(314 lignes, pure façade). Comme les deux précédents, `BudgetMutationService` n'est volontairement
pas un bean Spring. API publique et points d'entrée de test (`new PersistenceManager()`)
strictement inchangés sur les trois incréments — aucune modification de test nécessaire.

### 7. `deleteAll()` + réinsertion complète à chaque sauvegarde

**Plan.** Stratégie diff-based développée en parallèle et activable par un flag :
- Pour chaque collection, comparer les ids en base vs la nouvelle liste → sous-listes à insérer /
  mettre à jour / supprimer, au lieu du delete+reinsert global.
- Implémentation alternative (`IncrementalBudgetPersistenceGateway`) activable via
  `myfamilybudget.persistence.strategy=incremental|full`, testable en dev avant bascule en prod.

### 8. Fusion controller / logique métier (`*ServiceImpl` = `@RestController` + calculs)

**Plan.** Extraction progressive, un `*ServiceImpl` à la fois (en commençant par
`OverviewServiceImpl`, `TresorerieServiceImpl`) : les méthodes de calcul pur migrent vers une
classe `XxxCalculationService` sans dépendance Spring Web, testable indépendamment. La classe
`@RestController` ne garde que l'orchestration HTTP. Déplacement de code, pas de réécriture
fonctionnelle.

### 9. Duplication backend/frontend sur l'Analyse

**Plan.** Migrer les 4 onglets un par un pour consommer `apiData.kpis/landingData/driftRows/
monthlyCompareData`, calcul JS existant conservé comme *fallback* explicite (cohérent avec
`fetchJsonOrFallback()`). Ajouter un test de non-régression comparant sortie JS vs sortie serveur
sur un même jeu de données avant de couper le calcul JS en production.

### 10. Constructeur "de test" à champs `null` dans `PersistenceManager` — ✅ patché (0008)

**Problème.** `PersistenceManager` exposait un second constructeur sans argument (« Default
constructor for testing compatibility ») qui affectait `null` à ses 18 champs (repositories +
`transactionTemplate`), puis construisait `gateway`/`cacheStore`/`mutationService` avec ces
`null`. Cela obligeait `BudgetPersistenceGateway` à truffer son code de production de seize gardes
`if (xxxRepository == null) return;` (une par repository, plus `hasDatabase()`), uniquement pour
survivre à ce mode de test — un couplage test → prod qui obscurcit la lecture du chemin nominal et
aurait laissé passer silencieusement un vrai repository `null` en cas d'erreur de câblage Spring.

**Mitigation retenue.**
- Suppression pure et simple du constructeur sans argument : `PersistenceManager` n'a plus qu'un
  seul point de construction, le constructeur `@Autowired` existant.
- `BudgetPersistenceGateway` : suppression de `hasDatabase()` et des seize gardes
  `if (repository == null) return;` (`deleteAll`, `loadExistingIfPresent`, `save`, et les treize
  méthodes `saveXxx` par collection). Les deux gardes restantes (`budgetDataId == null` dans
  `loadBankImport`, `budgetData == null` dans `saveBankImport`) sont conservées : elles protègent
  un cas métier réel (entité pas encore persistée), pas une carence de test.
- `BudgetCacheStore.init()` : suppression du branchement sur `gateway.hasDatabase()`, devenu
  systématiquement vrai — l'appel à `gateway.loadExistingIfPresent()` (dans sa transaction
  programmatique) redevient la seule voie, identique au comportement précédent pour tout
  déploiement réel.
- **Nouveau** `server.internal.testsupport.PersistenceManagerTestFactory` (test uniquement) :
  fabrique un `PersistenceManager` avec les seize repositories mockés par Mockito plutôt que
  `null`, injectés dans le même constructeur `@Autowired` que la production. Seul
  `budgetDataRepository.save(...)` est stubé pour renvoyer l'entité reçue (évite une
  `NullPointerException` en cascade dans les `saveXxx`) ; les autres repositories utilisent le
  comportement par défaut de Mockito (no-op / `Optional.empty()`), suffisant puisque
  `BudgetCacheStore` ne relit jamais la base après une écriture. Les 13 fichiers de test qui
  appelaient `new PersistenceManager()` appellent désormais
  `PersistenceManagerTestFactory.inMemory()` — comportement observable inchangé, y compris pour
  ceux qui enchaînent avec `persistenceManager.init()`.

**Fichier livré.** `0008-remove-persistencemanager-test-constructor.patch` (dépend de 0001, 0003,
0005, 0006 et 0007 — modifie les mêmes fichiers que ces incréments du point 6).

---

## 🟡 Sécurité / configuration

### 11. Logging des payloads de requêtes en `DEBUG`

**Plan.** Différencier par profil : garder `DEBUG` en dev, mais en profil `docker`
(`application-docker.yml`) surcharger `logging.level.org.springframework.web: WARN` et
désactiver `setIncludePayload` (ou conditionner `CommonsRequestLoggingFilter` à `@Profile("!docker")`).

### 12. Identifiants de base de données par défaut en dur

**Plan.** Supprimer la valeur de repli (`${SPRING_DATASOURCE_PASSWORD}` sans défaut) → échec au
démarrage si la variable n'est pas positionnée (fail-fast plutôt que credentials faibles
silencieux). Documenter la variable requise dans `installation mini-serveur.md`.

### 13. CORS ouvert à `*`

**Plan.** Remplacer `"*"` par une liste explicite lue depuis
`myfamilybudget.cors.allowed-origins`, avec une valeur par défaut couvrant l'usage Tailscale
actuel. Modifiable sans recompilation si l'infra change.

---

## Ordre de traitement suggéré

1. ~~Point 1 (désync cache/DB)~~ — patché
2. ~~Point 3 (dispatch par chaînes)~~ — patché, bug historique sur l'historique des placements corrigé au passage
3. ~~Point 2 (gestion d'erreurs centralisée)~~ — patché, bénéficie directement du dispatcher du point 3 (`UnknownTresorerieFieldException` remonte désormais en 400 Bad Request explicite via le `GlobalExceptionHandler`)
4. ~~Point 5 (course lecture-modification-écriture)~~ — patché ; le patch 0001 ne couvrait que 16 des 19 méthodes écrivant dans `currentBudget`, les 3 restantes (`getBudgetData`, `setBudgetData`, `resetData`) ont été synchronisées sur le même verrou
5. ~~Point 6 (`PersistenceManager` God Class)~~ — patché en 3 incréments (`BudgetPersistenceGateway`, `BudgetCacheStore`, `BudgetMutationService`) ; `PersistenceManager` passe de 1449 à 314 lignes (-78 %)
6. Points 7 à 10 (maintenabilité) — à planifier selon disponibilité
7. Points 4, 11, 12, 13 (sécurité/config) — rapides à traiter indépendamment, à caser entre deux chantiers plus lourds
