/**
 * Base de connaissances & Aide en Ligne contextuelle (HELP_CONTENT)
 */
(function (exports) {
  'use strict';

  const HELP_CONTENT = {
    demarrage: {
      title: "🚀 Premiers Pas — Bien démarrer avec MyFamilyBudget",
      summary: "Mini-formation en 4 étapes pour prendre en main l'outil rapidement : importer vos relevés, catégoriser, retrouver une transaction, puis pointer votre budget — sans entrer dans le détail des moteurs de calcul.",
      sections: [{
        id: "parcours",
        title: "Le parcours en 4 étapes",
        content: `
            Si c'est votre première visite, suivez cet ordre — chaque étape s'appuie sur la précédente :
            1. **Importer** vos relevés bancaires (onglet *Import*).
            2. **Catégoriser** vos transactions (onglet *Import*, dictionnaire des catégories).
            3. **Rechercher & vérifier** vos transactions dans les tableaux (onglets *Import*, *Opérations en cours*, *Vue d'ensemble*).
            4. **Pointer** votre budget prévisionnel face au réel (onglet *Pointage*).

            💡 Chaque étape est détaillée ci-dessous avec le « pourquoi » et le « comment », sans entrer dans les formules de calcul — pour ça, consultez les onglets d'aide dédiés (Trésorerie, Vue d'ensemble...) ou le Manuel utilisateur complet.
          `
      }, {
        id: "etape1_import",
        title: "Étape 1 — Importer vos relevés bancaires (CSV / Excel)",
        badgeId: "demarrage_import",
        content: `
            Rendez-vous dans l'onglet **Import**. C'est le point d'entrée de toutes vos données réelles.
            - Téléchargez d'abord l'export de vos transactions depuis le site ou l'application de votre banque, au format **CSV** ou **Excel (XLS/XLSX)** — selon ce que propose votre banque.
            - Chargez ce fichier dans l'outil : il vous demandera une seule fois comment reconnaître les colonnes de votre banque (date, libellé, montant...) — ce réglage est mémorisé pour vos prochains imports.
            - Vérifiez l'aperçu des premières lignes avant de valider : c'est le moment de corriger un éventuel décalage de colonne.
            - Pas de panique si vous réimportez une période déjà chargée : l'outil ignore automatiquement les doublons.

            🎯 **Objectif de cette étape** : avoir vos mouvements bancaires réels dans l'outil, pour pouvoir ensuite les comparer à votre budget prévisionnel.
          `
      }, {
        id: "etape2_categories",
        title: "Étape 2 — Utiliser les catégories",
        badgeId: "demarrage_categories",
        content: `
            Une fois vos transactions importées, elles arrivent « non catégorisées ». Les catégories sont le fil qui relie tout le reste de l'outil :
            - **Pourquoi catégoriser ?** C'est ce qui permet au *Pointage* de proposer des rapprochements automatiques, et à l'*Analyse* de calculer vos moyennes par poste de dépense.
            - **Comment ça va vite** : classez une transaction une fois avec un mot-clé (ex. « ASF » pour un péage) ; toutes les transactions contenant ce mot-clé, passées et futures, seront classées automatiquement.
            - **Le flag « Poste réductible »** : à cocher sur vos catégories non indispensables (loisirs, sorties...). Il sert uniquement à vous aider à repérer votre marge de manœuvre dans l'onglet *Analyse* — il ne change rien d'autre.

            🎯 **Objectif de cette étape** : qu'un maximum de vos transactions importées aient une catégorie, pour que les étapes suivantes (recherche, pointage, analyse) soient fiables.
          `
      }, {
        id: "etape3_recherche",
        title: "Étape 3 — Rechercher & trier vos transactions",
        badgeId: "demarrage_recherche",
        content: `
            Tous les grands tableaux de l'outil (transactions importées, journal des mouvements, opérations en cours...) partagent le même réflexe :
            - **Trier** : cliquez sur l'en-tête d'une colonne pour trier dessus (date, montant, libellé...).
            - **Filtrer** : tapez un mot-clé directement sous l'en-tête d'une colonne — le tableau se réduit instantanément aux lignes qui correspondent. Les filtres sont cumulables sur plusieurs colonnes à la fois.
            - Pour la colonne *Catégorie*, un menu déroulant remplace souvent la saisie libre.
            - Le compteur au-dessus du tableau indique combien de lignes correspondent à vos filtres, sur le total ; un bouton permet de tout réinitialiser en un clic.

            🎯 **Objectif de cette étape** : savoir retrouver en quelques secondes une transaction précise (ex. « tous les retraits au distributeur en mars »), sans faire défiler des centaines de lignes.
          `
      }, {
        id: "etape4_pointage",
        title: "Étape 4 — Le pointage (comparer prévu et réel)",
        badgeId: "demarrage_pointage",
        content: `
            Le **Pointage** est l'étape qui boucle la boucle : il associe chaque transaction bancaire réelle (importée à l'étape 1) à une ligne de votre budget prévisionnel (revenu, charge, versement d'épargne).
            - Commencez par le bouton **⚡ Rapprochement automatique** : il associe en un clic tout ce qui partage la même catégorie entre budget et banque.
            - Pour le reste, utilisez **🔗 Pointer** ligne par ligne : l'outil vous suggère en priorité les correspondances de montant exact.
            - Un code couleur (🔵 conforme, 🟢 économie, 🔴 dépassement, 🟡 en attente) vous dit en un coup d'œil où se trouvent les écarts à regarder.

            🎯 **Objectif de cette étape** : savoir, mois après mois, si votre réalité colle à vos prévisions — c'est la base de tous les indicateurs de l'onglet *Analyse*.

            👉 Pour le détail complet du pointage (statuts, tolérance, gestion des catégories différentes), consultez l'onglet **Pointage** de cette aide.
          `
      }]
    },
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
            2. **Versements** : Versement mensuel programmé (soumis aux règles de pause) et fenêtre d'activité. *Le solde ne se saisit plus ici — voir « Historique d'un placement » ci-dessous.*
            3. **Rendements (3 Scénarios)** : Définissez un taux d'intérêt annuel composé pour les hypothèses *Pessimiste*, *Correct* et *Optimiste*.
            4. **Automatisations (Sweep & Pause)** : Réglage du virement automatique et des seuils d'alerte.
          `
      }, {
        id: "history",
        title: "Historique d'un placement",
        badgeId: "placement_history",
        content: `
            Le bouton **📈** sur chaque carte de placement ouvre une fenêtre dédiée, séparée de la fiche d'édition pour ne pas la surcharger : c'est ici, et uniquement ici, que se saisit la valeur d'un compte ou placement.

            - **Tableau de saisie** : ajoutez une ligne à chaque relevé (mensuel, annuel, ou toute autre fréquence) — date, valeur constatée, notes. La date du jour est pré-remplie automatiquement lors de l'ajout d'une nouvelle ligne.
            - **Graphique d'évolution** : la courbe réelle (valeurs saisies, du premier relevé à aujourd'hui), un trait vertical marquant aujourd'hui, puis 3 projections (Pessimiste / Correcte / Optimiste) qui repartent de la dernière valeur saisie. Zoom à la molette, glissé horizontal et sélection des courbes fonctionnent comme sur le graphique de Trésorerie.
            - **Euros constants** : le réglage affiché ici est hérité de la Vue d'ensemble/Trésorerie (il ne se règle pas indépendamment) et ne déflate que les 3 projections — les valeurs réellement saisies restent affichées telles quelles.

            La dernière valeur saisie devient automatiquement le solde de référence utilisé par les calculs de Trésorerie et de Vue d'ensemble : rien à reporter manuellement ailleurs.
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
            - **Seuil d'alerte — compte courant (€)** : Décorrélé du seuil bas. Si la trésorerie passe sous ce seuil, le niveau d'alerte (qui suspend les versements sur les placements marqués d'une priorité de pause) augmente de 1, sans déclencher de virement automatique. Laissé vide, il n'a aucun effet.
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

  const HELP_VIDEOS = {
    chapters: [
      {
        id: "ch0",
        number: "0",
        title: "Découverte & Vue globale",
        icon: "🌟",
        description: "Comprendre la philosophie de MyFamilyBudget et naviguer sur le tableau de bord de synthèse."
      },
      {
        id: "ch1",
        number: "1",
        title: "Configuration initiale & Paramètres",
        icon: "⚙️",
        description: "Définir vos hypothèses d'inflation, monnaie constante, trésorerie de départ, seuils d'épargne et sauvegardes."
      },
      {
        id: "ch2",
        number: "2",
        title: "Gestion du réel, Banques & Pointage",
        icon: "💳",
        description: "Importer vos relevés bancaires, catégoriser vos opérations, gérer le différé et pointer le réel face au prévisionnel."
      },
      {
        id: "ch3",
        number: "3",
        title: "Prévisions, Trésorerie & Planification patrimoniale",
        icon: "📈",
        description: "Modéliser vos revenus, charges, crédits, placements, retraite, fiscalité et activer la Date Pivot."
      }
    ],
    items: [
      // ── Chapitre 0 ──────────────────────────────────────────
      {
        id: "0",
        chapterId: "ch0",
        file: "video-00-presentation.webm",
        title: "Présentation générale de MyFamilyBudget",
        duration: "1 min 05",
        badge: "Fondations",
        need: "Vous découvrez l'application et souhaitez comprendre sa philosophie : concilier gestion du quotidien (analyse du réel) et projections financières à long terme (patrimoine, retraite).",
        highlights: [
          "Double approche : analyse du réel bancaire vs prévisions pluriannuelles",
          "Présentation des 7 modules clés de la barre latérale",
          "Respect total de la confidentialité : calculs et données 100% locaux dans votre navigateur"
        ],
        keywords: ["présentation", "introduction", "découverte", "modules", "navigation", "confidentialité"]
      },
      {
        id: "2.8",
        chapterId: "ch0",
        file: "video-28-overview.webm",
        title: "Vue d'ensemble : tableau de bord global",
        duration: "1 min 25",
        badge: "Synthèse",
        need: "Vous souhaitez avoir une vision claire et synthétique de votre santé financière : trésorerie disponible, patrimoine net, alertes et jauges d'indépendance.",
        highlights: [
          "Les 4 indicateurs clés (KPIs) en haut de page",
          "Graphique de trésorerie disponible cumulée et patrimoine net",
          "Journal des mouvements bancaires prévus",
          "Camembert de répartition d'actifs et diversification du foyer"
        ],
        keywords: ["tableau de bord", "overview", "kpis", "synthèse", "camembert", "actifs", "journal"]
      },

      // ── Chapitre 1 ──────────────────────────────────────────
      {
        id: "1.1",
        chapterId: "ch1",
        file: "video-11-parametres.webm",
        title: "Paramètres généraux du foyer",
        duration: "1 min 05",
        badge: "Paramétrage",
        need: "Vous configurez votre foyer pour la première fois : hypothèses d'inflation, monnaie constante, alertes de solde et notifications.",
        highlights: [
          "Taux d'inflation annuelle prévisionnel (2 % par défaut)",
          "Bascule monnaie constante vs euros courants (pouvoir d'achat futur)",
          "Seuil de confort et notifications de sécurité"
        ],
        keywords: ["paramètres", "inflation", "monnaie constante", "notifications", "confort", "réglages"]
      },
      {
        id: "1.2",
        chapterId: "ch1",
        file: "video-12-tresorerie-depart.webm",
        title: "Trésorerie de départ & Date Pivot initiale",
        duration: "0 min 50",
        badge: "Paramétrage",
        need: "Vous démarrez sans historique bancaire importé et souhaitez fixer le montant de départ de votre compte courant pour initialiser les courbes.",
        highlights: [
          "Saisie du solde initial du compte courant",
          "Rôle de la date de départ avant le premier import bancaire",
          "Point de départ de la courbe d'accumulation de trésorerie"
        ],
        keywords: ["trésorerie de départ", "solde initial", "compte courant", "date de départ"]
      },
      {
        id: "1.3",
        chapterId: "ch1",
        file: "video-13-sweep.webm",
        title: "Virement automatique & Seuils (Sweep)",
        duration: "1 min 00",
        badge: "Automatisation",
        need: "Vous souhaitez automatiser l'écrêtement des surplus de votre compte courant vers votre épargne (et inversement en cas de coup dur sous le seuil d'alerte).",
        highlights: [
          "Seuil de trésorerie haute (écrêtement vers l'épargne, ex: 6 000 €)",
          "Seuil d'alerte / pause épargne (suspension des versements en cas de tension)",
          "Activation du moteur de virement automatique"
        ],
        keywords: ["sweep", "virement automatique", "seuil haut", "seuil bas", "pause épargne", "sécurité"]
      },
      {
        id: "1.4",
        chapterId: "ch1",
        file: "video-14-categories-actifs.webm",
        title: "Catégories d'actifs patrimoniaux",
        duration: "0 min 50",
        badge: "Patrimoine",
        need: "Vous souhaitez personnaliser vos classes d'actifs (Livrets sécurisés, Actions / PEA, Immobilier, Retraite) avec leurs couleurs et icônes.",
        highlights: [
          "Gestion des classes d'actifs patrimoniales",
          "Personnalisation des couleurs, icônes et intitulés",
          "Impact direct sur le camembert de diversification de la Vue d'ensemble"
        ],
        keywords: ["actifs", "classes d'actifs", "catégories", "livrets", "actions", "immobilier", "couleurs"]
      },
      {
        id: "1.5",
        chapterId: "ch1",
        file: "video-15-sauvegarde.webm",
        title: "Sauvegarde, export & restauration",
        duration: "0 min 45",
        badge: "Sécurité",
        need: "Vous voulez sauvegarder vos données sur votre ordinateur, changer de machine, ou réinitialiser l'application avec les données de démonstration.",
        highlights: [
          "Exportation JSON complète en un clic (fichier de sauvegarde)",
          "Importation et restauration instantanée sur tout navigateur",
          "Réinitialisation sécurisée des données avec confirmation"
        ],
        keywords: ["sauvegarde", "export", "import", "json", "restauration", "réinitialiser", "backup"]
      },

      // ── Chapitre 2 ──────────────────────────────────────────
      {
        id: "2.1",
        chapterId: "ch2",
        file: "video-21-import-csv.webm",
        title: "Importer un relevé bancaire (CSV / Excel)",
        duration: "0 min 50",
        badge: "Banques",
        need: "Vous avez téléchargé un fichier de transactions depuis votre banque et souhaitez charger vos opérations réelles dans l'outil.",
        highlights: [
          "Glisser-déposer de votre fichier CSV ou Excel bancaire",
          "Mappage intuitif des colonnes (Date, Montant, Libellé)",
          "Détection et élimination automatique des opérations en doublon"
        ],
        keywords: ["import", "csv", "excel", "relevé", "banque", "transactions", "colonnes", "doublons"]
      },
      {
        id: "2.2",
        chapterId: "ch2",
        file: "video-22-categories-depenses.webm",
        title: "Créer des catégories de dépenses",
        duration: "0 min 55",
        badge: "Catégories",
        need: "Vous souhaitez structurer vos postes de budget (Alimentation, Logement, Transports) et marquer vos postes réductibles pour vos arbitrages.",
        highlights: [
          "Création d'une catégorie de dépense avec couleur et icône",
          "Rôle du drapeau « Poste réductible » (dépenses non indispensables)",
          "Identification immédiate de votre marge de manœuvre budgétaire"
        ],
        keywords: ["catégories", "dépenses", "poste réductible", "arbitrage", "budget", "alimentation", "loisirs"]
      },
      {
        id: "2.3",
        chapterId: "ch2",
        file: "video-23-categorisation.webm",
        title: "Catégoriser les transactions importées",
        duration: "1 min 15",
        badge: "Automatisation",
        need: "Vous venez d'importer vos transactions et souhaitez les classer rapidement grâce aux règles de mots-clés automatiques.",
        highlights: [
          "Attribution d'une catégorie à une opération bancaire",
          "Règles automatiques par mots-clés (rétroactives et pour les futurs imports)",
          "Jauge de progression du taux de catégorisation"
        ],
        keywords: ["catégorisation", "mots-clés", "règles automatiques", "classement", "gain de temps"]
      },
      {
        id: "2.4",
        chapterId: "ch2",
        file: "video-24-recherche-tri.webm",
        title: "Trier et rechercher dans les transactions",
        duration: "0 min 45",
        badge: "Productivité",
        need: "Vous recherchez une opération précise ou souhaitez analyser vos dépenses sur une période, un montant ou une catégorie donnée.",
        highlights: [
          "Tri instantané sur n'importe quel en-tête de colonne (Date, Montant, Libellé)",
          "Filtres textuels cumulatifs directement sous les en-têtes",
          "Compteur de résultats et bouton de réinitialisation des filtres"
        ],
        keywords: ["recherche", "tri", "filtres", "transactions", "retrouver", "dépenses"]
      },
      {
        id: "2.5",
        chapterId: "ch2",
        file: "video-25-operations-en-cours.webm",
        title: "Opérations en cours & solde disponible",
        duration: "1 min 00",
        badge: "Trésorerie réelle",
        need: "Vous utilisez des cartes bancaires à débit différé, des chèques non débités ou des dépenses engagées pour connaître votre solde réellement disponible.",
        highlights: [
          "Distinction clé entre solde bancaire brut et solde réel disponible",
          "Saisie des opérations engagées (cartes à débit différé, chèques émis)",
          "Anticipation du débit groupé en fin de mois sans mauvaise surprise"
        ],
        keywords: ["opérations en cours", "débit différé", "solde disponible", "chèques", "cb différée"]
      },
      {
        id: "2.6",
        chapterId: "ch2",
        file: "video-26-pointage.webm",
        title: "Le Pointage mensuel (Réel vs Prévu)",
        duration: "1 min 30",
        badge: "Pointage",
        need: "Vous arrivez en fin de mois et souhaitez rapprocher vos opérations bancaires réelles de vos lignes de budget prévisionnel.",
        highlights: [
          "Comprendre l'origine des données (alimentées depuis l'onglet Trésorerie)",
          "Rapprochement automatique en un clic par catégorie partagée",
          "Pointage manuel ligne par ligne avec suggestions de montants",
          "Codes couleurs : conforme (bleu), économie (vert), dépassement (rouge)"
        ],
        keywords: ["pointage", "rapprochement", "réel vs prévu", "écarts", "budget mensuel", "clôture"]
      },
      {
        id: "2.7",
        chapterId: "ch2",
        file: "video-27-analyse.webm",
        title: "Analyse : comprendre ses dépenses",
        duration: "0 min 50",
        badge: "Analyse",
        need: "Vous voulez faire le bilan de vos dépenses, analyser vos moyennes mensuelles réelles et identifier vos leviers d'économies.",
        highlights: [
          "Moyennes mensuelles réelles constatées par poste",
          "Répartition des dépenses réelles en camembert interactif",
          "Filtre exclusif sur les postes réductibles pour arbitrer votre budget"
        ],
        keywords: ["analyse", "moyennes", "dépenses réelles", "postes réductibles", "économies", "bilan"]
      },

      // ── Chapitre 3 ──────────────────────────────────────────
      {
        id: "3.1",
        chapterId: "ch3",
        file: "video-31-revenus-recurrents.webm",
        title: "Saisir ses revenus récurrents (Trésorerie)",
        duration: "0 min 50",
        badge: "Trésorerie",
        need: "Vous configurez les salaires et rentes de votre foyer avec leur rythme de revalorisation annuelle prévisionnelle.",
        highlights: [
          "Saisie des salaires nets mensuels de chaque conjoint",
          "Augmentation annuelle prévisionnelle (taux %)",
          "Mise à jour en temps réel de la courbe de trésorerie"
        ],
        keywords: ["revenus", "salaires", "augmentation", "trésorerie", "salaire net", "revalorisation"]
      },
      {
        id: "3.2",
        chapterId: "ch3",
        file: "video-32-charges-recurrentes.webm",
        title: "Saisir ses charges récurrentes",
        duration: "1 min 00",
        badge: "Trésorerie",
        need: "Vous listez vos loyers, assurances, abonnements et courses avec la règle de gestion de l'inflation.",
        highlights: [
          "Règle d'or de l'inflation : laisser le taux vide indexe la charge sur l'inflation",
          "Geler un montant fixe : saisir explicitement 0 % (ex : loyer fixe)",
          "Recensement complet de vos charges mensuelles"
        ],
        keywords: ["charges", "loyer", "courses", "inflation", "charges récurrentes", "dépenses fixes"]
      },
      {
        id: "3.3",
        chapterId: "ch3",
        file: "video-33-credits-tresorerie.webm",
        title: "Remboursements de crédits en trésorerie",
        duration: "1 min 00",
        badge: "Crédits",
        need: "Vous remboursez des prêts et souhaitez que leurs mensualités s'arrêtent automatiquement à leur échéance pour libérer de la trésorerie.",
        highlights: [
          "Saisie d'une mensualité avec date de début et date de fin",
          "Arrêt automatique de la charge dès la fin de l'échéance",
          "Accélération visible de l'accumulation d'épargne après remboursement"
        ],
        keywords: ["crédits", "mensualité", "fin de prêt", "échéance", "remboursement", "prêt immobilier"]
      },
      {
        id: "3.4",
        chapterId: "ch3",
        file: "video-34-placements-patrimoine.webm",
        title: "Placements & Comptes d'épargne",
        duration: "1 min 20",
        badge: "Épargne",
        need: "Vous voulez configurer vos livrets, assurance-vie ou PEA, définir leurs 3 rendements et renseigner leur solde de référence.",
        highlights: [
          "Création d'un placement via le tiroir latéral dédié",
          "Hypothèses de rendement : pessimiste, correct et optimiste",
          "Fenêtre 📈 Historique : saisie du solde de départ des projections"
        ],
        keywords: ["placements", "livret a", "pea", "assurance-vie", "rendement", "historique", "épargne"]
      },
      {
        id: "3.5",
        chapterId: "ch3",
        file: "video-35-credits-patrimoine.webm",
        title: "Crédits immobiliers & Passif (Patrimoine)",
        duration: "0 min 50",
        badge: "Passif",
        need: "Vous configurez le Capital Restant Dû (CRD) de vos emprunts pour calculer votre passif et votre patrimoine net réel.",
        highlights: [
          "Saisie des caractéristiques du prêt (capital emprunté, taux, mensualité)",
          "Renseignement du CRD au dernier relevé bancaire",
          "Amortissement automatique et soustraction du passif pour le patrimoine net"
        ],
        keywords: ["passif", "crd", "emprunt", "crédit immobilier", "patrimoine net", "amortissement"]
      },
      {
        id: "3.6",
        chapterId: "ch3",
        file: "video-36-sweep-placements.webm",
        title: "Virement automatique Sweep vers les placements",
        duration: "0 min 50",
        badge: "Stratégie",
        need: "Vous organisez l'affectation automatique de vos excédents de compte courant vers vos différents placements avec des priorités.",
        highlights: [
          "Attribution d'une priorité de Sweep (ex: Priorité 1 pour le Livret A)",
          "Définition d'un plafond (ex: 22 950 €) pour basculer vers la priorité suivante",
          "Priorité de pause d'épargne en cas de baisse de trésorerie sous le seuil d'alerte"
        ],
        keywords: ["sweep", "priorité", "plafond", "virement automatique", "stratégie d'épargne", "livret a"]
      },
      {
        id: "3.7",
        chapterId: "ch3",
        file: "video-37-depenses-ponctuelles.webm",
        title: "Dépenses ponctuelles, travaux & primes",
        duration: "1 min 00",
        badge: "Flux variables",
        need: "Vous prévoyez des dépenses non mensuelles (vacances, gros travaux) ou percevez des primes variables indexées sur votre salaire.",
        highlights: [
          "Saisie d'une dépense ponctuelle à date précise (déduite une seule fois)",
          "Configuration de primes et intéressement indexés en % du salaire",
          "Ajustement dynamique de la prime si le salaire de référence progresse"
        ],
        keywords: ["dépenses ponctuelles", "vacances", "travaux", "primes", "intéressement", "salaire de référence"]
      },
      {
        id: "3.8",
        chapterId: "ch3",
        file: "video-38-jauges-fire.webm",
        title: "Jauges FIRE & Indépendance financière",
        duration: "0 min 55",
        badge: "Indépendance",
        need: "Vous souhaitez évaluer si vos placements et vos futures pensions suffiront à couvrir votre niveau de vie à la retraite.",
        highlights: [
          "Jauge 1 : Règle des 4 % avec liquidation totale (placements + immobilier)",
          "Jauge 2 : Couverture réaliste du niveau de vie (placements financiers + pensions seules)",
          "Comparaison directe des 3 scénarios (pessimiste, correct, optimiste)"
        ],
        keywords: ["fire", "indépendance financière", "règle des 4%", "couverture réaliste", "rente", "retraite"]
      },
      {
        id: "3.9",
        chapterId: "ch3",
        file: "video-39-retraite.webm",
        title: "Retraite : estimation des pensions",
        duration: "0 min 55",
        badge: "Retraite",
        need: "Vous estimez vos futures pensions du Régime Général et de l'Agirc-Arrco directement à partir de votre carrière et vos trimestres.",
        highlights: [
          "Liaison de chaque conjoint à son salaire de trésorerie",
          "Saisie des trimestres validés à partir du relevé info-retraite.fr",
          "Calcul automatique des pensions brutes et intégration directe en trésorerie"
        ],
        keywords: ["retraite", "pension", "trimestres", "agirc-arrco", "taux plein", "décote", "info-retraite"]
      },
      {
        id: "3.10",
        chapterId: "ch3",
        file: "video-310-impots.webm",
        title: "Impôts & Prélèvement à la Source",
        duration: "0 min 50",
        badge: "Fiscalité",
        need: "Vous configurez votre foyer fiscal (parts, enfants) et simulez votre Prélèvement à la Source (PAS) mensualisé.",
        highlights: [
          "Configuration du quotient familial (parts adultes et demi-parts enfants)",
          "Simulation automatique du taux de PAS mensualisé dans le budget",
          "Ajustement avec l'impôt réel constaté sur avis d'imposition"
        ],
        keywords: ["impôts", "pas", "prélèvement à la source", "parts fiscales", "avis d'imposition", "quotient"]
      },
      {
        id: "3.11",
        chapterId: "ch3",
        file: "video-311-date-pivot.webm",
        title: "Date Pivot : bascule Réel → Prévisionnel",
        duration: "0 min 45",
        badge: "Date Pivot",
        need: "Vous activez la Date Pivot pour que vos projections partent automatiquement de votre solde réel bancaire le plus récent.",
        highlights: [
          "Bouton ⚡ Caler sur le dernier import bancaire",
          "Bascule en mode automatique basé sur les opérations réelles",
          "Frontière nette : le réel à gauche de la date, les prévisions à droite"
        ],
        keywords: ["date pivot", "caler", "point de bascule", "solde réel constaté", "automatique", "transition"]
      }
    ]
  };

  exports.HELP_CONTENT = HELP_CONTENT;
  exports.HELP_VIDEOS = HELP_VIDEOS;
})(typeof window !== 'undefined' ? window.BudgetApp = window.BudgetApp || {} : module.exports);