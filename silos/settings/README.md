# MyFamilyBudget — Silo Paramètres (`domain-settings`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Paramètres** de MyFamilyBudget.

## Structure des modules

- **`settings-api`** : simulation, hypothèses économiques. Purement JDK (sans dépendance externe Spring ni JPA).
- **`settings-core`** : persistance JPA de la table app_settings.

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
