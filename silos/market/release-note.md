# Release Notes — Silo Marché (domain-market)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-11-01

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Marché (`domain-market`), regroupant `market-api` et `market-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`market-api`** : cotations, fraîcheur, ports, interface de service (aucun changement métier).
- **`market-core`** : clients des sources publiques et agrégation, et persistance JPA de l'instantané de marché.

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
