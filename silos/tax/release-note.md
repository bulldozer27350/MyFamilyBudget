# Release Notes — Silo Fiscalité (domain-tax)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Fiscalité (`domain-tax`), regroupant `tax-api` et `tax-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`tax-api`** : contrats, ports, interface de service (calcul fiscal, projections) (aucun changement métier).
- **`tax-core`** : moteur de calcul et persistance JPA des tables fiscal_*.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
