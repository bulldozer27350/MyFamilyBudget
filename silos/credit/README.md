# MyFamilyBudget — Silo Crédit (`domain-credit`)

Dépôt autonome contenant les contrats et l'implémentation métier du domaine **Crédit** de MyFamilyBudget.

## Structure des modules

- **`credit-api`** : Contrats d'interfaces (`LoanAdviceCalculationService`, `LoanAdviceSettingsService`, `LoanReader`, `LoanWriter`), modèles DTO de prêt et de résultat de conseil. Purement JDK (sans dépendance externe Spring ni JPA).
- **`credit-core`** : Implémentation du moteur de conseil de crédit (`DefaultLoanAdviceCalculationService`) et persistance autonome JPA (`credit_loan`, `loan_advice_settings`).

## Compilation et tests

```bash
mvn clean verify
```

## Publication sur GitHub Packages

Les artefacts sont publiés sur GitHub Packages via le workflow GitHub Actions lors de tout push sur `main` ou création de tag `v*` :

```bash
mvn deploy
```
