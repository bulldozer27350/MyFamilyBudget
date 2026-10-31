# Release Notes — MyFamilyBudget

Toutes les versions publiées de MyFamilyBudget et de ses silos respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

> **Règle impérative** : Toute publication d'une nouvelle version (Majeure, Mineure ou Patch) **DOIT obligatoirement** comporter une entrée détaillée dans ce document (ou dans le `release-note.md` du dépôt autonome concerné) décrivant les impacts par composant et la garantie de rétro-compatibilité binaire.

---

## [1.0.0] - 2026-10-31

### Type de release
**MAJEURE** (Socle initial siloté, architecture modulaire et extraction du composition root)

### Résumé
Stabilisation du monolithe modulaire MyFamilyBudget : élimination de l'ancien module `server`, promotion de `bootstrap` comme point d'entrée exécutable et d'intégration, introduction du `myfamilybudget-bom` et cadrage de la séparation multi-dépôts des silos métier avec versionnement sémantique (SemVer) strict.

### Changements par composant
- **Architecture globale & lanceurs** :
  - `MyFamilyBudget.bat` et `MyFamilyBudget.sh` pointent vers `back/bootstrap/target/bootstrap-1.0.0-SNAPSHOT.jar` (construction ciblée `-pl bootstrap -am`).
  - `tests/e2e/setup.js` utilise `back/bootstrap` pour le démarrage du serveur de tests E2E.
- **Composition Root & Tests (`bootstrap`)** :
  - Rapatriement de l'ensemble des 150 fichiers de tests d'intégration, des suites ArchUnit et des configurations de test depuis l'ancien module `server` vers `back/bootstrap/src/test`.
  - Suppression définitive du module `server` du reactor Maven.
- **BOM centralisé (`myfamilybudget-bom`)** :
  - Création du module `myfamilybudget-bom` centralisant `spring-boot-dependencies:3.4.2`, les bibliothèques tierces (Jackson nullable, Swagger, ArchUnit, POI, Web Push, BouncyCastle, HttpClient) et les versions des modules `com.moe.myfamilybudget:*`.
  - Permet aux futurs dépôts autonomes de construire et tester leurs silos sans dépendre d'un parent relatif.
- **Silos métier (`*-api` et `*-core`)** :
  - Contrats d'interfaces de service purs JDK dans les modules `*-api`.
  - Cœurs de calculs et persistances JPA autonomes dans les modules `*-core`.
- **Intégration (`application` & `web`)** :
  - Conformité JPMS stricte : `module-info.java` explicite pour `api`, export ciblé des modèles DTO.
  - Dépendance exclusive d'`application` envers les `*-api` (interdiction stricte de dépendre de `*-core`).

### Rétro-compatibilité & Compatibilité binaire
- Version de référence initiale 1.0.0 du nouveau découpage.
- Rétro-compatibilité binaire garantie pour toutes les futures versions mineures `1.x.0` et patches `1.x.y` selon la plage `[1.0.0, 2.0.0)`.
