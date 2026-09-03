# Manuel de l'outil de gestion budgétaire familiale

Ce manuel explique ce que fait chaque page de l'outil, et surtout **comment l'argent circule d'une page à l'autre** — les règles qui relient les revenus, les charges, les placements, les impôts, les relevés bancaires réels et la trésorerie entre eux. Il est écrit pour quelqu'un qui découvre l'outil de zéro, et va jusqu'aux formules exactes utilisées par les moteurs de calcul.

L'outil est un simple fichier HTML qui fonctionne dans votre navigateur, sans serveur ni compte. Vos données sont stockées dans un fichier JSON séparé (chargé/exporté depuis l'onglet Paramètres ou le menu latéral) et recopiées automatiquement dans le stockage local du navigateur à chaque modification — pensez à exporter régulièrement une copie du JSON, c'est votre seule sauvegarde portable.

---

## 0. Les trois idées à comprendre avant tout le reste

1. **Un seul compte "trésorerie"**, votre compte courant, sert de pivot. Tous les revenus y arrivent, toutes les charges en sortent. L'outil surveille son solde en permanence et le maintient automatiquement entre un **seuil bas** et un **seuil haut** que vous définissez, en virant l'excédent vers l'épargne ou en rapatriant depuis l'épargne selon les besoins (détaillé au chapitre 11).

2. **Trois scénarios (Pessimiste / Correct / Optimiste)** ne s'appliquent qu'au **taux de rendement annuel de vos placements**. Vos revenus, vos charges, vos impôts et vos dépenses ponctuelles restent identiques d'un scénario à l'autre — ce ne sont pas trois versions différentes de votre vie, seulement trois hypothèses de rendement financier.

3. **Deux niveaux d'utilisation coexistent dans l'application** :
   - **La Planification & Projection Pluriannuelle (Macro / 10-30 ans)** : Vue d'ensemble, Trésorerie, Placements, Retraite, Impôts, Paramètres.
   - **L'Analyse du Réel & Suivi Bancaire (Micro / Mois par Mois)** : Import bancaire, Opérations en cours & Chèques, Pointage et Analyse.

---

# PARTIE 1 — PLANIFICATION & SIMULATION (LONG TERME)

---

## 1. Onglet « Vue d'ensemble »

C'est le tableau de bord général : il agrège des informations produites ailleurs, il n'a pas de saisie propre (à l'exception du sélecteur de scénario sur certains graphiques).

### KPIs en haut de page
- **Trésorerie de départ** : le solde de départ que vous avez saisi en Paramètres (ou calculé via la Date Pivot).
- **Patrimoine placé actuel** : somme des soldes actuels de tous vos placements (à la date de saisie de chacun).
- **Flux net — année en cours** : revenus moins charges de l'année en cours, calculé par le moteur simplifié.
- **Année de retraite visée** : `année de naissance (Paramètres) + âge de départ visé`.

### Trésorerie disponible cumulée, Passif & Patrimoine Net
Le graphique de référence, produit par le moteur détaillé (jour par jour). Il superpose :
- le **total des avoirs bruts** (tous les placements, sans déduire les crédits),
- le **passif restant** (somme des Capitaux Restants Dus des crédits, onglet Placements),
- le **patrimoine net réel** (avoirs bruts − passif),
- la **trésorerie disponible** (le compte courant lui-même — bornée entre vos seuils bas et haut grâce au sweep).

**Filtrage des courbes :** Les boutons de couleur au-dessus du graphique permettent d'afficher ou masquer chaque courbe indépendamment. Le bouton **Tous** affiche l'ensemble. Un clic sur une courbe visible quand toutes le sont **l'isole** (les autres se masquent). Un clic sur une courbe masquée **l'ajoute** à l'affichage. Un clic sur une courbe déjà visible parmi plusieurs **la masque**. Si une seule courbe est affichée, la cliquer à nouveau ramène à la vue complète. Ces actions sont également disponibles en cliquant directement sur les étiquettes de la légende intégrée au graphique.

**Navigation temporelle :** Zoomez avec la molette de la souris pour passer de la vue annuelle (> 3 ans) à la vue mensuelle (2 mois – 3 ans) puis à la vue journalière (< 2 mois). Glissez horizontalement pour vous déplacer dans le temps. Les boutons **Tout / 10 ans / 5 ans / 1 an** permettent un recadrage rapide.

### Virements & mouvements prévus (le « Journal des mouvements »)
Le détail, jour par jour, de chaque mouvement d'argent simulé par le moteur détaillé : versement de charge, prélèvement d'impôt, sweep vers/depuis l'épargne, dépense ponctuelle...
- **Tri** : cliquez sur l'en-tête d'une colonne pour trier.
- **Filtre** : tapez dans la case sous chaque en-tête pour restreindre l'affichage (insensible à la casse, cumulable).
- Le bouton **Exporter CSV** exporte toujours la totalité des mouvements.
- Un sélecteur permet de rejouer le journal pour chacun des 3 scénarios de rendement.

### Crédits (résumé) & Immobilier physique (résumé)
Rappel condensé des tableaux détaillés dans l'onglet Placements (chapitre 3).

### Indépendance Financière (FIRE) — Règle des 4 %
Compare, à votre année de retraite visée, une rente théorique tirée de votre patrimoine à vos charges mensuelles projetées cette année-là (détaillé au chapitre 11.6).

### Répartition d'actifs
Un donut qui répartit votre patrimoine placé actuel en 6 grandes classes d'actifs (Cash, Fonds en euros, Immobilier/SCPI, Actions, Obligations, Épargne salariale/PER), déterminées par la **catégorie** de chaque placement et la table de correspondance de l'onglet Paramètres.

### Patrimoine placé — 3 scénarios
Simple courbe de la somme de tous vos placements (hors immobilier physique), projetée dans les 3 hypothèses de rendement.

---

## 2. Onglet « Trésorerie »

### Évolution de la trésorerie (estimation simplifiée)
⚠️ **À lire avec prudence.** Cette courbe vient du moteur simplifié annuel : elle cumule année après année `revenus − charges − versements d'épargne programmés − dépenses ponctuelles + transferts − impôts`, sans tenir compte du sweep automatique vers/depuis l'épargne. Elle montre ce que deviendrait votre trésorerie *si elle n'était jamais canalisée vers l'épargne*.

### Revenus récurrents
Salaires et autres revenus réguliers. Chaque ligne a un montant mensuel, une augmentation annuelle optionnelle, une date de début et une date de fin. Formule : `montant_mensuel × (1 + croissance)^(années écoulées depuis le début)`.

### Charges récurrentes
Crédits, logement étudiant, factures, entretien... **Règle d'indexation par défaut : une charge sans taux de croissance explicitement saisi suit automatiquement le taux d'inflation général** (Paramètres). Pour fixer une charge sans augmentation, saisissez **0 %** explicitement.

### Dépenses ponctuelles
Événements et achats à date fixe, sans récurrence (travaux, véhicule...).

### Primes, participation & intéressement
Chaque ligne applique un **taux** à un **revenu de référence** choisi dans la liste des Revenus récurrents — la prévision se recalcule automatiquement si ce revenu change.

### Valeurs réelles constatées
Dès que vous connaissez le montant réel d'une prime/participation pour une année donnée, entrez-le ici : il remplace la prévision pour cette année précise.

---

## 3. Onglet « Placements »

### Placements & comptes (cartes + fiche détaillée)
Chaque carte représente un compte ou un placement. Cliquer dessus ouvre la fiche détaillée en 4 sections :
1. **Informations générales** : libellé, catégorie d'actif (utilisée pour la Répartition d'actifs), notes libres.
2. **Solde actuel & versements** : solde et sa date de référence, versement mensuel programmé et fenêtre d'activité (dès le / jusqu'au).
3. **Hypothèses de rendement annuel (%)** : un taux distinct pour chacun des 3 scénarios (Pessimiste / Correct / Optimiste), composé mensuellement.
4. **Automatisations (Sweep & Pause épargne)** :
   - **Priorité virement auto** : ordre d'utilisation du compte pour recevoir l'excédent ou combler le manque de trésorerie (1 = utilisé en premier).
   - **Plafond virement auto** : montant maximal pouvant être versé automatiquement sur ce compte.
   - **Seuil d'alerte tampon (€)** : si le solde de ce compte passe sous ce seuil, une alerte de tension est déclenchée.
   - **Priorité pause épargne** : détermine l'ordre de suspension des versements programmés en cas de coup dur.

### Transferts depuis un placement vers le compte courant
Simule un retrait ponctuel et volontaire pour financer une grosse dépense identifiée à l'avance.

### Crédits & Passif Immobilier
Capital Restant Dû (CRD), taux hors assurance, mensualité, assurance, dates.

### Actif Immobilier Physique
Résidence principale, terrains, nue-propriété. Valeur estimée à une date de référence et taux de revalorisation annuel.

---

## 4. Onglet « Retraite »

Cet onglet permet de modéliser les droits à la retraite de chaque membre du foyer fiscal et d'injecter automatiquement leurs futures pensions dans les simulations financières.

### 4.1 Fiche de chaque personne du foyer
- **Identité & Année de naissance** : détermine l'âge de départ légal et le taux plein automatique (67 ans).
- **Lien avec le revenu** : permet de lier la personne à une ligne de salaire de l'onglet Trésorerie.
- **Historique des salaires bruts** : tableau renseignant les salaires bruts passés et projetés année par année.

### 4.2 Paramètres des régimes de retraite
- **Plafond Annuel de la Sécurité Sociale (PASS)** : valeur de référence 2026 et taux de revalorisation annuel.
- **Régime de base** : calculé sur la moyenne des 25 meilleures années (plafonnées au PASS) et le ratio trimestres validés / 172 trimestres requis.
- **Régime complémentaire Agirc-Arrco** : calculé à partir des points accumulés et acquis chaque année, multipliés par la valeur du point Agirc-Arrco.

### 4.3 Injection automatique dans la simulation
À compter de l'année de départ à la retraite, la pension estimée est **injectée automatiquement comme un revenu réel** dans les deux moteurs de calcul (sans aucune saisie nécessaire dans l'onglet Trésorerie) et revalorisée annuellement selon l'inflation.

---

## 5. Onglet « Impôts »

Calcul fiscal simplifié basé sur le barème progressif de l'impôt sur le revenu français.

### Foyer fiscal & Barème progressif
- Déclaration commune : 2 parts de base. +0,5 part pour les 2 premiers enfants, +1 part par enfant à partir du 3ᵉ. Âge de sortie réglable (21 ans par défaut).
- Barème par tranches (taux marginaux à 0 %, 11 %, 30 %, 41 %, 45 %).
- Abattement forfaitaire de 10 % pour frais professionnels appliqué sur les revenus imposables.

### Prélèvement À la Source (PAS) & Régularisation
- **Taux de PAS prévisionnel** : calculé automatiquement chaque année.
- **Taux de PAS réels** : saisissez le taux réel de votre bulletin de paie dès son actualisation en septembre.
- **Impôt réel constaté (avis d'imposition)** : entrez le montant réel dû dès réception de l'avis. La différence avec les sommes prélevées engendre une **régularisation d'impôt en année N+1** dans les projections.

---

## 6. Onglet « Paramètres »

### Paramètres généraux & Horizon de projection
- **Année de naissance** et **âge de départ visé**.
- **Simuler la retraite jusqu'à l'âge de (ex. 90 ans)** : prolonge l'horizon de simulation pour vérifier la viabilité du patrimoine sur l'ensemble de la retraite.
- **Taux d'inflation estimé** : utilisé pour indexer les charges et pour le calcul en monnaie constante.

### Date Pivot & Mode de Trésorerie de Départ
- **Mode Manuel** : utilise le montant fixe saisi dans *« Trésorerie disponible de départ »*.
- **Mode Automatique (Date Pivot)** : calcule votre trésorerie exacte en sommant le solde initial et toutes les transactions bancaires importées jusqu'à la Date Pivot. Cela permet de caler la simulation sur le solde bancaire réel à une date clé.

### Virement automatique (Sweep)
Activation globale, seuil haut et seuil bas du compte courant.

### Catégories d'actifs & Stockage
- Table de correspondance des classes d'actifs pour le donut de répartition.
- Boutons **Exporter (JSON)**, **Importer (JSON)** et **Réinitialiser**.

---

# PARTIE 2 — ANALYSE DU RÉEL & SUIVI BANCAIRE (COURT TERME)

---

## 7. Onglet « Import »

L'onglet Import permet d'intégrer vos relevés bancaires réels (fichiers CSV ou XLS/XLSX exportés depuis votre banque) pour analyser vos dépenses concrètes.

### 7.1 Format du fichier & Mapping des colonnes
- **Séparateur** : Point-virgule ( ; ), virgule ( , ) ou tabulation.
- **Format de date** : `DD/MM/YYYY`, `YYYY-MM-DD`, etc.
- **Mapping interactif** : Assignez le rôle de chaque colonne détectée (*Date*, *Libellé*, *Type*, *Montant*, *Ignorer*) sur l'aperçu des premières lignes. Les montants doivent être signés (positif = rentrée, négatif = débit).

### 7.2 Dédoublonnage automatique intelligent
Réimporter un fichier qui chevauche une période déjà enregistrée ne crée aucun doublon : toute transaction identique (même date, même libellé et même montant) est automatiquement ignorée.

### 7.3 Moteur de règles d'auto-catégorisation
Lorsque vous assignez une catégorie à une transaction, l'outil vous propose d'enregistrer un **mot-clé éditable** (ex. `ASF` ou `TOTAL`). Dès qu'une transaction contient ce mot-clé, elle est catégorisée automatiquement, y compris rétroactivement sur tout l'historique importé.

### 7.4 Dictionnaire des catégories & Postes réductibles (compressibles)
- Définissez vos catégories de dépenses et de revenus.
- **Poste réductible (compressible)** : Cochez *« Oui »* sur les catégories non indispensables (loisirs, abonnements superflus, restaurants). L'outil isole automatiquement ces montants dans l'onglet Analyse pour mesurer votre potentiel d'économie.

---

## 8. Onglet « Opérations en cours » (Chèques, Différés & Solde Réel)

Cet onglet gère vos **engagements de trésorerie** au quotidien (chèques émis non encore débités, paiements en CB à débit différé, rentrées ou salaires annoncés) sans jamais fausser vos budgets prévisionnels.

### 8.1 Le calcul du Solde Réellement Disponible

$$\text{Solde Disponible Réel} = \text{Solde Banque (Relevé)} - \sum \text{Chèques \& Débits en cours} + \sum \text{Crédits attendus}$$

- **Solde Banque (Relevé)** : Solde calculé au relevé bancaire à la fin du mois sélectionné.
- **Chèques & Débits en cours** : Somme des débits engagés mais non encore prélevés par la banque.
- **Crédits en attente** : Rentrées prévues (ex. bulletin de salaire reçu avant le virement effectif).
- **🛡️ Solde Réellement Disponible** : L'argent véritablement utilisable sans risquer de découvert imprévu.

### 8.2 Saisie rapide & Pré-remplissage depuis le budget
- Bouton **« ➕ Nouvelle opération »** avec sélecteur optionnel *« ⚡ Pré-remplir depuis le budget »* (charges, revenus, dépenses ponctuelles).
- Pré-remplit le libellé, la catégorie, le type et le montant indicatif (100 % modifiable).
- **Dates** :
  - *Date d'engagement* : Obligatoire (date du jour ou 1er du mois actif).
  - *Date d'effet prévue* : Optionnelle (laissée vide pour les chèques ; renseignée pour les salaires/CB différées avec badge *« ⏱ Terme dépassé »* si en retard).

### 8.3 Import des encours de CB différées
- Bouton **« 📥 Importer encours CB »** : permet d'importer directement un fichier CSV d'encours de carte à débit différé fourni par votre banque (ex. `export_*.csv`). Les extensions compatibles sont : csv, xls, xlsx, txt.
- **Détection & Configuration automatique** : analyse intelligente des en-têtes et des colonnes (Date, Libellé, Montant), choix du séparateur (`;`, `,`, tab) et du format de date.
- **Option Date d'achat** : permet au choix de conserver la date de relevé fin de mois ou d'extraire la date d'achat réelle présente dans le libellé (ex: `FACTURE CARTE DU 290726` $\rightarrow$ 29/07/2026).
- **Dédoublonnage rigoureux** : identique à l'onglet Import, évite de compter deux fois les mêmes débits lors d'imports successifs.
- **Auto-catégorisation instantanée** : vos règles de catégorisation (créées dans l'onglet Import) s'appliquent immédiatement à toutes les dépenses CB importées.

### 8.4 Navigation mensuelle & Gestion des reliquats antérieurs
- **Vue par mois (`◄ Mois Année ►`)** : Maintient un tableau court et lisible mois après mois.
- **Alerte Reliquats antérieurs** : Si un chèque émis en janvier n'est toujours pas débité en juin, un bandeau d'alerte apparaît en juin (`⚠️ X engagement(s) émis avant juin toujours en circulation`). Vous pouvez déplier ce bloc et **pointer directement le chèque ancien** dès son débit bancaire, sans devoir remonter dans le passé.

### 8.5 Rapprochement bancaire (Pointage)
- **Manuel (`🔗 Pointer`)** : Ouvre le volet des transactions bancaires candidates avec suggestions automatiques (montant identique ou n° de chèque détecté).
- **Automatique (`⚡ Rapprochement auto`)** : Rapproche en un clic les chèques identifiés par leur numéro et les montants uniques non ambigus.
- **Cohérence des catégories entre l'opération en attente et la transaction importée** : une opération en attente et la transaction bancaire qui la solde sont deux représentations de la même donnée ; leur catégorisation (catégorie simple ou ventilation/splits) doit redevenir cohérente au moment du rapprochement.
  - Si les deux côtés portent **exactement** la même catégorisation (même catégorie, ou même ensemble de catégories en cas de splits), le rapprochement se fait directement, sans question.
  - Dans **tous les autres cas** — catégorisations différentes, ou un seul des deux côtés catégorisé — l'application ne choisit jamais à votre place, car il est possible que vous ayez fait des choix incohérents de part et d'autre :
    - En rapprochement **manuel**, une fenêtre « ⚖️ Catégorisation différente » s'ouvre et présente les deux catégorisations côte à côte (« Opération en attente » / « Transaction bancaire (import) »). Vous choisissez laquelle conserver ; l'autre est alignée dessus (montants des splits réajustés sur le montant réel débité, le cas échéant). Une ventilation (splits) choisie est toujours conservée telle quelle, jamais transformée en catégorie unique.
    - En rapprochement **automatique**, les paires en conflit ne sont **pas** rapprochées : elles restent « en attente », et le message de synthèse indique combien d'opérations nécessitent une confirmation manuelle (`🔗 Pointer`).

---

## 9. Onglet « Pointage » (Rapprochement du Budget)

L'onglet Pointage compare mois par mois les dépenses et recettes réelles de vos relevés bancaires avec vos lignes de budget prévisionnel (charges, revenus, versements d'épargne).

### 9.1 Les 4 indicateurs de synthèse
- **Budget prévu** : Total des charges et revenus actifs pour le mois sélectionné.
- **Débits en banque** : Total des débits réels constatés sur vos relevés ce mois-ci.
- **Pointé sur budget** : Montant des transactions effectivement associées aux lignes budgétaires.
- **Dépenses non pointées** : Total des débits bancaires qui n'ont pas encore été rattachés au budget (avec modal de consultation directe).

### 9.2 Les Statuts de pointage
- 🔵 **Conforme** : Montant pointé égal au montant prévu (avec une tolérance de ±2 % ou 1 €).
- 🟢 **Économie / Surplus** : Dépense réelle inférieure au budget, ou revenu supérieur au prévu.
- 🔴 **Dépassement / Déficit** : Dépense réelle supérieure au budget, ou revenu inférieur.
- 🟡 **En attente** : Ligne budgétaire pour laquelle aucun débit/crédit bancaire n'a encore été associé.

### 9.3 Volet d'association & Rapprochement automatique
- **🔗 Pointer (Volet latéral)** : Permet de sélectionner une ou plusieurs transactions bancaires pour les affecter à une ligne budgétaire (ex. 4 débits de supermarché sur la charge *Courses*). Filtre automatique par catégorie liée ou recherche libre.
- **⚡ Rapprochement automatique** : Associe en un clic toutes les transactions non pointées aux lignes de budget partageant la même catégorie.

---

## 10. Onglet « Analyse » (Tableau de bord du Réel)

L'onglet Analyse synthétise vos données réelles importées à travers 4 sous-onglets spécialisés :

### 10.1 Vue Générale & Postes Compressibles
- **Période d'analyse** : Vue sur 3 mois, 6 mois, 12 mois ou l'ensemble de l'historique.
- **Moyennes mensuelles** : Total des revenus, total des dépenses, épargne nette et taux d'épargne réel.
- **Indicateur des postes réductibles** : Calcule le montant et le pourcentage exact de vos dépenses consacrés aux catégories marquées comme « compressibles » dans l'onglet Import.

### 10.2 Atterrissage du Mois
Pour chaque catégorie de dépense du mois en cours, compare côte à côte :
- Le **Budget prévisionnel**.
- Le **Réel pointé** sur les lignes de budget.
- Les **Débits bruts en banque** et l'estimation de dépassement ou d'économie en fin de mois.

### 10.3 Historique Mensuel
Graphiques en barres et tableaux chronologiques détaillant mois par mois vos entrées, vos sorties et le flux net généré. Idéal pour visualiser les saisonnalités (vacances d'été, impôts, primes de fin d'année).

### 10.4 Dérives par Ligne Budgétaire
Compare le montant budgété de chaque charge ou revenu avec sa moyenne réelle constatée sur 3 mois et 12 mois. Permet d'identifier immédiatement les lignes de budget sous-évaluées (ex. abonnements ou énergie ayant augmenté).

---

# PARTIE 3 — MODÈLES DE CALCUL & CIRCULATION DE L'ARGENT

---

## 11. La circulation de l'argent : comment tout s'articule

### 11.1 Ce qui entre et sort de la trésorerie chaque mois
Le compte courant reçoit : les revenus récurrents actifs, les primes/participations, les transferts programmés. Il perd : les charges récurrentes, les dépenses ponctuelles, le prélèvement à la source, la régularisation d'impôt N+1 et les versements d'épargne programmés (sauf si mis en pause).

### 11.2 Le virement automatique compte courant ↔ épargne (sweep)
- En fin de mois, si le solde du compte courant dépasse le **seuil haut**, l'excédent est viré vers les placements selon leur **priorité virement auto** et dans la limite de leur **plafond**.
- À tout moment, si la trésorerie passe sous le **seuil bas**, le manque est immédiatement rapatrié depuis l'épargne dans le même ordre de priorité.

### 11.3 Croissance des placements
Chaque mois, le solde de chaque placement est actualisé selon la formule :
$$\text{Solde après intérêts} = \text{Solde initial} \times (1 + \text{taux annuel du scénario})^{1/12}$$

### 11.4 Monnaie constante
Quand la case « Monnaie constante » est cochée, les montants futurs sont corrigés du déflateur d'inflation :
$$\text{Déflateur} = \frac{1}{(1 + \text{inflation})^{\text{année} - \text{année de départ}}}$$

### 11.5 La pause automatique des versements d'épargne
Un niveau de pause suspend les versements programmés des placements dont la priorité de pause est inférieure ou égale au niveau en cours. Ce niveau s'élève dès qu'un rapatriement de trésorerie a lieu ou qu'un placement tombe sous son seuil d'alerte tampon, et redescend progressivement lorsque la trésorerie retrouve le seuil haut.

### 11.6 La jauge FIRE (règle des 4 %)
```
retirePatrimoine = (Σ placements financiers à l'année de retraite) + (Σ biens immobiliers)
fireRente         = retirePatrimoine × 4 % / 12
retireCharges     = charges projetées de l'année de retraite / 12
pourcentage FIRE  = min(100, fireRente / retireCharges × 100)
```

---

## 12. Récapitulatif visuel du cycle mensuel

```
        Revenus, primes,           Charges, dépenses ponctuelles,
        transferts programmés      impôt prélevé, régularisation
                │                              │
                ▼                              ▼
        ┌───────────────────────────────────────────────┐
        │         COMPTE COURANT (trésorerie)            │
        │   surveillé en continu entre seuil bas/haut    │
        └───────────────────────────────────────────────┘
                │                              ▲
       solde > seuil haut,               solde < seuil bas,
       en fin de mois :                  à tout moment :
       excédent réparti par              manque repris par
       priorité sweep,                   priorité sweep,
       dans la limite du plafond         jusqu'à vider si besoin
                │                              │
                ▼                              │
        ┌───────────────────────────────────────────────┐
        │      PLACEMENTS (croissance mensuelle selon    │
        │      le scénario actif, + versements           │
        │      programmés — sauf ceux mis en pause)      │
        └───────────────────────────────────────────────┘
                │
       un mois de rapatriement, ou un seuil d'alerte franchi
                │
                ▼
        Niveau de pause en cascade → certains versements
        programmés suspendus (chapitre 11.5), jusqu'à
        redescente progressive une fois la situation redressée
```

---

*Ce manuel décrit le fonctionnement complet de l'outil tel qu'actuellement implémenté. En cas de doute sur un calcul, le Journal des mouvements (Vue d'ensemble) et les vues d'Analyse du réel constituent vos références fiables.*