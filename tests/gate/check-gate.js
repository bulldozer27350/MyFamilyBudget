'use strict';

// VT-600 : garde "aucun test obligatoire uniquement tolere ou desactive".
//
//   node tests/gate/check-gate.js static    Analyse des sources (aucune execution requise), Porte A (SILO-190) incluse
//   node tests/gate/check-gate.js gate-a    Controles statiques de la Porte A seuls (SILO-190)
//   node tests/gate/check-gate.js reports   Analyse des rapports Surefire apres `mvn test`
//   node tests/gate/check-gate.js all       static puis reports
//
// Code de sortie 0 = gate respecte, 1 = au moins une violation (listee sur stderr).
// Aucun contrat metier n est touche : le script ne fait que lire des fichiers.

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const BACKEND_TESTS = path.join(ROOT, 'back', 'server', 'src', 'test');
// Tests unitaires des modules de domaine extraits du reactor (MAVEN-020 : domain-retirement devenu retirement-core (SILO-150), MAVEN-030 : domain-tax devenu tax-core (SILO-151), MAVEN-040 : domain-wealth devenu wealth-core (SILO-152), MAVEN-060 : domain-bank-pointage devenu bank-pointage-core (SILO-154), MAVEN-050 : domain-treasury devenu treasury-core (SILO-153), MAVEN-070 : domain-analysis devenu analysis-core (SILO-155), MAVEN-080 : domain-credit devenu credit-core (SILO-156), domain-goals devenu goals-api (SILO-157), MAVEN-090 : domain-notifications devenu notifications-core (SILO-158), silo Marché : market-core (SILO-159))
const MODULE_TESTS = ['retirement-core', 'tax-core', 'wealth-core', 'bank-pointage-core', 'treasury-core', 'analysis-core', 'credit-core', 'goals-api', 'goals-core', 'notifications-core', 'market-core', 'infra-jpa'].map(m => path.join(ROOT, 'back', m, 'src', 'test'));
const SUREFIRE_DIR = path.join(ROOT, 'back', 'server', 'target', 'surefire-reports');
const E2E_DIR = path.join(ROOT, 'tests', 'e2e');
const WORKFLOW = path.join(ROOT, '.github', 'workflows', 'ci-cd.yml');
const POMS = [
  path.join(ROOT, 'back', 'pom.xml'),
  path.join(ROOT, 'back', 'retirement-api', 'pom.xml'),
  path.join(ROOT, 'back', 'retirement-core', 'pom.xml'),
  path.join(ROOT, 'back', 'tax-api', 'pom.xml'),
  path.join(ROOT, 'back', 'tax-core', 'pom.xml'),
  path.join(ROOT, 'back', 'wealth-api', 'pom.xml'),
  path.join(ROOT, 'back', 'wealth-core', 'pom.xml'),
  path.join(ROOT, 'back', 'bank-pointage-api', 'pom.xml'),
  path.join(ROOT, 'back', 'bank-pointage-core', 'pom.xml'),
  path.join(ROOT, 'back', 'treasury-api', 'pom.xml'),
  path.join(ROOT, 'back', 'treasury-core', 'pom.xml'),
  path.join(ROOT, 'back', 'analysis-api', 'pom.xml'),
  path.join(ROOT, 'back', 'analysis-core', 'pom.xml'),
  path.join(ROOT, 'back', 'credit-api', 'pom.xml'),
  path.join(ROOT, 'back', 'credit-core', 'pom.xml'),
  path.join(ROOT, 'back', 'goals-api', 'pom.xml'),
  path.join(ROOT, 'back', 'goals-core', 'pom.xml'),
  path.join(ROOT, 'back', 'settings-api', 'pom.xml'),
  path.join(ROOT, 'back', 'settings-core', 'pom.xml'),
  path.join(ROOT, 'back', 'notifications-api', 'pom.xml'),
  path.join(ROOT, 'back', 'notifications-core', 'pom.xml'),
  path.join(ROOT, 'back', 'market-api', 'pom.xml'),
  path.join(ROOT, 'back', 'market-core', 'pom.xml'),
  path.join(ROOT, 'back', 'api', 'pom.xml'),
  path.join(ROOT, 'back', 'infra-jpa', 'pom.xml'),
  path.join(ROOT, 'back', 'transition-snapshot', 'pom.xml'),
  path.join(ROOT, 'back', 'application-api', 'pom.xml'),
  path.join(ROOT, 'back', 'application', 'pom.xml'),
  path.join(ROOT, 'back', 'persistence', 'pom.xml'),
  path.join(ROOT, 'back', 'server', 'pom.xml'),
];

// Seule exception admise : la variante PostgreSQL de VT-320, activee par variable d environnement.
// Elle DOIT s executer dans la CI (voir checkReports, exigee quand CI ou MFB_REQUIRE_POSTGRES est defini).
const ALLOWED_CONDITIONAL = [
  { file: 'RestartPersistenceTest.java', pattern: /@EnabledIfEnvironmentVariable\(\s*named\s*=\s*"MFB_TEST_POSTGRES_URL"/ },
];
const POSTGRES_REPORT = 'TEST-com.moe.myfamilybudget.server.internal.integration.RestartPersistenceTest.xml';
const POSTGRES_TEST_NAME = 'postgres_dataSurvivesSpringRestart';

const JAVA_FORBIDDEN = [
  /@Disabled\b/, /@Ignore\b/, /@DisabledIf\w*/, /@DisabledOn\w*/, /@DisabledIfEnvironmentVariable/,
  /@EnabledIf\w*/, /@EnabledOn\w*/, /@EnabledIfEnvironmentVariable/, /@EnabledIfSystemProperty/,
  /Assumptions\.assume\w+/, /\bassumeTrue\b/, /\bassumeFalse\b/,
];
const PLAYWRIGHT_FORBIDDEN = [
  /\btest\.skip\b/, /\btest\.fixme\b/, /\btest\.fail\b/, /\btest\.slow\b/, /\.only\s*\(/,
  /\bdescribe\.skip\b/, /\bdescribe\.fixme\b/, /\btest\.describe\.(?:skip|fixme|only)\b/,
];
const WORKFLOW_FORBIDDEN = [
  /continue-on-error\s*:\s*true/i, /-DskipTests/, /-Dmaven\.test\.skip/, /-Dmaven\.test\.failure\.ignore/,
  /--no-verify/, /\|\|\s*true\b/,
];
const POM_FORBIDDEN = [/<skipTests>\s*true/i, /<testFailureIgnore>\s*true/i, /<skip>\s*true\s*<\/skip>/i];

// Porte A (SILO-190, doc/architecture/21-plan-silotage.md) : silos isoles.
const BACK = path.join(ROOT, 'back');
const SILOS = ['retirement', 'tax', 'wealth', 'bank-pointage', 'treasury', 'analysis', 'credit', 'goals', 'notifications', 'market'];
// Seuls modules autorises a referencer BudgetDataModel dans leurs sources de production (le type vit dans
// transition-snapshot jusqu'a SILO-230 ; la persistance le manipule jusqu'a SILO-210 a SILO-216 et SILO-230).
const BUDGET_DATA_MODEL_MODULES = ['persistence', 'transition-snapshot'];

const violations = [];

function rel(file) {
  return path.relative(ROOT, file).split(path.sep).join('/');
}

function walk(dir, extension, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'report') continue;
      walk(full, extension, out);
    } else if (entry.name.endsWith(extension)) {
      out.push(full);
    }
  }
  return out;
}

/** Retire les commentaires pour ne pas signaler une mention dans un commentaire ou une doc. */
function stripComments(source, style) {
  if (style === 'yaml') return source.replace(/(^|\s)#.*$/gm, '$1');
  if (style === 'xml') return source.replace(/<!--[\s\S]*?-->/g, '');
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/.*$/gm, '$1');
}

function scan(file, patterns, style, allowed = []) {
  const source = stripComments(fs.readFileSync(file, 'utf8'), style);
  source.split(/\r?\n/).forEach((line, index) => {
    for (const pattern of patterns) {
      if (!pattern.test(line)) continue;
      const exempt = allowed.some(a => path.basename(file) === a.file && a.pattern.test(line));
      if (!exempt) {
        violations.push(rel(file) + ':' + (index + 1) + ' -> ' + line.trim());
      }
    }
  });
}

function checkStatic() {
  MODULE_TESTS.reduce((all, dir) => walk(dir, '.java', all), walk(BACKEND_TESTS, '.java')).forEach(f => scan(f, JAVA_FORBIDDEN, 'java', ALLOWED_CONDITIONAL));
  walk(E2E_DIR, '.js').forEach(f => scan(f, PLAYWRIGHT_FORBIDDEN, 'java'));
  if (fs.existsSync(WORKFLOW)) scan(WORKFLOW, WORKFLOW_FORBIDDEN, 'yaml');
  POMS.forEach(pom => { if (fs.existsSync(pom)) scan(pom, POM_FORBIDDEN, 'xml'); });

  checkPorteA();

  const config = path.join(ROOT, 'playwright.config.js');
  if (fs.existsSync(config)) {
    const retries = /retries\s*:\s*(\d+)/.exec(fs.readFileSync(config, 'utf8'));
    if (retries && Number(retries[1]) > 0) {
      violations.push(rel(config) + ' -> retries=' + retries[1] + ' : un test instable serait toleré au lieu d etre corrige');
    }
  }
}

/**
 * Porte A (SILO-190) : controles statiques, sans execution. Le build complet, ArchUnit, les tests JS et les E2E sont
 * verifies par les autres etapes de la CI ; ici :
 *   1. `application` ne declare aucun `*-core` de silo (ni reliquat `domain-xxx`) dans son pom ;
 *   2. aucune source de production d `application` ne reference un package `domain.<silo>.core` ;
 *   3. aucun `BudgetDataModel` hors persistance (et transition-snapshot, qui le porte) dans les sources de production.
 */
function checkPorteA() {
  const appPom = path.join(BACK, 'application', 'pom.xml');
  if (fs.existsSync(appPom)) {
    const coreDependency = new RegExp('<artifactId>\\s*(?:(?:' + SILOS.join('|') + ')-core|domain-[a-z-]+)\\s*</artifactId>');
    scan(appPom, [coreDependency], 'xml');
  }

  const siloCorePackage = /com\.moe\.myfamilybudget\.domain\.[a-z]+\.core\b/;
  walk(path.join(BACK, 'application', 'src', 'main'), '.java').forEach(f => scan(f, [siloCorePackage], 'java'));

  fs.readdirSync(BACK, { withFileTypes: true })
    .filter(e => e.isDirectory() && !BUDGET_DATA_MODEL_MODULES.includes(e.name))
    .forEach(e => walk(path.join(BACK, e.name, 'src', 'main'), '.java')
      .forEach(f => scan(f, [/\bBudgetDataModel\b/], 'java')));
}

function checkReports() {
  if (!fs.existsSync(SUREFIRE_DIR)) {
    violations.push(rel(SUREFIRE_DIR) + ' absent : lancer `mvn -f back/pom.xml -B -pl server -am test` avant `reports`');
    return;
  }
  const reports = fs.readdirSync(SUREFIRE_DIR).filter(n => /^TEST-.*\.xml$/.test(n));
  if (reports.length === 0) {
    violations.push(rel(SUREFIRE_DIR) + ' ne contient aucun rapport TEST-*.xml');
    return;
  }

  const mustRunPostgres = Boolean(process.env.CI) || process.env.MFB_REQUIRE_POSTGRES === 'true';
  let total = 0;
  for (const name of reports) {
    const xml = fs.readFileSync(path.join(SUREFIRE_DIR, name), 'utf8');
    const cases = xml.match(/<testcase\b[\s\S]*?(?:\/>|<\/testcase>)/g) || [];
    total += cases.length;
    for (const testCase of cases) {
      if (!/<skipped\b/.test(testCase)) continue;
      const caseName = /name="([^"]+)"/.exec(testCase);
      const isPostgres = name === POSTGRES_REPORT && caseName && caseName[1].startsWith(POSTGRES_TEST_NAME);
      if (isPostgres && !mustRunPostgres) continue; // poste local sans PostgreSQL : tolere hors CI
      violations.push(name + ' : test ignore -> ' + (caseName ? caseName[1] : '?')
        + (isPostgres ? ' (la variante PostgreSQL doit s executer : definir MFB_TEST_POSTGRES_URL)' : ''));
    }
  }
  if (total === 0) violations.push('aucun test execute dans ' + rel(SUREFIRE_DIR));

  if (mustRunPostgres && !reports.includes(POSTGRES_REPORT)) {
    violations.push(POSTGRES_REPORT + ' absent : RestartPersistenceTest n a pas ete execute');
  }
}

const mode = process.argv[2] || 'all';
if (!['static', 'gate-a', 'reports', 'all'].includes(mode)) {
  console.error('Usage : node tests/gate/check-gate.js static|gate-a|reports|all');
  process.exit(2);
}
if (mode === 'gate-a') checkPorteA();
if (mode === 'static' || mode === 'all') checkStatic();
if (mode === 'reports' || mode === 'all') checkReports();

if (violations.length > 0) {
  console.error('GATE VT-600 : ' + violations.length + ' violation(s)');
  violations.forEach(v => console.error('  - ' + v));
  process.exit(1);
}
console.log('GATE VT-600 (' + mode + ') : OK');
