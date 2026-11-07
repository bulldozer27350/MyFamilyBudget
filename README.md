# MyFamilyBudget — Processus de release

## Architecture

MyFamilyBudget est l'application hub d'un ensemble de 12 dépôts GitHub
indépendants : elle-même, plus 11 satellites Maven dédiés exclusivement à
cette application (aucun autre consommateur) :

```
myfamilybudget-domain-credit
myfamilybudget-domain-market
myfamilybudget-domain-notifications
myfamilybudget-domain-treasury
myfamilybudget-domain-bank-pointage
myfamilybudget-domain-wealth
myfamilybudget-domain-tax
myfamilybudget-domain-retirement
myfamilybudget-domain-analysis
myfamilybudget-domain-goals
myfamilybudget-domain-settings
```

Chaque satellite est référencé dans `back/pom.xml` via une property
(`<credit.version>`, etc.) et un bloc `<repository>` GitHub Packages dédié.
Documentation complète de la stratégie : `back/doc/architecture/27-strategie-multi-depots-bom-semver.md`.

## Principes

- SemVer `x.y.z` strict sur les 12 dépôts, jamais de ranges Maven.
- En continu, chaque dépôt reste en `x.y.z-SNAPSHOT`.
- Release = action manuelle et volontaire, jamais automatique à chaque
  commit. Un satellite n'est release que si son intégration a déjà été
  validée via l'application.
- Le BOM applicatif (`back/pom.xml`) ne pointe jamais sur un satellite en
  SNAPSHOT au moment de la release de l'application — garde-fou CI qui
  fait échouer le build sinon.
- Une version déjà publiée ne se réécrit jamais. Les tags `v*` sont
  protégés sur les 12 dépôts (GitHub Ruleset `protect-release-tags`) :
  suppression et déplacement bloqués, sauf bypass admin en dernier
  recours. La création d'un nouveau tag reste libre.
- Traçabilité : à la release de l'application, le SHA exact de chaque
  satellite est résolu via l'API GitHub et figé dans
  `release-manifest.yaml`, attaché à la GitHub Release — preuve
  indépendante du tag (même si un tag était déplacé par erreur).

## Marche à suivre pour livrer une version

### Étape 1 — Satellites (dans n'importe quel ordre, ils ne dépendent pas les uns des autres)

Pour chaque satellite dont le changement a été validé via cette
application : suivre le `README.md` de ce dépôt satellite (bump
`pom.xml`, mise à jour de `release-note.md`, tag `vX.Y.Z`, attendre que
la GitHub Release du satellite soit bien créée avant de passer à
l'étape 2 pour ce satellite).

Les satellites non concernés par cette release restent inchangés : pas
besoin de les retagger si rien n'a changé pour eux depuis la dernière
release de l'application.

### Étape 2 — Mise à jour du BOM

Dans `back/pom.xml`, mettre à jour la property de chaque satellite
fraîchement release (et uniquement ceux-là) vers sa nouvelle version
stable. Committer sur `main` :
```bash
git add back/pom.xml
git commit -m "Mise a jour BOM : credit 0.1.0, wealth 0.1.0"
git push origin main
```

### Étape 3 — Bump de la version de l'application elle-même

Dans `back/pom.xml` (version racine du reactor), retirer le suffixe
`-SNAPSHOT`. Mettre à jour le `release-note.md` de l'application de la
même façon que sur les satellites. Committer sur `main` :
```bash
git add back/pom.xml release-note.md
git commit -m "Release 0.1.0"
git push origin main
```

### Étape 4 — Tag de release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Deux workflows se déclenchent en parallèle sur ce tag :

- **`release.yml`** :
  - garde-fou 1 : `pom.xml` doit être en version stable et correspondre
    exactement au tag ;
  - garde-fou 2 : aucune des 11 properties satellites du BOM ne doit être
    en SNAPSHOT ;
  - résolution du SHA de tag Git de chacun des 11 satellites via l'API
    GitHub, génération de `release-manifest.yaml` ;
  - construction du jar exécutable, ajout d'une JRE Temurin 21 Windows
    x64, production de `MyFamilyBudget-X.Y.Z-windows-x64.zip` ;
  - création de la GitHub Release avec `release-manifest.yaml` et le zip
    portable en pièces jointes.

- **`ci-cd.yml`** : construit et pousse l'image Docker
  `moetourneau/myfamilybudget:vX.Y.Z` (sans toucher `:latest` ni
  `:sha-xxxxxxx`, qui restent le reflet du dernier push sur `main`).

### Étape 5 — Reprendre le développement en continu

Bumper `back/pom.xml` vers le SNAPSHOT suivant, committer et pousser sur
`main`, pour que `publish-snapshot` reprenne (il ne fait rien tant que la
version n'est pas un SNAPSHOT).

## Vérifications après une release

- La GitHub Release de l'application contient bien `release-manifest.yaml`
  et `MyFamilyBudget-X.Y.Z-windows-x64.zip`.
- L'image Docker `moetourneau/myfamilybudget:vX.Y.Z` existe bien sur
  Docker Hub.
- Chaque satellite inclus dans cette release a bien sa propre GitHub
  Release correspondante.

## Points de vigilance

- Ne jamais tenter de déplacer ou supprimer un tag `v*` déjà poussé (voir
  Ruleset) : en cas d'erreur, publier une version corrective.
- Un tag posé sur l'application avant que ses satellites ne soient
  effectivement publiés (BOM encore en SNAPSHOT) échoue volontairement —
  c'est le garde-fou 2, pas un bug.
