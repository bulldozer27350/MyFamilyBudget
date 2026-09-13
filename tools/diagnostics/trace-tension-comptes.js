#!/usr/bin/env node
'use strict';

/**
 * Diagnostic "one-shot" — tension des comptes / virements & mouvements prévus.
 *
 * Ce script NE réimplémente PAS la logique métier : il charge le vrai moteur de calcul
 * du front (view/js/models.js + view/js/calculations.js, fonction
 * calculateDetailedFinancialTimeline) tel qu'utilisé par la carte "Virements & mouvements
 * prévus" de la Vue d'ensemble, et rejoue vos données réelles avec.
 *
 * Objectif : pour un compte/placement donné, expliquer mois par mois :
 *   - si une alimentation mensuelle était attendue (fenêtre monthlyFrom/monthlyUntil),
 *   - si elle a effectivement eu lieu (présence d'un mouvement "Versement placement"),
 *   - si non, quel(s) compte(s) "surveillés" (pauseTriggerBalance) étaient sous leur seuil
 *     ce mois-là et ont donc probablement déclenché la mise en pause,
 *   - tous les autres mouvements (ponctions, renflouements, retraits) touchant ce compte.
 *
 * Prérequis : Node.js (aucune dépendance npm).
 *
 * Récupération des données réelles :
 *   - Depuis l'appli (Tailscale) :
 *       curl http://<hote-tailscale>:<port>/api/v1/budget -o budget-export.json
 *   - Ou via le bouton d'export de l'onglet Paramètres.
 *
 * Usage :
 *   node tools/diagnostics/trace-tension-comptes.js --data budget-export.json --compte "Nom du compte"
 *
 * Options :
 *   --data <fichier>     Chemin vers l'export JSON complet (BudgetDataDto). Obligatoire.
 *   --compte <label>     Libellé exact du compte/placement à tracer (colonne "Cible" du
 *                        tableau "Virements & mouvements prévus", ou onglet Patrimoine).
 *                        Obligatoire.
 *   --scenario <s>       pess | corr | opti (défaut : corr, comme la carte de la Vue
 *                        d'ensemble).
 *   --constant-euros     Active le mode "euros constants" (comme la case à cocher de
 *                        l'appli).
 *   --end-year <annee>   Force l'année de fin de simulation. Par défaut, calculée
 *                        exactement comme le fait le backend pour /overview :
 *                        max(annéeRetraite + 3, annéeNaissance + simulateUntilAge).
 *   --limit <n>          Nombre max de lignes affichées dans la trace mensuelle
 *                        (défaut : 300). Le résumé et la conclusion sont toujours affichés
 *                        en entier.
 *
 * Ce script n'écrit rien : il ne fait qu'analyser et afficher. Toute correction (données
 * ou code) reste à décider après lecture du résultat.
 */

const fs = require('fs');
const path = require('path');

// --- Chargement du moteur de calcul réel de l'application (zéro réimplémentation) -------
// On simule le contexte navigateur minimal attendu par les IIFE de models.js/calculations.js
// (elles s'accrochent à window.BudgetApp quand `window` existe).
global.window = global.window || {};
require(path.join(__dirname, '..', '..', 'view', 'js', 'tokens.js'));
require(path.join(__dirname, '..', '..', 'view', 'js', 'models.js'));
require(path.join(__dirname, '..', '..', 'view', 'js', 'calculations.js'));
const BudgetApp = global.window.BudgetApp;
const { normalizeData, calculateDetailedFinancialTimeline, findEarliestYear } = BudgetApp;

if (typeof calculateDetailedFinancialTimeline !== 'function') {
  console.error('Impossible de charger calculateDetailedFinancialTimeline depuis view/js/calculations.js.');
  console.error('Vérifiez que ce script se trouve bien dans tools/diagnostics/ à la racine du dépôt.');
  process.exit(1);
}

// --- Lecture des arguments CLI ----------------------------------------------------------
function parseArgs(argv) {
  const args = { scenario: 'corr', useConstantEuros: false, endYear: null, limit: 300 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--data') args.dataPath = argv[++i];
    else if (a === '--compte' || a === '--account') args.compte = argv[++i];
    else if (a === '--scenario') args.scenario = argv[++i];
    else if (a === '--constant-euros') args.useConstantEuros = true;
    else if (a === '--end-year') args.endYear = parseInt(argv[++i], 10);
    else if (a === '--limit') args.limit = parseInt(argv[++i], 10);
    else if (a === '--help' || a === '-h') args.help = true;
  }
  return args;
}

function printHelp() {
  console.log(`
Usage : node tools/diagnostics/trace-tension-comptes.js --data <export.json> --compte "<Nom du compte>" [options]

Options :
  --data <fichier>       Export JSON complet (BudgetDataDto). Obligatoire.
  --compte <label>       Libellé exact du compte/placement à tracer. Obligatoire.
  --scenario <s>         pess | corr | opti (défaut : corr).
  --constant-euros       Active le mode euros constants.
  --end-year <annee>     Force l'année de fin de simulation.
  --limit <n>            Nombre max de lignes dans la trace mensuelle (défaut 300).
`);
}

const args = parseArgs(process.argv.slice(2));
if (args.help || !args.dataPath || !args.compte) {
  printHelp();
  process.exit(args.help ? 0 : 1);
}

// --- Chargement et normalisation des données --------------------------------------------
let raw;
try {
  raw = JSON.parse(fs.readFileSync(path.resolve(args.dataPath), 'utf8'));
} catch (e) {
  console.error(`Impossible de lire/parser "${args.dataPath}" : ${e.message}`);
  process.exit(1);
}
const data = typeof normalizeData === 'function' ? normalizeData(raw) : raw;

const placements = data.placements || [];
const target = placements.find(p => p.label === args.compte);
if (!target) {
  console.error(`Compte "${args.compte}" introuvable dans data.placements. Comptes disponibles :`);
  placements.forEach(p => console.error(`  - ${p.label}`));
  process.exit(1);
}

// --- Détermination des années de simulation (même formule que /overview côté backend) ---
const settings = data.settings || {};
const birthYear = Number(settings.birthYear) || 1985;
const retireAge = Number(settings.retireAge) || 64;
const simulateUntilAge = Number(settings.simulateUntilAge) || 90;
const retireYear = birthYear + retireAge;
const startYear = findEarliestYear(data);
const wantedEnd = birthYear + simulateUntilAge;
const endYear = args.endYear || Math.max(retireYear + 3, wantedEnd);
const years = [];
for (let y = startYear; y <= endYear; y++) years.push(y);

console.log('='.repeat(78));
console.log('DIAGNOSTIC TENSION DES COMPTES — ' + target.label);
console.log('='.repeat(78));
console.log(`Période simulée : ${startYear} -> ${endYear} (${years.length} ans). Scénario : ${args.scenario}.`);
if (!args.endYear) {
  console.log(`  (fin = max(retraite+3=${retireYear + 3}, naissance+simulateUntilAge=${wantedEnd}))`);
}

// --- Configuration du compte tracé -------------------------------------------------------
console.log(`\n--- Configuration de "${target.label}" ---`);
console.log(`  solde initial (balance)     : ${target.balance ?? 0}`);
console.log(`  versement mensuel (monthly) : ${target.monthly ?? 0}`);
console.log(`  monthlyFrom                 : ${target.monthlyFrom || '(vide -> début de simulation)'}`);
console.log(`  monthlyUntil                : ${target.monthlyUntil || '(vide -> pas de fin)'}`);
console.log(`  pausePriority               : ${target.pausePriority ?? '(non pausable)'}`);
console.log(`  pauseTriggerBalance         : ${target.pauseTriggerBalance ?? '(pas de seuil de vigilance)'}`);
console.log(`  sweepPriority               : ${target.sweepPriority ?? '(non alimenté par excédent)'}`);
console.log(`  sweepCap                    : ${target.sweepCap ?? '(illimité)'}`);

// --- Mécanisme de tension : qui peut être mis en pause, qui est surveillé --------------
const pausablePlacements = placements.filter(p => p.pausePriority !== undefined && p.pausePriority !== null && p.pausePriority !== '');
const maxPauseLevel = pausablePlacements.length ? Math.max(...pausablePlacements.map(p => Number(p.pausePriority) || 0)) : 0;
const bufferWatch = placements.filter(p => p.pauseTriggerBalance !== undefined && p.pauseTriggerBalance !== null && p.pauseTriggerBalance !== '');

console.log(`\n--- Comptes "pausables" (priorité d'arrêt en cas de tension, niveau max = ${maxPauseLevel}) ---`);
if (!pausablePlacements.length) {
  console.log('  Aucun compte avec pausePriority défini : le mécanisme de pause ne peut jamais se déclencher.');
} else {
  pausablePlacements
    .slice()
    .sort((a, b) => Number(a.pausePriority) - Number(b.pausePriority))
    .forEach(p => {
      const marker = p.label === target.label ? '   <== COMPTE TRACÉ' : '';
      console.log(`  priorité ${p.pausePriority} : ${p.label}${marker}`);
    });
}

console.log('\n--- Comptes "surveillés" (déclenchent une alerte de tension si leur solde passe sous leur seuil) ---');
if (!bufferWatch.length) {
  console.log('  Aucun compte avec pauseTriggerBalance défini.');
} else {
  bufferWatch.forEach(p => {
    const marker = p.label === target.label ? '   <== COMPTE TRACÉ : il surveille SON PROPRE solde' : '';
    console.log(`  ${p.label} : seuil = ${p.pauseTriggerBalance}${marker}`);
  });
}

console.log('\n--- Seuils de trésorerie (compte courant) ---');
console.log(`  sweepEnabled (bascule d'excédent/ponction automatique) : ${!!settings.sweepEnabled}`);
console.log(`  cashFloor (seuil bas déclenchant une ponction)         : ${settings.cashFloor ?? '0 (non défini)'}`);
console.log(`  cashCeiling (plafond déclenchant un excédent versé)    : ${settings.cashCeiling ?? 'illimité (non défini)'}`);

// --- Exécution du VRAI moteur de calcul de l'application ---------------------------------
const timeline = calculateDetailedFinancialTimeline(data, years, args.scenario, !!args.useConstantEuros);

// --- Index utilitaires à partir des résultats officiels du moteur -----------------------
function monthKey(y, m) { return `${y}-${String(m + 1).padStart(2, '0')}`; }

function contributionActiveFor(p, y, m) {
  const monthlyFromYear = p.monthlyFrom ? new Date(p.monthlyFrom).getFullYear() : years[0];
  const monthlyFromMonth = p.monthlyFrom ? new Date(p.monthlyFrom).getMonth() : 0;
  const afterStart = y > monthlyFromYear || (y === monthlyFromYear && m >= monthlyFromMonth);
  let beforeEnd = true;
  if (p.monthlyUntil) {
    const untilYear = new Date(p.monthlyUntil).getFullYear();
    const untilMonth = new Date(p.monthlyUntil).getMonth();
    beforeEnd = y < untilYear || (y === untilYear && m <= untilMonth);
  }
  return afterStart && beforeEnd;
}

const contributionsByMonth = new Map(); // "YYYY-MM" -> événement "Versement placement"
const touchingEventsByMonth = new Map(); // "YYYY-MM" -> [événements où le compte est source OU cible]
timeline.events.forEach(e => {
  const key = e.dateISO.slice(0, 7);
  if (e.type === 'Versement placement' && e.target === target.label) {
    contributionsByMonth.set(key, e);
  }
  if (e.source === target.label || e.target === target.label) {
    if (!touchingEventsByMonth.has(key)) touchingEventsByMonth.set(key, []);
    touchingEventsByMonth.get(key).push(e);
  }
});

const monthlyByKey = new Map(); // "YYYY-MM" -> point mensuel (soldes de tous les comptes)
timeline.monthly.forEach(pt => monthlyByKey.set(`${pt.year}-${String(pt.month).padStart(2, '0')}`, pt));

// --- Trace mensuelle -----------------------------------------------------------------------
console.log(`\n--- Trace mensuelle : ${target.label} ---`);
console.log('(fenêtre active = attendue selon monthlyFrom/monthlyUntil ; versement = constaté dans le journal des mouvements)\n');

let firstContributionKey = null;
let printedLines = 0;
let blockedStreak = 0;

for (const y of years) {
  for (let m = 0; m < 12; m++) {
    const key = monthKey(y, m);
    const active = contributionActiveFor(target, y, m);
    const contributed = contributionsByMonth.has(key);
    const events = touchingEventsByMonth.get(key) || [];
    const pt = monthlyByKey.get(key);

    const blockedThisMonth = active && !contributed;
    blockedStreak = blockedThisMonth ? blockedStreak + 1 : 0;

    // On affiche : le premier mois d'un blocage, un rappel tous les 12 mois de blocage
    // continu, tout mois où un versement a bien eu lieu, et tout mois où un autre
    // mouvement (ponction, retrait, renflouement...) touche ce compte.
    const shouldPrint = contributed || events.length > 0 || (blockedThisMonth && (blockedStreak === 1 || blockedStreak % 12 === 0));

    if (shouldPrint && printedLines < args.limit) {
      printedLines++;
      const soldeCompte = pt ? pt[target.label] : undefined;
      const soldeCourant = pt ? pt.cash : undefined;
      let line = `${key} | fenêtre active: ${active ? 'oui' : 'non '} | versement mensuel: ${contributed ? 'OUI (+' + contributionsByMonth.get(key).amount + ' €)' : 'non'}`;
      line += ` | solde compte: ${soldeCompte !== undefined ? soldeCompte.toFixed(2) : '?'} € | solde courant: ${soldeCourant !== undefined ? soldeCourant.toFixed(2) : '?'} €`;
      if (blockedThisMonth && pt) {
        const watchedBelow = bufferWatch.filter(b => Number(pt[b.label]) < Number(b.pauseTriggerBalance)).map(b => b.label);
        if (watchedBelow.length) {
          line += `\n      -> sous tension probable à cause de : ${watchedBelow.join(', ')} (sous leur seuil ce mois-ci)`;
        } else if (pausablePlacements.some(p => p.label === target.label)) {
          line += '\n      -> aucun compte surveillé n\'est sous son seuil ce mois-ci : la pause vient probablement du seuil de trésorerie (cashFloor/cashCeiling), pas d\'un pauseTriggerBalance.';
        }
      }
      events.forEach(e => {
        if (e.type === 'Versement placement') return; // déjà affiché ci-dessus
        const sens = e.target === target.label ? `reçoit +${e.amount} € de ${e.source}` : `verse -${e.amount} € vers ${e.target}`;
        line += `\n      -> [${e.type}] ${sens}${e.comment ? ' (' + e.comment + ')' : ''}`;
      });
      console.log(line);
    } else if (blockedThisMonth && printedLines >= args.limit && printedLines === args.limit) {
      console.log(`  ... (limite de ${args.limit} lignes atteinte, utilisez --limit pour voir plus) ...`);
      printedLines++;
    }

    if (contributed && !firstContributionKey) firstContributionKey = key;
  }
}

// --- Conclusion --------------------------------------------------------------------------
console.log('\n' + '='.repeat(78));
console.log('CONCLUSION');
console.log('='.repeat(78));
if (!firstContributionKey) {
  console.log(`Aucun versement mensuel constaté sur "${target.label}" sur toute la période ${startYear}-${endYear}.`);
} else {
  console.log(`Premier versement mensuel constaté sur "${target.label}" : ${firstContributionKey}.`);
  const [fy, fm] = firstContributionKey.split('-').map(Number);
  if (fy > startYear + 1) {
    console.log('  Ce délai important par rapport au début de la simulation est le signe le plus probable');
    console.log('  d\'une mise en pause prolongée par le mécanisme de tension, plutôt qu\'un problème de');
    console.log('  configuration de date (monthlyFrom).');
  }
}
if (target.pausePriority !== undefined && target.pausePriority !== null && target.pausePriority !== '') {
  console.log(`\n"${target.label}" a une pausePriority de ${target.pausePriority} : il est mis en pause dès que le niveau`);
  console.log('de tension global (pauseLevel) atteint ou dépasse cette valeur.');
}
if (bufferWatch.some(b => b.label === target.label)) {
  console.log(`\nATTENTION : "${target.label}" est lui-même dans la liste des comptes surveillés`);
  console.log(`(pauseTriggerBalance=${target.pauseTriggerBalance}). Son solde ne peut monter QUE grâce aux`);
  console.log('versements mensuels — or ces versements sont bloqués tant que son solde est sous ce seuil.');
  console.log('Si ce compte démarre à 0 (ou en dessous du seuil) et a une pausePriority basse (parmi les');
  console.log('premières à être coupées), ceci peut créer un blocage qui s\'auto-entretient : le compte ne');
  console.log('reçoit jamais rien, donc reste sous son seuil, donc reste en pause indéfiniment — jusqu\'à ce');
  console.log('que d\'autres comptes surveillés remontent suffisamment pour faire baisser le niveau de');
  console.log('tension global, ou jusqu\'à la fin de la simulation.');
}
console.log('\nCe script ne modifie rien : il vous appartient de décider, à la lecture de ce qui précède,');
console.log('si le réglage à corriger est côté données (pausePriority / pauseTriggerBalance / monthlyFrom');
console.log('sur vos placements) ou s\'il révèle un comportement du code à ajuster.');
