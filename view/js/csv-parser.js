/**
 * Parseur CSV, gestion des dates/montants et règles de catégorisation
 */
(function (exports) {
  'use strict';

  /**
   * Parseur CSV générique et tolérant : gère un séparateur configurable, les champs entre
   * guillemets (avec séparateur ou retour à la ligne à l'intérieur), et les fins de ligne
   * Windows/Unix. Ne dépend d'aucune bibliothèque externe.
   */
  function parseCSVText(text, delimiter) {
    const rows = [];
    let row = [],
      field = "",
      inQuotes = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (inQuotes) {
        if (c === '"') {
          if (text[i + 1] === '"') {
            field += '"';
            i++;
          } else inQuotes = false;
        } else field += c;
      } else if (c === '"') {
        inQuotes = true;
      } else if (c === delimiter) {
        row.push(field);
        field = "";
      } else if (c === "\n") {
        row.push(field);
        rows.push(row);
        row = [];
        field = "";
      } else if (c === "\r") {
        // ignoré, géré par le \n qui suit
      } else {
        field += c;
      }
    }
    if (field.length || row.length) {
      row.push(field);
      rows.push(row);
    }
    return rows.filter(r => r.some(cell => (cell || "").trim() !== ""));
  }

  /**
   * Convertit une date texte selon un format configurable (ex. "DD/MM/YYYY", "YYYY-MM-DD") en ISO YYYY-MM-DD.
   */
  function parseDateWithFormat(str, format) {
    if (!str) return null;
    const cleaned = String(str).trim();
    const parts = cleaned.split(/[\/\-\.]/);
    const order = (format || "DD/MM/YYYY").split(/[\/\-\.]/);
    if (parts.length !== 3 || order.length !== 3) return null;
    let d, m, y;
    order.forEach((token, idx) => {
      const v = parseInt(parts[idx], 10);
      if (isNaN(v)) return;
      if (token[0] === "D") d = v;else if (token[0] === "M") m = v;else if (token[0] === "Y") y = v < 100 ? 2000 + v : v;
    });
    if (!d || !m || !y) return null;
    return `${y}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  }

  /**
   * Convertit un montant texte (virgule ou point décimal, espaces/symbole monétaire, signé) en nombre.
   */
  function parseAmountText(str) {
    if (str === undefined || str === null) return 0;
    let s = String(str).trim().replace(/[€\s]/g, "");
    if (s.includes(",") && s.includes(".")) {
      if (s.lastIndexOf(",") > s.lastIndexOf(".")) s = s.replace(/\./g, "").replace(",", ".");else s = s.replace(/,/g, "");
    } else if (s.includes(",")) {
      s = s.replace(",", ".");
    }
    const v = parseFloat(s);
    return isNaN(v) ? 0 : v;
  }

  /**
   * Clé de dédoublonnage d'une transaction — évite de réimporter deux fois la même ligne
   */
  function transactionDedupeKey(t) {
    return `${t.date}|${(t.label || "").trim().toLowerCase()}|${Math.round((Number(t.amount) || 0) * 100)}`;
  }

  /**
   * Mot-clé de règle dérivé d'un libellé
   */
  function ruleKeyFromLabel(label) {
    return (label || "").replace(/[0-9]/g, "").replace(/\s+/g, " ").trim().toUpperCase();
  }

  /**
   * Applique les règles de catégorisation existantes à une liste de transactions
   */
  function applyRulesToTransactions(transactions, rules) {
    const activeRules = (rules || []).filter(r => r.matchText && r.matchText.trim() !== "");
    return transactions.map(t => {
      if (t.categoryId) return t;
      const label = (t.label || "").toUpperCase();
      const match = activeRules.find(r => label.includes(r.matchText.trim().toUpperCase()));
      return match ? {
        ...t,
        categoryId: match.categoryId
      } : t;
    });
  }
  exports.parseCSVText = parseCSVText;
  exports.parseDateWithFormat = parseDateWithFormat;
  exports.parseAmountText = parseAmountText;
  exports.transactionDedupeKey = transactionDedupeKey;
  exports.ruleKeyFromLabel = ruleKeyFromLabel;
  exports.applyRulesToTransactions = applyRulesToTransactions;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);