/**
 * Génération du Bilan Patrimonial imprimable (export PDF via impression navigateur)
 *
 * Aucune dépendance externe : le document est ouvert dans un nouvel onglet et
 * l'utilisateur choisit "Microsoft Print to PDF" (ou équivalent) dans la boîte
 * d'impression du navigateur pour l'enregistrer en PDF. Fonctionne entièrement
 * hors ligne, comme le reste de l'outil.
 */
(function (exports) {
  'use strict';

  const {
    C,
    eur
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    classifyAllocation,
    projectPlacementBalanceAt,
    projectLoanCrdToDate
  } = exports.classifyAllocation ? exports : window.BudgetApp || {};

  function escapeHtml(str) {
    return String(str ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function formatDateFR(iso) {
    if (!iso) return "—";
    const parts = String(iso).split("-");
    return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : iso;
  }

  function buildKPI(label, value, accent) {
    return `
      <div class="report-kpi">
        <div class="report-kpi-label">${escapeHtml(label)}</div>
        <div class="report-kpi-value" style="color:${accent || "#232A2E"}">${escapeHtml(value)}</div>
      </div>`;
  }

  function buildTable(headers, rows) {
    if (!rows.length) {
      return `<p class="report-empty">Aucune donnée saisie.</p>`;
    }
    const thead = headers.map(h => `<th>${escapeHtml(h)}</th>`).join("");
    const tbody = rows.map(r => `<tr>${r.map(c => `<td>${escapeHtml(c)}</td>`).join("")}</tr>`).join("");
    return `<table class="report-table"><thead><tr>${thead}</tr></thead><tbody>${tbody}</tbody></table>`;
  }

  /**
   * Construit le document HTML complet du bilan patrimonial (chaîne autonome,
   * avec sa propre feuille de style adaptée à l'impression A4).
   *
   * @param {object} data  Le jeu de données complet de l'outil (placements, loans, realEstate, settings...)
   * @param {object} opts  { patrimoineActuel, pivotBalance, pivotLabel }
   */
  function buildPatrimoineReportHTML(data, opts) {
    const {
      pivotBalance = null,
      pivotLabel = null
    } = opts || {};
    const todayISO = new Date().toISOString().slice(0, 10);
    const getBalance = p => projectPlacementBalanceAt(p, todayISO, "rateCorr", data.transfers);
    const startBalance = Number(data?.settings?.startBalance) || 0;
    const tresorerie = pivotBalance !== null ? Number(pivotBalance) || 0 : startBalance;

    const {
      allocation
    } = classifyAllocation(data.placements, getBalance, tresorerie, data.assetCategories);

    const crdToday = l => projectLoanCrdToDate ? projectLoanCrdToDate(l, todayISO) : Number(l.crd) || 0;
    const totalPlacements = (data.placements || []).reduce((s, p) => s + getBalance(p), 0);
    const totalCRD = (data.loans || []).reduce((s, l) => s + crdToday(l), 0);
    const totalImmobilier = (data.realEstate || []).reduce((s, r) => s + (Number(r.currentValue) || 0), 0);
    const patrimoineNet = totalPlacements + totalImmobilier - totalCRD;

    const now = new Date();
    const dateGeneration = now.toLocaleDateString("fr-FR", {
      day: "numeric",
      month: "long",
      year: "numeric"
    });
    const heureGeneration = now.toLocaleTimeString("fr-FR", {
      hour: "2-digit",
      minute: "2-digit"
    });

    const kpisHtml = [buildKPI(pivotLabel || "Trésorerie disponible", eur(tresorerie), "#2F5D50"), buildKPI("Patrimoine placé", eur(totalPlacements), "#28394A"), buildKPI("Immobilier physique", eur(totalImmobilier), "#2F5D50"), buildKPI("Passif restant (crédits)", eur(totalCRD), "#A8503C"), buildKPI("Patrimoine net", eur(patrimoineNet), "#232A2E")].join("");

    const allocationTable = buildTable(["Classe d'actif", "Montant", "Répartition"], allocation.map(a => [a.label, eur(a.amount), `${a.pct.toFixed(1)} %`]));
    const placementTable = buildTable(["Libellé", "Catégorie", "Solde actuel", "Date de référence", "Versement mensuel"], (data.placements || []).map(p => [p.label || "(sans nom)", p.category || "—", eur(getBalance(p)), formatDateFR(p.balanceDate), p.monthly ? `${eur(p.monthly)} / mois` : "—"]));
    const loanTable = buildTable(["Crédit", "CRD actuel", "Taux (hors assurance)", "Mensualité", "Échéance"], (data.loans || []).map(l => [l.label || "(sans nom)", eur(crdToday(l)), `${((Number(l.rate) || 0) * 100).toFixed(2)} %`, eur(Number(l.monthly) || 0), formatDateFR(l.endDate)]));
    const realEstateTable = buildTable(["Bien", "Type", "Valeur estimée", "Année d'estimation", "Revalorisation"], (data.realEstate || []).map(r => [r.label || "(sans nom)", r.type || "—", eur(Number(r.currentValue) || 0), r.valuationYear || "—", `+${((Number(r.annualGrowthRate) || 0) * 100).toFixed(1)} % / an`]));

    return `<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="UTF-8">
<title>Bilan patrimonial</title>
<style>
  @page { size: A4; margin: 16mm 14mm; }
  * { box-sizing: border-box; }
  body {
    font-family: Georgia, 'Times New Roman', serif;
    color: #232A2E;
    margin: 0;
    padding: 0;
    background: #FFFFFF;
  }
  h1 {
    font-size: 22px;
    margin: 0 0 4px 0;
    color: #2F5D50;
  }
  .report-subtitle {
    font-size: 12px;
    color: #6B7278;
    margin-bottom: 20px;
    font-family: Arial, sans-serif;
  }
  .report-kpis {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    margin-bottom: 24px;
  }
  .report-kpi {
    flex: 1 1 150px;
    border: 1px solid #DED6C4;
    border-radius: 6px;
    padding: 10px 12px;
    background: #F6F3EC;
  }
  .report-kpi-label {
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.4px;
    color: #6B7278;
    font-family: Arial, sans-serif;
    margin-bottom: 4px;
  }
  .report-kpi-value {
    font-size: 16px;
    font-weight: bold;
    font-family: 'Courier New', monospace;
  }
  h2 {
    font-size: 14px;
    color: #232A2E;
    border-bottom: 1px solid #DED6C4;
    padding-bottom: 4px;
    margin: 26px 0 10px 0;
  }
  .report-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 11px;
    font-family: Arial, sans-serif;
    margin-bottom: 6px;
  }
  .report-table th, .report-table td {
    border: 1px solid #DED6C4;
    padding: 5px 8px;
    text-align: left;
  }
  .report-table th {
    background: #EFEAE0;
    font-weight: bold;
  }
  .report-table tr {
    break-inside: avoid;
  }
  .report-empty {
    font-size: 11px;
    color: #6B7278;
    font-style: italic;
    font-family: Arial, sans-serif;
  }
  .report-footer {
    margin-top: 30px;
    font-size: 9.5px;
    color: #6B7278;
    font-family: Arial, sans-serif;
    border-top: 1px solid #DED6C4;
    padding-top: 8px;
  }
  section {
    break-inside: avoid-page;
  }
  @media print {
    body { padding: 0; }
  }
</style>
</head>
<body>
  <h1>Bilan Patrimonial</h1>
  <div class="report-subtitle">Généré le ${dateGeneration} à ${heureGeneration} — MyFamilyBudget (document indicatif, hors valeurs de marché temps réel)</div>

  <div class="report-kpis">${kpisHtml}</div>

  <section>
    <h2>Répartition d'actifs</h2>
    ${allocationTable}
  </section>

  <section>
    <h2>Placements &amp; comptes</h2>
    ${placementTable}
  </section>

  <section>
    <h2>Crédits &amp; passif</h2>
    ${loanTable}
  </section>

  <section>
    <h2>Immobilier physique</h2>
    ${realEstateTable}
  </section>

  <div class="report-footer">
    Ce document est généré localement dans votre navigateur à partir des données saisies dans MyFamilyBudget. Les valeurs de placements et d'immobilier sont des estimations déclaratives, non connectées à un flux bancaire ou boursier en temps réel.
  </div>
</body>
</html>`;
  }

  /**
   * Ouvre le bilan patrimonial dans un nouvel onglet et déclenche la boîte
   * d'impression du navigateur (l'utilisateur choisit "Enregistrer en PDF").
   */
  function exportPatrimoineReportPDF(data, opts) {
    const html = buildPatrimoineReportHTML(data, opts);
    const printWindow = window.open("", "_blank", "width=900,height=1000");
    if (!printWindow) {
      window.alert("Le navigateur a bloqué l'ouverture de la fenêtre d'impression. Autorisez les pop-ups pour cette page puis réessayez.");
      return;
    }
    printWindow.document.open();
    printWindow.document.write(html);
    printWindow.document.close();
    printWindow.focus();
    // Laisse le temps au navigateur de mettre en page le document avant d'ouvrir la boîte d'impression
    printWindow.setTimeout(() => {
      printWindow.print();
    }, 300);
  }

  exports.buildPatrimoineReportHTML = buildPatrimoineReportHTML;
  exports.exportPatrimoineReportPDF = exportPatrimoineReportPDF;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);
