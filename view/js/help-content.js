/**
 * Base de connaissances & Aide en Ligne contextuelle (HELP_CONTENT)
 */
(function (exports) {
  'use strict';

  const HELP_CONTENT = {
    overview: {
      title: "Onglet « Vue d'ensemble »",
      summary: "Tableau de bord général agrégeant votre trésorerie, vos placements, vos crédits et vos jauges d'indépendance financière.",
      sections: [{
        id: "kpis",
        title: "KPIs & Indicateurs clés",
        content: `
            - **Trésorerie de départ** : Solde initial configuré dans l'onglet *Paramètres*.
            - **Patrimoine placé actuel** : Somme des soldes actuels de l'ensemble de vos placements (à la date de saisie).
            - **Flux net — année en cours** : Solde annuel (Revenus − Charges) calculé par le moteur de projection.
            - **Année de retraite visée** : Calculée via \`Année de naissance + Âge de départ visé\`.
          `
      }, {
        id: "graphique_tresorerie",
        title: "Graphique — Filtrage des courbes & Navigation temporelle",
        content: `
            Le graphique **Trésorerie disponible cumulée, Passif & Patrimoine Net** superpose plusieurs courbes : total des avoirs, passif (CRD des crédits), patrimoine net et trésorerie disponible, ainsi qu'une courbe par placement.

            **Filtrer les courbes :**
            - Cliquer sur une courbe (bouton ou étiquette de légende) quand **toutes sont visibles** → l'**isole** (les autres se masquent).
            - Cliquer sur une courbe **masquée** → l'**ajoute** à l'affichage.
            - Cliquer sur une courbe **visible parmi plusieurs** → la **masque**.
            - Cliquer sur la dernière courbe visible → **revient à tout afficher**.
            - Le bouton **Tous** remet toujours l'ensemble des courbes en un clic.

            **Naviguer dans le temps :**
            - 🖱️ **Molette** : zoom in/out. L'échelle s'adapte automatiquement (> 3 ans → vue annuelle ; 2 mois–3 ans → mensuelle ; < 2 mois → journalière).
            - ↔️ **Glisser** : déplacez-vous dans le temps en cliquant-glissant sur le graphique.
            - **Boutons rapides** : Tout / 10 ans / 5 ans / 1 an pour recadrer instantanément la période visible.
          `
      }, {
        id: "journal",
        title: "Journal des mouvements & Moteur Détaillé (Jour par Jour)",
        badgeId: "journal_mouvements",
        content: `
            Le graphique de trésorerie disponible et le **Journal des mouvements** sont alimentés par le **moteur détaillé jour par jour**. 
            C'est le moteur de référence de l'outil :
            - Il simule réellement la circulation de la trésorerie sur votre compte courant.
            - Il exécute les **virements automatiques** vers/depuis vos livrets et placements selon vos seuils (sweep).
            - Il gère le **mécanisme de pause des versements d'épargne** en cas d'imprévu.

            💡 **Recherche & Tri** : Vous pouvez filtrer chaque colonne en tapant un mot-clé sous l'en-tête, et trier les colonnes d'un simple clic.
          `
      }, {
        id: "fire",
        title: "Jauges FIRE & Indépendance Financière (Règle des 4 %)",
        badgeId: "fire_gauge",
        content: `
            La règle des 4 % estime si votre patrimoine accumulé à la retraite permettra de couvrir vos charges sans épuiser votre capital.

            **1. Jauges d'Indépendance Financière (Liquidation totale)** :
            \`Rente mensuelle = (Patrimoine Placé + Actif Immobilier physique) × 4 % / 12\`
            *⚠️ Attention : cette jauge inclut la valeur théorique de vos biens immobiliers. Or, votre résidence principale n'est pas un actif liquide produisant une rente sans vente ou viager.*

            **2. Jauge de Couverture réaliste (Pensions + Placements seuls)** :
            \`Rente mensuelle = (Pensions de retraite estimées) + (Placements financiers mobilisables × 4 % / 12)\`
            *Cette seconde jauge exclut la résidence principale et les comptes de tiers (ex. enfants) pour comparer vos revenus réellement disponibles à vos charges.*

            💡 *Ces deux jauges restent des photos à l'année du départ. Pour voir si ça tient dans la durée (pas seulement au premier jour), consultez la courbe de trésorerie de la Vue d'ensemble après avoir réglé "Simuler jusqu'à l'âge de" dans Paramètres — les pensions y sont injectées automatiquement comme un revenu réel.*
          `
      }, {
        id: "allocation",
        title: "Répartition d'actifs (Asset Allocation)",
        badgeId: "asset_allocation",
        content: `
            Ventile votre patrimoine placé en 6 classes d'actifs (Cash, Fonds en euros, Immobilier/SCPI, Actions, Obligations, Épargne salariale/PER).

            ⚠️ **Important** : Le classement dépend de la **catégorie** associée à chaque placement (page *Placements*) et de sa table de correspondance (page *Paramètres*). Tout placement avec une catégorie inconnue est classé par sécurité en Cash avec un avertissement.
          `
      }]
    },
    cashflow: {
      title: "Onglet « Trésorerie »",
      summary: "Gestion des revenus récurrents, charges, primes, dépenses ponctuelles et estimation prévisionnelle.",
      sections: [{
        id: "simplifie_vs_detaille",
        title: "Moteur Simplifié vs Moteur Détaillé",
        badgeId: "moteur_simplifie",
        content: `
            ⚠️ **Deux moteurs de calcul coexistent dans l'outil** :
            - **Le Moteur Simplifié (Année par Année)** : Alimente la courbe de cet onglet (*Évolution de la trésorerie*). Il fait une simple addition/soustraction annuelle sans simuler les virements automatiques. Il sert d'indicateur théorique (« que deviendrait ma trésorerie si aucun virement automatique n'était fait ? »).
            - **Le Moteur Détaillé (Jour par Jour)** : Alimente la Vue d'ensemble. C'est la réalité simulée exacte avec écrêtage et rapatriement.
          `
      }, {
        id: "revenus_charges",
        title: "Revenus & Charges Récurrentes (Règle d'indexation)",
        badgeId: "charges_indexation",
        content: `
            - **Revenus** : Définis par un montant mensuel, une date de début et de fin. Chaque année, la formule applique le taux d'augmentation annuel saisi.
            - **Charges & Inflation** : 💡 **Règle fondamentale** : Une charge sans taux de croissance explicitement saisi **suit automatiquement le taux d'inflation général** configuré dans *Paramètres*. Pour fixer une charge sans augmentation, vous devez saisir **0 %** explicitement.
          `
      }, {
        id: "primes",
        title: "Primes, Participation & Intéressement",
        badgeId: "primes_var",
        content: `
            Les primes et participations appliquent un pourcentage à un **revenu de référence** choisi dans la liste des revenus récurrents.
            - **Valeurs réelles constatées** : Dès que vous connaissez le montant réel d'une année donnée, saisissez-le dans le tableau des ajustements réels : il remplacera la prévision calculée pour cette année unique.
          `
      }]
    },
    patrimoine: {
      title: "Onglet « Placements & Patrimoine »",
      summary: "Gestion fine de vos comptes, livrets, assurances-vie, SCPI, crédits et actifs immobiliers.",
      sections: [{
        id: "drawer",
        title: "Fiche détaillée d'un placement & 3 Scénarios",
        badgeId: "placement_drawer",
        content: `
            En cliquant sur une carte de placement, vous ouvrez sa fiche en 4 volets :
            1. **Infos Générales** : Libellé, catégorie d'actif, notes.
            2. **Solde & Versements** : Solde actuel et versement mensuel programmé (soumis aux règles de pause).
            3. **Rendements (3 Scénarios)** : Définissez un taux d'intérêt annuel composé pour les hypothèses *Pessimiste*, *Correct* et *Optimiste*.
            4. **Automatisations (Sweep & Pause)** : Réglage du virement automatique et des seuils d'alerte.
          `
      }, {
        id: "sweep_pause",
        title: "Virement Automatique (Sweep) & Pause Épargne",
        badgeId: "sweep_pause_help",
        content: `
            **Virement Automatique (Sweep)** :
            - **Priorité virement auto** : Ordre d'utilisation du compte (ex. 1 pour le Livret A, 2 pour le LDD). Le compte de priorité 1 reçoit l'excédent de trésorerie en premier et est ponctionné en premier si le compte courant manque de fonds.
            - **Plafond virement auto** : Montant max qu'il peut recevoir via le virement automatique (ex. 22 950 €).

            **Pause Épargne (Coups durs)** :
            - **Seuil d'alerte tampon (€)** : Si le solde de ce compte tombe sous ce seuil, une alerte est déclenchée.
            - **Priorité pause épargne** : En cas de tension de trésorerie ou d'alerte, les versements mensuels des placements dont la priorité de pause est inférieure ou égale au niveau de pause sont suspendus ce mois-là pour préserver la trésorerie.
          `
      }, {
        id: "immo_loans",
        title: "Crédits Immobiliers & Actifs Physiques",
        content: `
            - **Crédits** : Saisissez le Capital Restant Dû (CRD), le taux et les mensualités. Le CRD s'amortit au fil des mois et constitue le passif dans le calcul du patrimoine net.
            - **Immobilier Physique** : Saisissez la valeur estimée de vos biens et le taux de revalorisation annuel. Intégré dans le patrimoine net.
          `
      }]
    },
    retraite: {
      title: "Onglet « Retraite »",
      summary: "Module d'estimation approximative des pensions de retraite (Régime Général & Agirc-Arrco).",
      sections: [{
        id: "retraite_calc",
        title: "Mécanique du calcul de pension",
        content: `
            L'outil effectue un calcul approximatif à l'année de retraite visée :
            - **Régime de base** : Basé sur le salaire annuel moyen des 25 meilleures années, le nombre de trimestres validés (172 requis) et l'âge de départ (taux plein automatique à 67 ans).
            - **Régime complémentaire Agirc-Arrco** : Calculé à partir des points accumulés et acquis chaque année via le salaire brut et le ratio points/€.
            
            💡 *Note : Ce module est une estimation indicative et doit être comparé avec votre relevé officiel sur info-retraite.fr.*
          `
      }, {
        id: "retraite_injection",
        title: "Simulation de la retraite elle-même (après le départ)",
        content: `
            Les pensions calculées ci-dessus ne servent pas qu'aux jauges FIRE : elles sont **injectées automatiquement comme
            un revenu réel** dans les deux moteurs de projection (simplifié et détaillé), à partir de l'année de retraite,
            pour chaque personne de cet onglet — sans rien à saisir de plus dans la page Trésorerie, et sans jamais modifier
            vos revenus déclarés.

            - Le montant part de la pension estimée à l'année de retraite, puis se revalorise chaque année suivante au taux
              d'inflation général (Paramètres) — une approximation raisonnable de l'indexation légale des pensions.
            - Réglez **"Simuler la retraite jusqu'à l'âge de"** (onglet Paramètres) pour prolonger la projection au-delà du
              départ : la courbe de trésorerie de la Vue d'ensemble montre alors si pension + rentes suffisent à couvrir vos
              charges dans la durée, ou si le patrimoine s'épuise avant l'âge choisi.
          `
      }]
    },
    impots: {
      title: "Onglet « Impôts »",
      summary: "Modélisation simplifiée du Foyer Fiscal et du Prélèvement À la Source (PAS).",
      sections: [{
        id: "fiscalite",
        title: "Calcul de l'Impôt sur le Revenu & Régularisation",
        badgeId: "impots_pas",
        content: `
            - **Parts fiscales** : 2 parts pour un couple marié/pacsé, +0,5 part par enfant (2 premiers), +1 part à partir du 3ᵉ enfant.
            - **Abattement** : 10 % par défaut sur les revenus imposables.
            - **Taux de PAS prévisionnel** : Calculé automatiquement par application du barème progressif.
            - **Ajustement & Impôt réel** : Vous pouvez saisir le taux de PAS réel de votre fiche de paie et l'impôt réel constaté sur l'avis d'imposition. La différence déclenche une **régularisation en année N+1** dans la simulation.
          `
      }]
    },
    import: {
      title: "Onglet « Import »",
      summary: "Importez vos relevés bancaires réels pour analyser où va concrètement votre argent — à distinguer des prévisions théoriques du budget.",
      sections: [{
        id: "mapping",
        title: "Format du fichier & mapping des colonnes",
        content: `
            Chaque banque exporte différemment : le séparateur (; , tabulation), le format de date et l'ordre des colonnes
            se règlent une fois (mémorisés pour les imports suivants, modifiables si vous changez de banque).
            Après avoir choisi un fichier CSV, assignez le rôle de chaque colonne détectée (*Date*, *Libellé*, *Type*,
            *Montant*) sur l'aperçu des premières lignes. Le montant doit être signé (positif = entrée, négatif = dépense).
          `
      }, {
        id: "dedupe",
        title: "Dédoublonnage & catégorisation automatique",
        content: `
            - **Dédoublonnage intelligent** : Réimporter un fichier qui recoupe une période déjà importée ne crée aucun doublon. Une transaction identique (même date, libellé et montant) est automatiquement ignorée.
            - **Moteur de règles** : Assigner une catégorie propose un **mot-clé éditable** (ex. « ASF » au lieu de tout le libellé d'un péage). Dès que ce mot-clé apparaît dans un libellé bancaire, la transaction est classée automatiquement, y compris rétroactivement sur tout l'historique déjà importé.
          `
      }, {
        id: "categories",
        title: "Dictionnaire des catégories & Postes réductibles",
        content: `
            - **Catégories** : Créez vos propres catégories de dépenses ou de revenus.
            - **Poste réductible (compressible)** : Cochez *« Oui »* sur les catégories non indispensables (loisirs, abonnements superflus, sorties). L'outil isole automatiquement ces dépenses dans les indicateurs d'analyse pour mesurer votre marge de manœuvre en cas d'économie souhaitée.
          `
      }]
    },
    pending: {
      title: "Onglet « Opérations en cours »",
      summary: "Suivez vos chèques émis, CB différées, débits engagés et salaires/crédits en attente. Calculez votre solde réel disponible sans polluer le budget prévisionnel.",
      sections: [{
        id: "solde_reel",
        title: "Solde Réellement Disponible vs Solde Banque",
        content: `
            - **Solde Banque (Relevé)** : L'argent comptabilisé par la banque lors du dernier relevé importé.
            - **Chèques & Débits en cours** : Les sommes que vous avez déjà engagées (chèque signé chez le médecin ou l'artisan, paiement en CB différée) mais que la banque n'a pas encore débitées.
            - **Crédits en attente** : Les rentrées connues (ex. bulletin de salaire reçu, virement validé) pas encore portées au crédit.
            - **Solde Disponible Réel** : \`Solde Banque + Débits engagés + Crédits en attente\`. C'est le véritable montant dont vous disposez sans risquer de découvert imprévu.
          `
      }, {
        id: "navigation_mois",
        title: "Navigation mensuelle & Reliquats des mois passés",
        content: `
            - **Vue par mois** : La navigation temporelle (◄ Mois Année ►) permet de saisir et consulter les opérations du mois sans jamais accumuler un tableau infini.
            - **Suivi des chèques anciens (Reliquats)** : Si un chèque émis en janvier n'est toujours pas débité en juin, un bandeau d'alerte apparaît automatiquement en juin pour vous rappeler son existence et vous permettre de le pointer dès son débit, sans devoir remonter dans l'historique passé.
          `
      }, {
        id: "pre_remplissage",
        title: "Pré-remplissage depuis le budget",
        content: `
            Lors de l'ajout d'une opération, le menu *« ⚡ Pré-remplir depuis le budget »* permet de sélectionner une charge récurrente (ex. *Courses*), un revenu (ex. *Salaire*) ou une dépense ponctuelle.
            Tous les champs pré-remplis (libellé, montant, catégorie, type) restent **100% modifiables**.
          `
      }, {
        id: "import_cb",
        title: "Import des encours de CB différées & Détection intelligente des doublons",
        content: `
            - **Bouton « 📥 Importer encours CB »** : Permet de charger directement un fichier CSV exporté depuis votre banque contenant le relevé d'encours de vos cartes à débit différé.
            - **Détection automatique du format** : Le séparateur (\`;\`, \`,\`, tabulation) et les colonnes (*Date*, *Libellé*, *Montant*) sont détectés automatiquement avec prévisualisation.
            - **Gestion des dates** : Vous pouvez choisir d'enregistrer l'opération à la date de débit fin de mois ou d'extraire la date réelle d'achat présente dans le libellé bancaire (ex. *FACTURE CARTE DU 290726*).
            - **Règles de catégorisation partagées** : Les règles créées dans l'onglet *Import* sont appliquées immédiatement pour classer vos dépenses (ex. courses, carburant, abonnements).
            - **Dédoublonnage intelligent & Fusion des saisies manuelles** :
              - Les opérations strictement identiques (même date, libellé, montant) sont automatiquement ignorées.
              - **Rattrapage & Fusion** : Si vous aviez préalablement saisi une dépense manuellement, l'outil détecte les correspondances candidates sur la **date de la dépense (à ±1 jour près)** et le **montant (à ±10 € près)**, sans exiger un libellé identique.
              - Une boîte de dialogue vous propose alors de **fusionner** la ligne (les informations officielles du relevé font foi tout en **conservant votre catégorie manuelle**), de l'**importer comme nouvelle opération** ou de l'**ignorer**.
          `
      }, {
        id: "rapprochement",
        title: "Rapprochement bancaire (Manuel & Automatique)",
        content: `
            - **Manuel** : Cliquez sur *🔗 Pointer* sur une ligne pour ouvrir le volet de sélection des transactions bancaires importées. Les correspondances de montant exact ou de numéro de chèque sont suggérées en tête de liste.
            - **Automatique** : Le bouton *⚡ Rapprochement automatique* analyse les libellés bancaires (numéros de chèque) et les montants uniques pour lier les opérations en un seul clic.
            - **Cohérence des catégories** : l'opération en attente et la transaction bancaire importée sont deux représentations de la même donnée. Si elles portent exactement la même catégorisation (catégorie ou ventilation/splits), le rapprochement se fait sans question. Sinon — catégorisations différentes, ou un seul des deux côtés catégorisé — l'application ne tranche jamais à votre place :
              - En rapprochement manuel, une fenêtre *⚖️ Catégorisation différente* vous laisse choisir laquelle des deux conserver ; l'autre est alignée dessus (une ventilation choisie reste une ventilation, jamais aplatie en catégorie unique).
              - En rapprochement automatique, ces paires en conflit sont laissées de côté (opération toujours *« en attente »*) et comptabilisées dans le message de synthèse — traitez-les ensuite une par une via *🔗 Pointer*.
          `
      }]
    },
    pointage: {
      title: "Onglet « Pointage & Rapprochement »",
      summary: "Rapprochez vos transactions bancaires réelles avec les charges, revenus et versements d'épargne prévus au budget.",
      sections: [{
        id: "pointage_usage",
        title: "Fonctionnement du pointage mensuel & KPIs",
        content: `
            Pour chaque mois, l'outil liste vos charges, revenus et versements programmés actifs.
            
            **4 indicateurs en tête de page** :
            - **Budget prévu** : Total des prévisions budgétaires pour le mois.
            - **Débits en banque** : Somme totale des sorties relevées sur votre compte bancaire ce mois-ci.
            - **Pointé sur budget** : Somme des transactions associées aux lignes budgétaires.
            - **Dépenses non pointées** : Total des mouvements bancaires du mois qui n'ont pas encore été rattachés au budget (avec modal de visualisation directe).
          `
      }, {
        id: "pointage_statuts",
        title: "Statuts & Tolérance de conformité",
        content: `
            - 🔵 **Conforme** : Le montant pointé correspond au prévisionnel (tolérance de ±2 % ou 1 €).
            - 🟢 **Économie / Surplus** : Dépense réelle inférieure au budget prévu, ou revenu réel supérieur aux prévisions.
            - 🔴 **Dépassement / Déficit** : Dépense supérieure au budget, ou revenu inférieur.
            - 🟡 **En attente** : Aucune transaction bancaire n'a encore été associée à cette ligne.
          `
      }, {
        id: "pointage_association",
        title: "Association manuelle & Rapprochement automatique",
        content: `
            - **🔗 Pointer (Volet latéral)** : Cliquez pour afficher les transactions du mois. Vous pouvez filtrer par la catégorie liée ou afficher toutes les transactions, et associer plusieurs débits à une même ligne de budget (ex. 4 tickets de supermarché sur la ligne *Courses*).
            - **⚡ Rapprochement automatique** : Associe en un clic toutes les transactions non pointées aux lignes budgétaires partageant la même catégorie.
          `
      }]
    },
    analyse: {
      title: "Onglet « Analyse »",
      summary: "Tableau de bord d'analyse du réel : moyennes mensuelles, atterrissage du mois en cours, historique des flux et dérives par rapport au budget.",
      sections: [{
        id: "vue_generale",
        title: "1. Vue Générale & Postes Compressibles",
        content: `
            - **Périodes d'analyse** : Choisissez d'observer vos données réelles sur 3 mois, 6 mois, 12 mois ou l'ensemble de l'historique importé.
            - **Moyennes mensuelles** : Revenus moyens, dépenses moyennes, épargne nette et taux d'épargne réel.
            - **Postes réductibles** : Mesure la part exacte de vos dépenses consacrée aux catégories marquées « compressibles » pour identifier rapidement votre potentiel d'économie.
          `
      }, {
        id: "atterrissage",
        title: "2. Atterrissage du Mois",
        content: `
            Compare pour chaque catégorie du mois en cours :
            - Le **Budget prévisionnel**.
            - Le **Réel pointé** (validé sur les lignes de budget).
            - Les **Débits bruts en banque** et l'écart d'atterrissage estimé en fin de mois.
          `
      }, {
        id: "historique_mensuel",
        title: "3. Historique Mensuel",
        content: `
            Graphique et tableau chronologique retraçant mois après mois vos entrées réelles, vos sorties réelles et le flux net généré. Permet de repérer les saisonnalités (vacances, impôts, primes).
          `
      }, {
        id: "derives_lignes",
        title: "4. Dérives par Ligne Budgétaire",
        content: `
            Met en regard le montant budgété de chaque charge/revenu avec sa moyenne réelle constatée sur 3 mois et 12 mois.
            Met en évidence les dérives structurelles (ex. une facture d'énergie ou un abonnement ayant augmenté sans que le budget n'ait été réévalué).
          `
      }]
    },
    settings: {
      title: "Onglet « Paramètres »",
      summary: "Paramétrage global, trésorerie de départ, date pivot, seuils de virement automatique et sauvegarde/importation JSON.",
      sections: [{
        id: "pivot_date",
        title: "Date Pivot & Mode de Trésorerie de Départ",
        content: `
            - **Mode Manuel** : Le moteur de simulation part de la *Trésorerie disponible de départ* que vous avez saisie manuellement.
            - **Mode Automatique (Date Pivot)** : L'outil calcule automatiquement votre trésorerie de départ exacte en sommant le solde initial et toutes les transactions bancaires importées jusqu'à la Date Pivot. Cela permet de caler la simulation prospective sur le solde réel de votre compte à une date charnière.
          `
      }, {
        id: "sweep_settings",
        title: "Seuil Haut & Seuil Bas de Trésorerie",
        badgeId: "settings_sweep",
        content: `
            - **Seuil haut (€)** : Montant maximum conservé sur le compte courant (ex. 15 000 €). Tout excédent en fin de mois est automatiquement versé sur les placements éligibles.
            - **Seuil bas (€)** : Montant minimum de sécurité (ex. 3 000 €). Si la trésorerie descend en dessous, l'outil rapatrie immédiatement la somme manquante depuis vos placements d'épargne.
          `
      }, {
        id: "simulate_until_age",
        title: "Simuler la retraite jusqu'à l'âge de",
        content: `
            Étend l'horizon de projection au-delà du simple départ en retraite (par défaut, +3 ans seulement). Combiné à
            l'injection automatique des pensions (onglet Retraite), ça permet de voir, sur la courbe de trésorerie de la Vue
            d'ensemble, si votre patrimoine tient jusqu'à l'âge choisi ou s'épuise avant — pas seulement une photo au jour
            du départ.
          `
      }, {
        id: "storage_json",
        title: "Sauvegarde & Portabilité (100% local)",
        content: `
            L'outil fonctionne **entièrement dans votre navigateur**, sans aucun serveur ni base de données distante.
            - **Sauvegarde automatique** : Vos modifications sont conservées dans le \`localStorage\` du navigateur.
            - **Exportation JSON** : Télécharge une copie du fichier de données sur votre ordinateur. C'est votre sauvegarde principale.
            - **Importation JSON** : Permet de recharger votre budget sur n'importe quel ordinateur ou navigateur en ouvrant ce simple fichier HTML.
          `
      }]
    }
  };
  exports.HELP_CONTENT = HELP_CONTENT;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);