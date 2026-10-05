'use strict';

// VT-600 : garde "aucun test obligatoire uniquement tolere ou desactive".
//
//   node tests/gate/check-gate.js static    Analyse des sources (aucune execution requise)
//   node tests/gate/check-gate.js reports   Analyse des rapports Surefire apres `mvn test`
//   node tests/gate/check-gate.js all       static puis reports
//
// Code de sortie 0 = gate respecte, 1 = au moins une violation (listee sur stderr).
// Aucun contrat metier n est touche : le script ne fait que lire des fichiers.

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const BACKEND_TESTS = path.join(ROOT, 'back', 'server', 'src', 'test');
// Tests unitaires des modules de domaine extraits du reactor (MAVEN-020 : domain-retirement devenu retirement-core (SILO-150), MAVEN-030 : domain-tax devenu tax-core (SILO-151), MAVEN-040 : domain-wealth, MAVEN-060 : domain-bank-pointage, MAVEN-050 : domain-treasury, MAVEN-070 : domain-analysis, MAVEN-080 : domain-credit, domain-goals, MAVEN-090 : domain-notifications)
const MODULE_TESTS = ['retirement-core', 'tax-core', 'domain-wealth', 'domain-bank-pointage', 'domain-treasury', 'domain-analysis', 'domain-credit', 'domain-goals', 'domain-notifications'].map(m => path.join(ROOT, 'back', m, 'src', 'test'));
const SUREFIRE_DIR = path.join(ROOT, 'back', 'server', 'target', 'surefire-reports');
const E2E_DIR = path.join(ROOT, 'tests', 'e2e');
const WORKFLOW = path.join(ROOT, '.github', 'workflows', 'ci-cd.yml');
const POMS = [
  path.join(ROOT, 'back', 'pom.xml'),
  path.join(ROOT, 'back', 'retirement-api', 'pom.xml'),
  path.join(ROOT, 'back', 'retirement-core', 'pom.xml'),
  path.join(ROOT, 'back', 'tax-api', 'pom.xml'),
  path.join(ROOT, 'back', 'tax-core', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-wealth', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-bank-pointage', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-treasury', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-analysis', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-credit', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-goals', 'pom.xml'),
  path.join(ROOT, 'back', 'domain-notifications', 'pom.xml'),
  path.join(ROOT, 'back', 'api', 'pom.xml'),
  path.join(ROOT, 'back', 'transition-snapshot', 'pom.xml'),
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

  const config = path.join(ROOT, 'playwright.config.js');
  if (fs.existsSync(config)) {
    const retries = /retries\s*:\s*(\d+)/.exec(fs.readFileSync(config, 'utf8'));
    if (retries && Number(retries[1]) > 0) {
      violations.push(rel(config) + ' -> retries=' + retries[1] + ' : un test instable serait toleré au lieu d etre corrige');
    }
  }
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
if (!['static', 'reports', 'all'].includes(mode)) {
  console.error('Usage : node tests/gate/check-gate.js static|reports|all');
  process.exit(2);
}
if (mode === 'static' || mode === 'all') checkStatic();
if (mode === 'reports' || mode === 'all') checkReports();

if (violations.length > 0) {
  console.error('GATE VT-600 : ' + violations.length + ' violation(s)');
  violations.forEach(v => console.error('  - ' + v));
  process.exit(1);
}
console.log('GATE VT-600 (' + mode + ') : OK');
