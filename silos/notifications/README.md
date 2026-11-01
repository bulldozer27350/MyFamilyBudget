# MyFamilyBudget — Silo Notifications (`domain-notifications`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Notifications** de MyFamilyBudget.

## Structure des modules

- **`notifications-api`** : contrats, paramètres, entrées de règles. Purement JDK (sans dépendance externe Spring ni JPA).
- **`notifications-core`** : règles pures, dispatch, paramètres, canal Web Push, et persistance JPA des paramètres, du journal d'envoi et des abonnements.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
