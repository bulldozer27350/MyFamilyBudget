# Release Notes — Silo Retraite (domain-retirement)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Retraite (`domain-retirement`), regroupant `retirement-api` et `retirement-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`retirement-api`** : contrats, ports, interface de service (moteur de calcul, projections) (aucun changement métier).
- **`retirement-core`** : moteur de calcul et persistance JPA des tables pension_*.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
