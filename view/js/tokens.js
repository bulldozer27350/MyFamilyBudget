/**
 * Tokens de design & fonctions utilitaires de formatage
 */
(function (exports) {
  'use strict';

  const C = {
    paper: "#F6F3EC",
    panel: "#FFFFFF",
    panelAlt: "#EFEAE0",
    ink: "#232A2E",
    inkSoft: "#6B7278",
    line: "#DED6C4",
    pine: "#2F5D50",
    pineSoft: "#E3ECE8",
    brick: "#A8503C",
    brickSoft: "#F4E4DF",
    gold: "#93802E",
    goldSoft: "#F0EAD3",
    navy: "#28394A"
  };
  const fmt0 = new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 0
  });
  const eur = v => fmt0.format(Number(v) || 0);
  const fmt2 = new Intl.NumberFormat("fr-FR", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  // Montant exact (2 décimales) — réservé aux transactions individuelles (relevés bancaires)
  const eurExact = v => fmt2.format(Number(v) || 0);
  const uid = () => Math.random().toString(36).slice(2, 10);
  const yearOf = iso => iso ? new Date(iso).getFullYear() : null;

  // Exposition globale & modulaire
  exports.C = C;
  exports.fmt0 = fmt0;
  exports.eur = eur;
  exports.fmt2 = fmt2;
  exports.eurExact = eurExact;
  exports.uid = uid;
  exports.yearOf = yearOf;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);