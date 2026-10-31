# Release Notes — Silo Crédit (domain-credit)

Toutes les versions publiées de ce dépôt respectent la spécification [SemVer 2.0.0](https://semver.org/lang/fr/).

---

## [0.0.1-SNAPSHOT] - 2026-10-31

### Type de release
**PATCH** (Initialisation technique et tests de la configuration multi-dépôts)

### Résumé
Mise en place de la structure autonome multi-modules du silo Crédit (`domain-credit`), regroupant `credit-api` et `credit-core` avec import du BOM centralisé, cycle de build autonome et configuration de déploiement GitHub Packages.

### Changements par composant
- **`credit-api`** : Contrats d'interfaces de calcul de conseil sur les prêts et modèles de données (aucun changement métier).
- **`credit-core`** : Moteur de conseil (`DefaultLoanAdviceCalculationService`) et persistance JPA des prêts (`credit_loan`, `loan_advice_settings`).

### Rétro-compatibilité & Compatibilité binaire
- Version initiale de calibrage technique 0.0.1.
