# Release Notes — Silo Analyse (domain-analysis)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Analyse (`domain-analysis`), regroupant `analysis-api` et `analysis-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`analysis-api`** : contrats, modèles, interface de service (aucun changement métier).
- **`analysis-core`** : moteur de calcul pur, sans Spring ni JPA.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
