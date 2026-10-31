'use strict';

// R-93 (24-backlog-reste-a-faire.md / 25-cartographie-modules-patchs.md, SILO-350) : CI sélective par
// sous-graphe. Calcule, à partir d'une liste de fichiers modifiés (un chemin relatif à la racine du
// dépôt par ligne, lus sur stdin — typiquement `git diff --name-only <base> <head>`), le sous-ensemble
// de modules Maven du reactor `back/pom.xml` à construire et tester pour ce push, au lieu de toujours
// reconstruire `server` (et, via sa dépendance de test sur `bootstrap`, la quasi-totalité du reactor).
//
// Sortie sur stdout, au format `clef=valeur` (une ligne par clef), directement utilisable avec
// `>> "$GITHUB_OUTPUT"` :
//   full=true|false   true si un fichier modifié touche la définition du build/CI elle-même
//                      (back/pom.xml, .github/workflows, tests/gate, openapi, Dockerfile,
//                      docker-compose*.yml) ou un chemin sous back/ qui ne correspond à aucun module
//                      connu : repli prudent sur la suite complète (comportement d'avant R-93).
//   modules=a,b,c      liste triée, sans doublon, des modules dont du code a changé (vide si full=true
//                      ou si rien sous back/ n'a changé). Ne compte pas la documentation
//                      (`back/<module>/doc/**`), qui ne déclenche aucun test.
//   any=true|false     true si un test backend doit tourner pour ce push (full=true, ou modules non
//                      vide). false uniquement quand aucun fichier sous back/ n'a changé (ex. un
//                      changement limité à view/, tests/e2e ou à de la documentation).
//
// La sélection des modules eux-mêmes (au-delà de ceux listés ici) est laissée au reactor Maven :
// `mvn -pl <modules> -amd -am test` ajoute automatiquement les dépendances nécessaires (-am) et tout
// module qui dépend (même en portée test, ex. `server` via `bootstrap`) de l'un des modules sélectionnés
// (-amd) — c'est le sous-graphe réellement impacté, pas seulement les modules listés ici.

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');

// Fichiers dont la modification concerne le build/la CI eux-mêmes (ou des artefacts partagés par tout
// le reactor, comme le contrat OpenAPI) : aucune sélectivité fiable n'est possible, on repli sur la
// suite complète.
const GLOBAL_TRIGGERS = [
  '.github/workflows/',
  'tests/gate/',
  'back/pom.xml',
  'openapi/',
  'Dockerfile',
  'docker-compose.yml',
  'docker-compose.prod.yml',
];

function readKnownModules() {
  const pom = fs.readFileSync(path.join(ROOT, 'back', 'pom.xml'), 'utf8');
  const block = /<modules>([\s\S]*?)<\/modules>/.exec(pom);
  if (!block) return [];
  const names = [];
  const re = /<module>([^<]+)<\/module>/g;
  let m;
  while ((m = re.exec(block[1])) !== null) names.push(m[1].trim());
  return names;
}

function isGlobalTrigger(file) {
  return GLOBAL_TRIGGERS.some(prefix => (prefix.endsWith('/') ? file.startsWith(prefix) : file === prefix));
}

function main() {
  const input = fs.readFileSync(0, 'utf8');
  const files = input.split(/\r?\n/).map(f => f.trim()).filter(Boolean);
  const knownModules = readKnownModules();

  let full = false;
  const modules = new Set();

  for (const file of files) {
    if (isGlobalTrigger(file)) { full = true; continue; }
    if (!file.startsWith('back/')) continue; // view/, tests/e2e, documentation racine... : hors backend

    const segments = file.slice('back/'.length).split('/');
    const moduleName = segments[0];
    if (!moduleName || !knownModules.includes(moduleName)) { full = true; continue; }
    if (segments[1] === 'doc') continue; // documentation d'architecture du module : ne déclenche rien

    modules.add(moduleName);
  }

  const moduleList = Array.from(modules).sort();
  const any = full || moduleList.length > 0;

  process.stdout.write('full=' + full + '\n');
  process.stdout.write('modules=' + moduleList.join(',') + '\n');
  process.stdout.write('any=' + any + '\n');
}

main();
