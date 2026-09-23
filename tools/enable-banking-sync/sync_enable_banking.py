"""
Synchronisation automatique des transactions bancaires (Enable Banking / DSP2)
vers MyFamilyBudget, via l'API locale POST /bank-import/import.

Ce script :
  1. Authentifie l'application Enable Banking (JWT signe avec la cle privee).
  2. Recupere les soldes et transactions de chaque compte connecte
     (avec pagination via continuation_key et recuperation incrementale
     depuis la derniere synchronisation).
  3. Convertit chaque transaction Enable Banking vers le format attendu
     par le contrat OpenAPI de MyFamilyBudget (ImportBankTransactionsRequestDto :
     rawRows / colRoles), en devinant un "type" d'operation (VIR, PRLV, CB, ...)
     a partir des libelles, faute de bank_transaction_code fiable cote banque.
  4. Poste le resultat sur l'API locale (http://localhost:8080/api/v1/bank-import/import
     par defaut) ; la deduplication (date + libelle + montant) est geree cote serveur.
  5. Memorise, par compte, la date de la derniere transaction importee dans un
     fichier d'etat JSON, pour ne requeter que les nouveautes lors des executions
     suivantes.

Usage :
    python sync_enable_banking.py --config enable_banking_config.json
    python sync_enable_banking.py --config enable_banking_config.json --dry-run

A planifier via le Planificateur de taches Windows (voir README.md).
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import sys
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta
from pathlib import Path
from typing import Any

import jwt
import requests

LOG = logging.getLogger("enable_banking_sync")

ENABLE_BANKING_BASE_URL = "https://api.enablebanking.com"

# Roles de colonnes attendus par ImportBankTransactionsRequestDto.colRoles
# (voir back/server/.../BankImportCalculator.importTransactions : indexOf("date"/"label"/"type"/"amount"))
COL_ROLES = ["date", "label", "type", "amount"]

# Recouvrement de securite (en jours) applique a la date de derniere synchro,
# pour ne pas rater une transaction "PDNG" (en attente) devenue "BOOK" (comptabilisee)
# avec une booking_date legerement differente. La dedup cote serveur absorbe les doublons.
OVERLAP_DAYS = 3

# Heuristique de detection du type d'operation a partir du libelle (remittance_information),
# faute de bank_transaction_code exploitable pour toutes les banques. A ajuster selon
# les libelles reellement observes sur tes comptes.
TYPE_PATTERNS: list[tuple[str, str]] = [
    (r"^VIR(EMENT)?\s+SEPA\s+RECU", "VIR RECU"),
    (r"^VIR(EMENT)?\s+SEPA", "VIR SEPA"),
    (r"^VIR(EMENT)?\s+(INTERNE|CPTE\s*A\s*CPTE|DE\s+COMPTE\s+A\s+COMPTE)", "VIR CPTE A CPTE"),
    (r"^VIR(EMENT)?\b", "VIREMENT"),
    (r"^PRLV(EVEMENT)?\s+SEPA", "PRLV SEPA"),
    (r"^PRLV(EVEMENT)?\b", "PRELEVEMENT"),
    (r"^(CB|CARTE)\b", "CARTE"),
    (r"^RET(RAIT)?\s+DAB", "RETRAIT DAB"),
    (r"^CHQ|^CHEQUE", "CHEQUE"),
    (r"^FRAIS\b", "FRAIS BANCAIRES"),
    (r"^COTIS(ATION)?\b", "COTISATION"),
    (r"^VERSEMENT\b", "VERSEMENT"),
    (r"^INTERETS?\b", "INTERETS"),
]


@dataclass
class AccountConfig:
    label: str
    uid: str


@dataclass
class SyncConfig:
    application_id: str
    private_key_path: str
    accounts: list[AccountConfig]
    api_base_url: str
    state_file: str


def load_config(path: str) -> SyncConfig:
    with open(path, "r", encoding="utf-8") as f:
        raw = json.load(f)
    accounts = [AccountConfig(label=a["label"], uid=a["uid"]) for a in raw["accounts"]]
    return SyncConfig(
        application_id=raw["application_id"],
        private_key_path=raw["private_key_path"],
        accounts=accounts,
        api_base_url=raw.get("api_base_url", "http://localhost:8080/api/v1"),
        state_file=raw.get("state_file", "sync_state.json"),
    )


def get_enable_banking_token(application_id: str, private_key_path: str) -> str:
    with open(private_key_path, "r", encoding="utf-8") as f:
        private_key = f.read()
    now = int(time.time())
    payload = {
        "iss": "enablebanking.com",
        "aud": "api.enablebanking.com",
        "iat": now,
        "exp": now + 3600,
    }
    return jwt.encode(payload, private_key, algorithm="RS256", headers={"kid": application_id})


def load_state(state_file: str) -> dict[str, Any]:
    path = Path(state_file)
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_state(state_file: str, state: dict[str, Any]) -> None:
    path = Path(state_file)
    with path.open("w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False, indent=2, sort_keys=True)


def fetch_balances(token: str, uid: str) -> list[dict[str, Any]]:
    resp = requests.get(
        f"{ENABLE_BANKING_BASE_URL}/accounts/{uid}/balances",
        headers={"Authorization": f"Bearer {token}"},
        timeout=30,
    )
    resp.raise_for_status()
    return resp.json().get("balances", [])


def fetch_all_transactions(token: str, uid: str, date_from: str | None) -> list[dict[str, Any]]:
    """Recupere toutes les transactions d'un compte, en suivant la pagination
    (continuation_key) jusqu'a ce que la banque n'en renvoie plus."""
    transactions: list[dict[str, Any]] = []
    params: dict[str, str] = {}
    if date_from:
        params["date_from"] = date_from

    continuation_key: str | None = None
    while True:
        query = dict(params)
        if continuation_key:
            query["continuation_key"] = continuation_key

        resp = requests.get(
            f"{ENABLE_BANKING_BASE_URL}/accounts/{uid}/transactions",
            headers={"Authorization": f"Bearer {token}"},
            params=query,
            timeout=30,
        )
        resp.raise_for_status()
        payload = resp.json()

        transactions.extend(payload.get("transactions", []))
        continuation_key = payload.get("continuation_key")
        if not continuation_key:
            break

    return transactions


def guess_type(label: str) -> str:
    upper = label.upper()
    for pattern, type_value in TYPE_PATTERNS:
        if re.search(pattern, upper):
            return type_value
    return "AUTRE"


def map_transaction(tx: dict[str, Any]) -> tuple[str, str, str, str] | None:
    """Convertit une transaction Enable Banking en ligne (date, libelle, type, montant)
    dans le format attendu par ImportBankTransactionsRequestDto.rawRows."""
    booking_date = tx.get("booking_date") or tx.get("value_date")
    if not booking_date:
        LOG.warning("Transaction sans date ignoree : %s", tx.get("entry_reference"))
        return None

    remittance = tx.get("remittance_information") or []
    label = " ".join(part.strip() for part in remittance if part).strip()
    if not label:
        label = tx.get("entry_reference", "") or "(sans libelle)"

    amount_raw = (tx.get("transaction_amount") or {}).get("amount")
    if amount_raw is None:
        LOG.warning("Transaction sans montant ignoree : %s", tx.get("entry_reference"))
        return None
    amount = float(amount_raw)
    if tx.get("credit_debit_indicator") == "DBIT":
        amount = -amount

    # bank_transaction_code.description est prioritaire quand la banque le renseigne ;
    # a defaut, on retombe sur l'heuristique de libelle (voir TYPE_PATTERNS).
    code_description = (tx.get("bank_transaction_code") or {}).get("description")
    tx_type = code_description.strip() if code_description else guess_type(label)

    return booking_date, label, tx_type, f"{amount:.2f}"


def post_import(api_base_url: str, rows: list[tuple[str, str, str, str]]) -> dict[str, Any]:
    body = {
        "rawRows": [list(row) for row in rows],
        "colRoles": COL_ROLES,
        "mapping": {
            "delimiter": ";",
            "dateFormat": "YYYY-MM-DD",
            "hasHeader": False,
        },
    }
    resp = requests.post(f"{api_base_url}/bank-import/import", json=body, timeout=30)
    resp.raise_for_status()
    return resp.json()


def latest_booking_date(rows: list[tuple[str, str, str, str]], fallback: str | None) -> str | None:
    dates = [row[0] for row in rows if row[0]]
    if not dates:
        return fallback
    return max(dates + ([fallback] if fallback else []))


def run(config: SyncConfig, dry_run: bool) -> int:
    token = get_enable_banking_token(config.application_id, config.private_key_path)
    state = load_state(config.state_file)
    exit_code = 0

    for account in config.accounts:
        LOG.info("=== %s ===", account.label)
        account_state = state.get(account.uid, {})
        last_sync_date = account_state.get("last_booking_date")

        date_from = None
        if last_sync_date:
            overlap_start = date.fromisoformat(last_sync_date) - timedelta(days=OVERLAP_DAYS)
            date_from = overlap_start.isoformat()

        try:
            balances = fetch_balances(token, account.uid)
            raw_transactions = fetch_all_transactions(token, account.uid, date_from)
        except requests.HTTPError as exc:
            LOG.error(
                "Echec de recuperation pour %s (le consentement DSP2 a peut-etre expire) : %s",
                account.label, exc,
            )
            exit_code = 1
            continue

        booked_balance = next(
            (b for b in balances if b.get("balance_type") == "CLBD"), None
        )
        if booked_balance:
            LOG.info(
                "Solde comptable : %s %s",
                booked_balance["balance_amount"]["amount"],
                booked_balance["balance_amount"]["currency"],
            )

        rows = []
        for tx in raw_transactions:
            if tx.get("status") != "BOOK":
                # On n'importe que les operations comptabilisees ; les operations
                # en attente (PDNG) sont deja couvertes par le module "operations
                # en attente" cote MyFamilyBudget.
                continue
            mapped = map_transaction(tx)
            if mapped:
                rows.append(mapped)

        LOG.info("%d transaction(s) comptabilisee(s) recuperee(s)", len(rows))

        if not rows:
            continue

        if dry_run:
            for row in rows:
                LOG.info("[dry-run] %s | %-20s | %s | %s", *row)
            continue

        try:
            summary = post_import(config.api_base_url, rows)
        except requests.HTTPError as exc:
            LOG.error("Echec de l'import vers MyFamilyBudget pour %s : %s", account.label, exc)
            exit_code = 1
            continue

        LOG.info(
            "Import termine : %s importee(s), %s doublon(s) ignore(s), %s categorisee(s) automatiquement",
            summary.get("imported"), summary.get("duplicates"), summary.get("autoCategorized"),
        )

        state[account.uid] = {
            "last_booking_date": latest_booking_date(rows, last_sync_date),
            "last_sync_at": datetime.now().isoformat(timespec="seconds"),
        }
        save_state(config.state_file, state)

    return exit_code


def main() -> int:
    parser = argparse.ArgumentParser(description="Synchronisation Enable Banking -> MyFamilyBudget")
    parser.add_argument("--config", default="enable_banking_config.json", help="Chemin du fichier de configuration JSON")
    parser.add_argument("--dry-run", action="store_true", help="N'ecrit rien dans MyFamilyBudget, affiche seulement ce qui serait importe")
    parser.add_argument("--verbose", action="store_true", help="Active les logs de niveau DEBUG")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    try:
        config = load_config(args.config)
    except (FileNotFoundError, KeyError, json.JSONDecodeError) as exc:
        LOG.error("Configuration invalide (%s) : %s", args.config, exc)
        return 2

    return run(config, dry_run=args.dry_run)


if __name__ == "__main__":
    sys.exit(main())
