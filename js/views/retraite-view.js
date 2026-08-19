/**
 * Vue Retraite (RetraiteView & PersonRetraitePanel : Estimation Régime Général & Agirc-Arrco)
 */
(function (exports) {
  'use strict';

  const {
    useState,
    useMemo
  } = React;
  const {
    C,
    eur,
    uid
  } = exports.C ? exports : window.BudgetApp || {};
  const {
    computeRetirementProjection
  } = exports.computeRetirementProjection ? exports : window.BudgetApp || {};
  const {
    SectionCard,
    EditableTable,
    IconBtn,
    KPI
  } = exports.SectionCard ? exports : window.BudgetApp || {};
  function PersonRetraitePanel({
    data,
    update,
    person,
    retireYear
  }) {
    const inputStyle = {
      border: `1px solid ${C?.line || "#DED6C4"}`,
      borderRadius: 7,
      padding: "8px 10px",
      fontSize: 14,
      width: 140
    };
    const proj = useMemo(() => {
      const fn = exports.computeRetirementProjection || window.BudgetApp && window.BudgetApp.computeRetirementProjection || computeRetirementProjection;
      return fn(data, person, retireYear);
    }, [data, person, retireYear]);
    const incomeOptions = useMemo(() => [{
      value: "",
      label: "— Choisir un revenu lié —"
    }, ...(data?.incomes || []).map(i => ({
      value: i.label,
      label: i.label
    }))], [data?.incomes]);
    const updatePerson = fn => {
      update("retirement", r => ({
        ...r,
        people: (r?.people || []).map(p => p.id === person.id ? fn(p) : p)
      }));
    };
    return /*#__PURE__*/React.createElement("div", {
      style: {
        marginBottom: 24
      }
    }, !person.incomeLabel && /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.brickSoft || "#F4E4DF",
        border: `1px solid ${C?.brick || "#A8503C"}`,
        borderRadius: 8,
        padding: "10px 14px",
        fontSize: 12.5,
        color: C?.brick || "#A8503C",
        marginBottom: 14
      }
    }, "Aucun revenu lié à cette personne — la projection des salaires futurs sera nulle. Choisissez une ligne de revenu ci-dessous (page Trésorerie)."), proj.manqueTauxPlein && /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.goldSoft || "#F0EAD3",
        border: `1px solid ${C?.gold || "#93802E"}`,
        borderRadius: 8,
        padding: "10px 14px",
        fontSize: 12.5,
        color: "#6B5A1E",
        marginBottom: 14
      }
    }, "⚠️ À ", retireYear, ", ", person.name || "cette personne", " n'atteindrait que ", /*#__PURE__*/React.createElement("strong", null, proj.trimestresEstimesDepart), " trimestres sur les ", proj.trimestresRequis, " requis — une décote d'environ ", /*#__PURE__*/React.createElement("strong", null, (proj.decote * 100).toFixed(1), "%"), " s'appliquerait (taux retenu : ", (proj.tauxAppliqué * 100).toFixed(2), "% au lieu de 50%)."), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 14,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement(KPI, {
      label: "Trimestres estimés au départ",
      value: `${proj.trimestresEstimesDepart} / ${proj.trimestresRequis}`,
      accent: proj.manqueTauxPlein ? C?.brick || "#A8503C" : C?.pine || "#2F5D50",
      sub: `Validés à ce jour : ${proj.trimestresValides}`
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "SAM estimé (25 dernières années)",
      value: eur(proj.SAM),
      sub: "Plafonné au PASS, hors revalorisation fine"
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Pension de base / mois",
      value: eur(proj.pensionBaseAnnuelle / 12),
      accent: C?.navy || "#28394A",
      sub: `Taux ${(proj.tauxAppliqué * 100).toFixed(2)}%${proj.majoration > 1 ? " · +10% (3 enfants)" : ""}`
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Agirc-Arrco / mois",
      value: eur(proj.pensionComplementaireAnnuelle / 12),
      accent: C?.navy || "#28394A",
      sub: `${proj.pointsEstimes.toFixed(0)} pts × ${proj.valeurPointDepart.toFixed(4)} €`
    }), /*#__PURE__*/React.createElement(KPI, {
      label: "Total pension brute / mois",
      value: eur(proj.pensionTotaleMensuelle),
      accent: C?.pine || "#2F5D50",
      sub: "Brut — hors prélèvements sociaux (~ -9 à -10%)"
    })), /*#__PURE__*/React.createElement(SectionCard, {
      title: "Identité & réglages carrière",
      subtitle: "Renseignez ces éléments à partir du relevé de carrière (info-retraite.fr) pour fiabiliser l'estimation"
    }, /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 24,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Nom"), /*#__PURE__*/React.createElement("input", {
      type: "text",
      value: person.name,
      onChange: e => updatePerson(p => ({
        ...p,
        name: e.target.value
      })),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Revenu lié (page Trésorerie)"), /*#__PURE__*/React.createElement("select", {
      value: person.incomeLabel || "",
      onChange: e => updatePerson(p => ({
        ...p,
        incomeLabel: e.target.value
      })),
      style: {
        ...inputStyle,
        width: 220,
        cursor: "pointer"
      }
    }, incomeOptions.map(o => /*#__PURE__*/React.createElement("option", {
      key: o.value,
      value: o.value
    }, o.label)))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Année de naissance"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      value: person.birthYear,
      onChange: e => updatePerson(p => ({
        ...p,
        birthYear: e.target.value
      })),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("label", {
      style: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        cursor: "pointer",
        marginTop: 20
      }
    }, /*#__PURE__*/React.createElement("input", {
      type: "checkbox",
      checked: !!person.cadre,
      onChange: e => updatePerson(p => ({
        ...p,
        cadre: e.target.checked
      }))
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 12.5,
        color: C?.ink || "#232A2E",
        fontWeight: 600
      }
    }, "Statut cadre"))), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 24,
        flexWrap: "wrap",
        marginBottom: 18
      }
    }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Trimestres validés à ce jour"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      value: person.trimestresValides,
      onChange: e => updatePerson(p => ({
        ...p,
        trimestresValides: e.target.value
      })),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Date du relevé"), /*#__PURE__*/React.createElement("input", {
      type: "date",
      value: person.trimestresDate,
      onChange: e => updatePerson(p => ({
        ...p,
        trimestresDate: e.target.value
      })),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Points Agirc-Arrco actuels"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      step: "0.01",
      value: person.agircPoints,
      onChange: e => updatePerson(p => ({
        ...p,
        agircPoints: e.target.value
      })),
      style: inputStyle
    })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 6
      }
    }, "Ratio points/€ (acquisition future)"), /*#__PURE__*/React.createElement("input", {
      type: "number",
      step: "0.0001",
      value: person.ratioPointsParEuro,
      onChange: e => updatePerson(p => ({
        ...p,
        ratioPointsParEuro: e.target.value
      })),
      style: inputStyle
    }))), /*#__PURE__*/React.createElement("div", {
      style: {
        fontFamily: "'Newsreader', serif",
        fontSize: 15,
        color: C?.ink || "#232A2E",
        fontWeight: 600,
        marginBottom: 10
      }
    }, "Historique des salaires bruts annuels"), /*#__PURE__*/React.createElement("div", {
      style: {
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 12
      }
    }, "Saisissez librement les années souhaitées (issues du relevé de carrière). Les années futures sont projetées automatiquement."), /*#__PURE__*/React.createElement(EditableTable, {
      columns: [{
        key: "year",
        label: "Année",
        type: "number"
      }, {
        key: "salary",
        label: "Salaire brut annuel (€)",
        type: "number",
        align: "right"
      }],
      rows: person.salaryHistory || [],
      onCell: (id, field, value) => updatePerson(p => ({
        ...p,
        salaryHistory: (p.salaryHistory || []).map(r => r.id === id ? {
          ...r,
          [field]: value
        } : r)
      })),
      onRemove: id => updatePerson(p => ({
        ...p,
        salaryHistory: (p.salaryHistory || []).filter(r => r.id !== id)
      })),
      onAdd: () => updatePerson(p => ({
        ...p,
        salaryHistory: [...(p.salaryHistory || []), {
          id: uid(),
          year: new Date().getFullYear(),
          salary: 0
        }]
      }))
    })));
  }
  function RetraiteView({
    data,
    update,
    retireYear
  }) {
    const people = data?.retirement?.people || [];
    const [activeId, setActiveId] = useState(people[0]?.id || null);
    const activePerson = people.find(p => p.id === activeId) || people[0] || null;
    const addPerson = () => {
      const newPerson = {
        id: uid(),
        name: "Nouvelle personne",
        incomeLabel: "",
        birthYear: data?.settings?.birthYear || "",
        cadre: false,
        trimestresValides: 0,
        trimestresDate: "",
        agircPoints: 0,
        ratioPointsParEuro: 0.0051,
        salaryHistory: []
      };
      update("retirement", r => ({
        ...r,
        people: [...(r?.people || []), newPerson]
      }));
      setActiveId(newPerson.id);
    };
    const removePerson = id => {
      update("retirement", r => ({
        ...r,
        people: (r?.people || []).filter(p => p.id !== id)
      }));
      if (activeId === id) setActiveId(null);
    };
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("div", {
      style: {
        background: C?.panelAlt || "#EFEAE0",
        border: `1px solid ${C?.line || "#DED6C4"}`,
        borderRadius: 8,
        padding: "10px 14px",
        fontSize: 12,
        color: C?.inkSoft || "#6B7278",
        marginBottom: 18
      }
    }, "Estimation indicative (régime général + Agirc-Arrco), à recaler avec une simulation officielle sur", " ", /*#__PURE__*/React.createElement("strong", null, "info-retraite.fr"), ". Projection à l'année de retraite visée : ", /*#__PURE__*/React.createElement("strong", null, retireYear), "."), /*#__PURE__*/React.createElement("div", {
      style: {
        display: "flex",
        gap: 8,
        marginBottom: 20,
        flexWrap: "wrap",
        alignItems: "center"
      }
    }, people.map(p => /*#__PURE__*/React.createElement("div", {
      key: p.id,
      style: {
        display: "flex",
        alignItems: "center",
        gap: 4
      }
    }, /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: () => setActiveId(p.id),
      style: {
        padding: "8px 16px",
        borderRadius: 8,
        border: `1px solid ${C?.line || "#DED6C4"}`,
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
        background: activePerson?.id === p.id ? C?.navy || "#28394A" : C?.panel || "#FFFFFF",
        color: activePerson?.id === p.id ? "#fff" : C?.ink || "#232A2E"
      }
    }, p.name || "Sans nom"), /*#__PURE__*/React.createElement(IconBtn, {
      title: "Supprimer cette personne",
      danger: true,
      onClick: () => removePerson(p.id)
    }, "✕"))), /*#__PURE__*/React.createElement("button", {
      type: "button",
      onClick: addPerson,
      style: {
        display: "flex",
        alignItems: "center",
        gap: 6,
        fontSize: 12.5,
        color: C?.pine || "#2F5D50",
        background: C?.pineSoft || "#E3ECE8",
        border: "none",
        borderRadius: 7,
        padding: "8px 14px",
        cursor: "pointer",
        fontWeight: 600
      }
    }, "+ Ajouter une personne")), activePerson ? /*#__PURE__*/React.createElement(PersonRetraitePanel, {
      data: data,
      update: update,
      person: activePerson,
      retireYear: retireYear
    }) : /*#__PURE__*/React.createElement("div", {
      style: {
        color: C?.inkSoft || "#6B7278",
        fontSize: 13
      }
    }, "Aucune personne enregistrée — cliquez sur \"+ Ajouter une personne\" pour commencer."));
  }
  exports.RetraiteView = RetraiteView;
  exports.PersonRetraitePanel = PersonRetraitePanel;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);