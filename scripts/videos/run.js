'use strict';
/**
 * Lanceur de vidéos tutoriels MyFamilyBudget.
 *
 * Usage :
 *   node scripts/videos/run.js         → liste les vidéos disponibles
 *   node scripts/videos/run.js 0       → Vidéo 0 — Présentation générale
 *   node scripts/videos/run.js 1.1     → Vidéo 1.1 — Paramètres généraux
 *   node scripts/videos/run.js all     → toutes les vidéos dans l'ordre
 *
 * Variables d'environnement optionnelles :
 *   E2E_FRONT_URL   (défaut : http://localhost:3000)
 *   E2E_API_URL     (défaut : http://localhost:8080/api/v1)
 */

const path = require('path');
const { execSync } = require('child_process');

const VIDEOS = [
  { id: '0',   file: 'video-00-presentation.js',      title: 'Présentation générale' },
  { id: '1.1', file: 'video-11-parametres.js',        title: 'Paramètres généraux' },
  { id: '1.2', file: 'video-12-tresorerie-depart.js', title: 'Trésorerie de départ & Date Pivot' },
  { id: '1.3', file: 'video-13-sweep.js',             title: 'Virement automatique & Seuils (Sweep)' },
  { id: '1.4', file: 'video-14-categories-actifs.js', title: 'Catégories d\'actifs' },
  { id: '1.5', file: 'video-15-sauvegarde.js',        title: 'Sauvegarde, export & restauration' },
  { id: '2.1', file: 'video-21-import-csv.js',        title: 'Importer un relevé bancaire (CSV)' },
  { id: '2.2', file: 'video-22-categories-depenses.js', title: 'Créer des catégories de dépenses' },
  { id: '2.3', file: 'video-23-categorisation.js',    title: 'Catégoriser les transactions importées' },
  { id: '2.4', file: 'video-24-recherche-tri.js',     title: 'Trier et rechercher dans les transactions' },
  { id: '2.5', file: 'video-25-operations-en-cours.js', title: 'Opérations en cours (solde réel disponible)' },
  { id: '2.6', file: 'video-26-pointage.js',          title: 'Le Pointage mensuel' },
  { id: '2.7', file: 'video-27-analyse.js',           title: 'Analyse : comprendre ses dépenses' },
  { id: '2.8', file: 'video-28-overview.js',          title: 'Vue d\'ensemble : tableau de bord global' },
  { id: '3.1', file: 'video-31-revenus-recurrents.js', title: 'Saisir ses revenus récurrents (Trésorerie)' },
  { id: '3.2', file: 'video-32-charges-recurrentes.js', title: 'Saisir ses charges récurrentes' },
  { id: '3.3', file: 'video-33-credits-tresorerie.js',  title: 'Remboursements de crédits (charges liées aux prêts)' },
  { id: '3.4', file: 'video-34-placements-patrimoine.js', title: 'Placements & Patrimoine : créer ses livrets et comptes' },
  { id: '3.5', file: 'video-35-credits-patrimoine.js',  title: 'Placements : crédits immobiliers (Passif)' },
  { id: '3.6', file: 'video-36-sweep-placements.js',    title: 'Virement automatique Sweep : lier épargne et compte courant' },
  { id: '3.7', file: 'video-37-depenses-ponctuelles.js', title: 'Dépenses ponctuelles et primes' },
  { id: '3.8', file: 'video-38-jauges-fire.js',         title: 'Vue d\'ensemble : jauges FIRE et indépendance financière' },
  { id: '3.9', file: 'video-39-retraite.js',            title: 'Retraite : estimation des pensions' },
  { id: '3.10', file: 'video-310-impots.js',            title: 'Impôts : Prélèvement à la Source et simulation' },
  { id: '3.11', file: 'video-311-date-pivot.js',        title: 'Date Pivot : basculer du réel vers le prévisionnel' },
];

function run(scriptFile) {
  const full = path.join(__dirname, scriptFile);
  console.log(`\n${'─'.repeat(60)}`);
  console.log(`▶  ${scriptFile}`);
  console.log('─'.repeat(60));
  execSync(`node "${full}"`, { stdio: 'inherit' });
}

function list() {
  console.log('\nVidéos disponibles :\n');
  VIDEOS.forEach(v => console.log(`  ${v.id.padEnd(6)} ${v.title}  (${v.file})`));
  console.log('\nUsage : node run.js <id>  |  node run.js all\n');
}

const arg = process.argv[2];

if (!arg) {
  list();
} else if (arg === 'all') {
  for (const v of VIDEOS) run(v.file);
} else {
  const v = VIDEOS.find(x => x.id === arg);
  if (!v) {
    console.error(`Vidéo introuvable : "${arg}"`);
    list();
    process.exit(1);
  }
  run(v.file);
}
