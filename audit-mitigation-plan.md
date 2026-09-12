# MyFamilyBudget — Audit backend Java & plans de mitigation

Audit réalisé sur le dépôt `bulldozer27350/MyFamilyBudget` (branche `main`), focalisé sur le
module `back/server`. Classement par criticité : risques de bug (du plus au moins impactant),
puis maintenabilité (lisibilité, évolutivité), puis sécurité/configuration.

Statut des patchs livrés : **0001** (point 1) et **0003** (point 3) sont fournis et validés
(`git apply --check` sur clone frais). Les autres points sont documentés avec un plan mais pas
encore patchés.

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

### 2. Absence de gestion d'erreurs centralisée + `catch (Exception e) { return null; }`

**Problème.** 25 blocs `catch (Exception)` dans le code, sans `@ControllerAdvice` global. Une
partie avale silencieusement l'erreur (`BankImportCalculator`, `TaxCalculator`,
`PointageCalculator`, `RetraiteServiceImpl`, `OverviewServiceImpl`, `PatrimoineServiceImpl`,
`TresorerieServiceImpl`...) : un champ mal formé devient un `null` propagé sans log dans les
calculs financiers, sans qu'aucune erreur ne remonte.

**Plan de mitigation.**
- **Étape 1 (additive, sans risque)** : ajouter un `@RestControllerAdvice` global qui capture
  les exceptions non gérées et renvoie un format d'erreur JSON cohérent (code, message,
  timestamp) au lieu de la page d'erreur Spring par défaut.
- **Étape 2 (fichier par fichier)** : remplacer les `catch (Exception e) { return null; }` par
  une exception métier dédiée (`DataParsingException`), *loggée* avant conversion en valeur par
  défaut documentée, ou remontée si elle impacte un calcul financier critique. Prioriser les
  calculateurs qui alimentent des totaux (`TaxCalculator`, `BankImportCalculator`) avant le
  formatage d'affichage.

*Non patché à ce stade — prochain candidat naturel après 0001/0003.*

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

### 5. Course en lecture-modification-écriture malgré `AtomicReference`

**Statut : mitigé en même temps que le point 1.** Le verrou `synchronized (mutationLock)` ajouté
dans `applyAndPersist` (patch 0001) protège désormais toute la séquence lecture → mutation →
écriture contre les pertes de mise à jour en cas de requêtes concurrentes. Aucune action
supplémentaire nécessaire tant que l'application reste mono-instance.

---

## 🟠 Maintenabilité

### 6. `PersistenceManager` God Class (1512 lignes)

**Plan.** Strangler Fig appliqué au backend :
- `BudgetCacheStore` (gestion de l'`AtomicReference` / `applyAndPersist`)
- `BudgetPersistenceGateway` (delete/save des entités JPA)
- `BudgetMutationService` (logique des `updateXxx`/`removeXxx`, dont le dispatcher du point 3)

`PersistenceManager` devient une façade fine déléguant aux trois, API publique inchangée pour les
appelants. Migration composant par composant, validée par les tests existants.

*Note : le patch 0003 a déjà commencé cette extraction pour la logique de mutation par champ
(`server.internal.updater`), ce qui facilite ce chantier plus large.*

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

### 10. Constructeur "de test" à champs `null` dans `PersistenceManager`

**Plan.** Rendre le constructeur sans argument `private`/le supprimer ; migrer les tests
concernés vers des mocks Mockito (`@Mock BudgetDataRepository`, etc.) injectés dans le
constructeur `@Autowired` existant. Élimine les `if (repository == null) return;` disséminés
dans le code de prod.

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
3. Point 2 (gestion d'erreurs centralisée) — bénéficie directement du dispatcher du point 3 (`UnknownTresorerieFieldException` a besoin d'un `@ControllerAdvice` pour devenir un vrai 400 côté client)
4. Point 5 — déjà couvert par le patch 0001
5. Points 6 à 10 (maintenabilité) — à planifier selon disponibilité
6. Points 4, 11, 12, 13 (sécurité/config) — rapides à traiter indépendamment, à caser entre deux chantiers plus lourds
