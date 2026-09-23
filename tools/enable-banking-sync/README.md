# Synchronisation Enable Banking -> MyFamilyBudget

Ce dossier contient un script Python qui recupere automatiquement les
transactions bancaires via l'API DSP2 Enable Banking et les importe dans
MyFamilyBudget, en reutilisant l'API locale existante
POST /bank-import/import (voir openapi.yaml, contrat
ImportBankTransactionsRequestDto).

Le script ne modifie pas le backend Java : il se contente d'appeler
l'API REST exposee par l'application (deja utilisee par le front pour
l'import CSV manuel), donc aucune recompilation n'est necessaire.

## Prerequis

- Python 3.10 ou plus recent, installe sur la machine Windows qui fait
  tourner MyFamilyBudget (MyFamilyBudget.bat).
- Une application Enable Banking en production (celle utilisee dans
  ton POC), avec sa cle privee .pem.
- MyFamilyBudget doit etre demarre (MyFamilyBudget.bat) avant
  l'execution du script, puisqu'il appelle l'API locale sur
  http://localhost:8080/api/v1 par defaut.

## Installation

    cd tools\enable-banking-sync
    py -m venv .venv
    .venv\Scripts\activate
    pip install -r requirements.txt
    copy enable_banking_config.example.json enable_banking_config.json

Puis modifie enable_banking_config.json :
- application_id : l'ID de ton application Enable Banking (production).
- private_key_path : chemin Windows complet vers ta cle privee
  (ex. C:\Users\VotreNom\enable-banking\private_key.pem).
- accounts : les UID de comptes recuperes a l'etape POST /sessions
  (les memes que dans ton script de POC).

enable_banking_config.json et sync_state.json sont ignores par
Git (.gitignore) car ils contiennent des identifiants et un chemin de
cle privee propres a ta machine.

## Utilisation

Test sans rien ecrire dans MyFamilyBudget :

    python sync_enable_banking.py --config enable_banking_config.json --dry-run

Synchronisation reelle :

    python sync_enable_banking.py --config enable_banking_config.json

## Fonctionnement

1. Authentification Enable Banking (JWT signe RS256 avec la cle privee),
   comme dans ton script de POC.
2. Pour chaque compte : recuperation du solde comptable (CLBD) et des
   transactions, avec pagination automatique (continuation_key) et
   recuperation incrementale (uniquement depuis la derniere transaction
   deja synchronisee, avec 3 jours de recouvrement de securite).
3. Seules les transactions status == "BOOK" (comptabilisees) sont
   importees ; les operations PDNG (en attente) ne sont pas envoyees
   pour eviter les incoherences avec le module "operations en attente"
   de MyFamilyBudget.
4. Chaque transaction est convertie en une ligne
   (date, libelle, type, montant) :
   - date : booking_date (format ISO YYYY-MM-DD, reconnu tel quel
     par BankImportCalculator.parseDateWithFormat).
   - libelle : concatenation de remittance_information.
   - type : bank_transaction_code.description s'il est renseigne par
     la banque, sinon une heuristique sur le libelle (VIR SEPA,
     PRLV SEPA, CARTE, RETRAIT DAB, ...). A ajuster dans
     TYPE_PATTERNS selon les libelles reels de ta banque : le champ
     bank_transaction_code etait vide sur l'exemple fourni, donc
     l'heuristique est la source principale pour l'instant.
   - montant : negatif si credit_debit_indicator == "DBIT".
5. Les lignes sont envoyees en une fois a
   POST /bank-import/import, avec colRoles = ["date", "label", "type", "amount"].
   La deduplication (cle date + libelle + montant) est geree cote
   serveur (BankImportCalculator.transactionDedupeKey) : relancer le
   script sur une periode deja importee ne cree pas de doublons.
6. L'etat (sync_state.json) memorise la derniere date de transaction
   importee par compte, pour limiter les appels a l'API Enable Banking
   au fil du temps.

## Planification automatique (Planificateur de taches Windows)

1. Ouvrir le Planificateur de taches -> Creer une tache de base.
2. Declencheur : quotidien, a une heure ou MyFamilyBudget est deja lance.
3. Action : Demarrer un programme
   - Programme : C:\chemin\vers\MyFamilyBudget\tools\enable-banking-sync\.venv\Scripts\python.exe
   - Arguments : sync_enable_banking.py --config enable_banking_config.json
   - Demarrer dans : C:\chemin\vers\MyFamilyBudget\tools\enable-banking-sync

## Limite connue : renouvellement du consentement DSP2

Le consentement donne a la banque lors de l'authentification forte a une
duree de validite limitee (generalement jusqu'a 90 jours selon les
banques). Ce script ne peut pas le renouveler automatiquement : si les
appels echouent en erreur d'autorisation, il faut repasser par le flux
/auth d'Enable Banking pour regenerer une session. Le script logue
une erreur explicite ("le consentement DSP2 a peut-etre expire") dans ce
cas, mais ne bloque pas la synchronisation des autres comptes.
