# Release Notes — Silo Objectifs (domain-goals)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Objectifs (`domain-goals`), regroupant `goals-api` et `goals-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`goals-api`** : modèles, ports, paramètres (aucun changement métier).
- **`goals-core`** : persistance JPA des tables goal, goal_allocation et objectifs_settings.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
