# Release Notes — Silo Paramètres (domain-settings)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Paramètres (`domain-settings`), regroupant `settings-api` et `settings-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`settings-api`** : simulation, hypothèses économiques (aucun changement métier).
- **`settings-core`** : persistance JPA de la table app_settings.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
