# Release Notes — Silo Trésorerie (domain-treasury)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Trésorerie (`domain-treasury`), regroupant `treasury-api` et `treasury-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`treasury-api`** : contrats, ports, interfaces de service (calcul de trésorerie) (aucun changement métier).
- **`treasury-core`** : moteur de calcul pur, et persistance JPA des revenus, charges, ponctuels, virements et variables.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
