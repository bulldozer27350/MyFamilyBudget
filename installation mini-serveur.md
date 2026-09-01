# Guide pas-à-pas : transformer le mini-PC en serveur Docker + Tailscale

Ce guide part du principe que tu ne connais rien à Linux. Chaque étape indique
l'outil exact à utiliser et les commandes à copier-coller telles quelles.

**OS choisi : Ubuntu Server 26.04 LTS ("Resolute Raccoon")**
Pourquoi celui-là et pas une distribution "ultra-légère" type Alpine/Arch :
- Version serveur officielle = **pas d'interface graphique par défaut**, donc
  déjà très légère en RAM (comptez ~500 Mo-1 Go au repos).
- Support long terme (LTS) : mises à jour de sécurité jusqu'en 2031.
- Communauté immense → si un jour tu bloques sur une commande, la réponse
  existe déjà sur Internet à 99% des cas. C'est le meilleur compromis
  légèreté / facilité pour quelqu'un qui découvre Linux.

---

## Partie 0 — Ce qu'il te faut avant de commencer

- Une **clé USB** vide de 4 Go minimum (elle sera entièrement effacée).
- Ton PC Windows habituel, connecté à Internet.
- Le mini-PC, un écran, un clavier (juste pour l'installation initiale — après
  ça, plus besoin, tout se fera à distance depuis ton PC Windows).
- Un câble réseau (Ethernet) branché entre le mini-PC et ta box internet. Le
  Wi-Fi est possible mais l'Ethernet est plus simple et plus fiable pour un
  serveur.
- Compte Docker Hub existant avec ton image `MyFamilyBudget` déjà poussée.
- Un compte Tailscale (gratuit) — si tu n'en as pas : https://tailscale.com/
  → "Get started" → connexion avec Google/Microsoft/GitHub.

---

## Partie 1 — Créer la clé USB d'installation (sur ton PC Windows)

### Étape 1.1 — Télécharger l'image Ubuntu Server
1. Va sur : **https://ubuntu.com/download/server**
2. Clique sur le bouton de téléchargement de la version LTS mise en avant
   (ce sera "Ubuntu Server 26.04.x LTS"). Le fichier téléchargé est un `.iso`
   d'environ 2-3 Go.
3. Laisse-le dans ton dossier `Téléchargements`.

### Étape 1.2 — Télécharger Rufus (outil pour créer la clé bootable)
1. Va sur le site officiel : **https://rufus.ie**
2. Télécharge la version standard (pas besoin de l'installer, c'est un `.exe`
   qui se lance directement).

### Étape 1.3 — Créer la clé USB avec Rufus
1. Branche la clé USB sur ton PC Windows.
2. Lance `rufus-x.x.exe` (accepte l'admin si Windows le demande).
3. Dans Rufus :
   - **Périphérique** : sélectionne ta clé USB (vérifie bien la lettre/le nom,
     tout son contenu va être effacé).
   - **Sélection de démarrage** : clique sur "SÉLECTION" et choisis le fichier
     `.iso` d'Ubuntu Server téléchargé.
   - **Schéma de partition** : laisse `GPT` (c'est le standard des PC récents
     en UEFI).
   - **Système de destination** : `UEFI (non CSM)`.
   - Laisse le reste par défaut.
4. Clique sur **DÉMARRER**, puis **OK** pour confirmer l'effacement de la clé.
5. Attends la fin (barre verte à 100%), puis ferme Rufus et éjecte la clé
   proprement (icône "Retirer le périphérique" dans la barre Windows).

---

## Partie 2 — Installer Ubuntu Server sur le mini-PC

> Cette étape efface **tout** le disque dur du mini-PC (donc Windows 11 Pro
> disparaît). Tu n'as pas besoin de formater le disque toi-même avant : ça se
> fait automatiquement pendant l'installation, à l'étape "Storage".

### Étape 2.1 — Démarrer sur la clé USB
1. Branche la clé USB, l'écran et le clavier sur le mini-PC. Branche le câble
   Ethernet. Allume le mini-PC.
2. Dès l'allumage, martèle une touche pour ouvrir le **menu de boot** (pas le
   BIOS complet, juste le menu de choix du disque de démarrage). Sur la
   plupart des mini-PC c'est **F7**, **F11** ou **Échap** — l'écran de
   démarrage constructeur affiche en général la touche en bas ("Press F11 for
   Boot Menu" ou équivalent). Si rien ne s'affiche, essaie F2, Suppr ou F10
   pour aller dans le BIOS et cherche l'onglet "Boot".
3. Choisis la clé USB dans la liste (souvent nommée "UEFI: <nom de ta
   marque de clé>").

### Étape 2.2 — Suivre l'installateur Ubuntu Server
L'installateur (nom technique : Subiquity) pose une série d'écrans, navigation
au clavier (flèches + Entrée, Tab pour changer de zone). Voici quoi répondre à
chaque écran :

1. **Langue** : French (ou English si tu préfères des messages d'erreur plus
   faciles à chercher sur Google ensuite — au choix).
2. **Type d'installation** : "Ubuntu Server" (pas "minimized", garde la
   version normale qui inclut les outils de base utiles pour débuter).
3. **Réseau** : normalement déjà détecté en DHCP via le câble Ethernet
   (une adresse IP du style `192.168.1.xx` doit apparaître). Valide sans rien
   changer pour l'instant — on gérera une IP fixe plus tard si besoin, via ta
   box internet.
4. **Proxy** : laisse vide, valide.
5. **Miroir Ubuntu** : laisse l'adresse par défaut, valide.
6. **Configuration du disque (Storage)** : choisis **"Use an entire disk"**
   (utiliser tout le disque), sélectionne le disque du mini-PC. C'est cette
   étape qui efface tout et repartitionne proprement. Confirme.
7. **Profil (Profile setup)** : renseigne :
   - Ton nom complet (libre)
   - Nom du serveur (**hostname**), ex : `myfamilybudget-server`
   - Nom d'utilisateur (ex : `marco`) — **retiens-le**, tu en auras besoin à
     chaque connexion.
   - Mot de passe — **retiens-le aussi**, note-le dans un gestionnaire de
     mots de passe.
8. **Upgrade** : "No" suffit (tu mettras à jour juste après).
9. **SSH Setup** : coche impérativement **"Install OpenSSH server"**. C'est
   ce qui te permettra de piloter le serveur depuis ton PC Windows sans écran
   ni clavier ensuite. Ne mets pas de clé GitHub/Launchpad si tu n'en as pas,
   ce n'est pas obligatoire.
10. **Featured Server Snaps** : ne coche rien, valide (on installera Docker
    nous-mêmes, à jour, plus tard).
11. L'installation se lance (quelques minutes). Un message "Install complete"
    apparaît en bas → sélectionne **Reboot Now**.
12. Quand il te le demande, retire la clé USB puis appuie sur Entrée.

### Étape 2.3 — Relever l'adresse IP du serveur
Après le redémarrage, un écran de connexion en mode texte (noir) apparaît. Il
affiche en général les adresses IP des interfaces réseau juste au-dessus de
l'invite de connexion, du type :

```
eth0: 192.168.1.42
```

**Note bien cette adresse IP** (ici `192.168.1.42`), tu vas t'en servir depuis
Windows. Connecte-toi une fois localement (nom d'utilisateur + mot de passe
définis à l'étape 2.2) juste pour vérifier que ça fonctionne, puis tu peux
débrancher écran et clavier : tout le reste se fait à distance.

> Conseil : ouvre l'interface d'administration de ta box internet (souvent
> `192.168.1.1`) et réserve cette IP pour l'adresse MAC du mini-PC (option
> "bail DHCP statique" / "réservation IP"), pour qu'elle ne change jamais.
> Ce n'est pas obligatoire pour démarrer, tu peux le faire plus tard.

---

## Partie 3 — Se connecter au serveur depuis Windows

Windows 11 a un client SSH intégré, pas besoin d'installer PuTTY.

1. Ouvre **PowerShell** ou **Windows Terminal** (clic droit menu Démarrer →
   "Terminal" ou "Windows PowerShell").
2. Tape (remplace `marco` par ton nom d'utilisateur et l'IP par la tienne) :

```powershell
ssh marco@192.168.1.42
```

3. La première fois, il demande de confirmer l'empreinte du serveur → tape
   `yes` puis Entrée.
4. Entre le mot de passe défini à l'installation (rien ne s'affiche pendant
   la saisie, c'est normal).

Tu es maintenant connecté au serveur. **Toutes les commandes des parties
suivantes se tapent dans cette fenêtre SSH**, sauf mention contraire.

---

## Partie 4 — Mise à jour du système

```bash
sudo apt update && sudo apt upgrade -y
```

Si une fenêtre bleue demande de redémarrer les services, valide par défaut
(Entrée / OK). Si le système demande un redémarrage complet :

```bash
sudo reboot
```

Attends 30 secondes puis reconnecte-toi en SSH (étape Partie 3).

---

## Partie 5 — Installer Docker Engine (méthode officielle)

Copie-colle ce bloc entier d'un coup dans le terminal SSH (chaque ligne sera
exécutée dans l'ordre) :

```bash
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Vérifie que Docker tourne :

```bash
sudo systemctl enable --now docker
sudo docker run hello-world
```

Tu dois voir un message commençant par `Hello from Docker!`. Si oui, Docker
est prêt.

### Autoriser ton utilisateur à taper `docker` sans `sudo` (confort)

```bash
sudo usermod -aG docker $USER
```

Puis déconnecte-toi et reconnecte-toi en SSH pour que ça prenne effet
(tape `exit`, puis refais la commande `ssh marco@192.168.1.42` de la Partie 3).

---

## Partie 6 — Installer Tailscale

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

La commande affiche une URL du type `https://login.tailscale.com/a/xxxxx`.

1. Copie cette URL.
2. Colle-la dans un navigateur **sur ton PC Windows** (pas besoin d'écran sur
   le mini-PC).
3. Connecte-toi avec le même compte que celui utilisé pour installer
   Tailscale sur tes autres appareils (PC, téléphone...).
4. Retourne dans le terminal SSH : le message doit confirmer que la machine
   est connectée au tailnet.

Récupère l'adresse Tailscale de ton serveur :

```bash
tailscale ip -4
```

Note cette adresse (ex : `100.x.x.x`) — c'est celle qui te permettra
d'accéder au serveur **depuis n'importe où dans le monde**, sans ouvrir le
moindre port sur ta box internet, une fois que Tailscale est aussi installé
et connecté sur ton PC/téléphone.

---

## Partie 7 — Déployer MyFamilyBudget depuis Docker Hub

### Étape 7.1 — Créer le dossier de travail sur le serveur

```bash
mkdir -p ~/myfamilybudget
```

### Étape 7.2 — Envoyer les deux fichiers nécessaires depuis Windows

Tu n'as **pas besoin de cloner tout le dépôt Git sur le serveur** : l'image
Docker Hub contient déjà l'application compilée. Il te faut seulement deux
fichiers : `docker-compose.prod.yml` et `.env`.

Sur ton PC Windows, ouvre un **nouveau** PowerShell (pas celui connecté en
SSH), place-toi dans le dossier où se trouve ton dépôt `MyFamilyBudget`
cloné/patché, puis :

```powershell
scp docker-compose.prod.yml marco@192.168.1.42:~/myfamilybudget/
scp .env.example marco@192.168.1.42:~/myfamilybudget/.env
```

(Adapte le nom d'utilisateur et l'IP.)

### Étape 7.3 — Configurer le fichier .env sur le serveur

Retourne dans ta fenêtre SSH (celle connectée au serveur) :

```bash
nano ~/myfamilybudget/.env
```

Dans l'éditeur `nano` :
- Remplace `TON_PSEUDO_DOCKERHUB/myfamilybudget:latest` par le vrai nom de
  ton image (ex : `marco27350/myfamilybudget:latest`).
- Remplace `change-moi` par un vrai mot de passe PostgreSQL.
- Laisse le reste tel quel, sauf si tu veux changer les ports.

Pour sauvegarder et quitter nano : `Ctrl+O` puis `Entrée` (sauvegarder),
puis `Ctrl+X` (quitter).

### Étape 7.4 — Si ton image Docker Hub est privée

Si tu as poussé l'image en **public**, ignore cette étape. Si elle est
**privée**, connecte Docker à ton compte Docker Hub :

```bash
docker login
```

(Entre ton identifiant et ton mot de passe/token Docker Hub.)

### Étape 7.5 — Lancer l'application

```bash
cd ~/myfamilybudget
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Vérifie que tout tourne :

```bash
docker compose -f docker-compose.prod.yml ps
```

Les deux services (`myfamilybudget-db` et `myfamilybudget-app`) doivent être
`Up` (le second peut mettre 30-60 secondes à devenir "healthy" le temps que
Spring Boot démarre).

Teste que l'application répond :

```bash
curl -I http://localhost:8080
```

Tu dois voir une ligne `HTTP/1.1 200` (ou proche).

---

## Partie 8 — Accéder à l'application depuis ton PC Windows

- **Depuis le même réseau local** : `http://192.168.1.42:8080` dans ton
  navigateur (adapte l'IP).
- **Depuis n'importe où (via Tailscale)**, une fois Tailscale installé et
  connecté sur ton PC Windows (client officiel : https://tailscale.com/download/windows) :
  `http://100.x.x.x:8080` (l'adresse Tailscale relevée en Partie 6).

---

## Partie 9 — Aide-mémoire des commandes utiles (à garder sous la main)

| Besoin | Commande (à taper en SSH dans `~/myfamilybudget`) |
|---|---|
| Voir les logs de l'appli | `docker compose -f docker-compose.prod.yml logs -f app` |
| Redémarrer l'appli | `docker compose -f docker-compose.prod.yml restart app` |
| Mettre à jour vers une nouvelle image poussée sur Docker Hub | `docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d` |
| Tout arrêter | `docker compose -f docker-compose.prod.yml down` |
| Tout arrêter (avec suppression des données !) | `docker compose -f docker-compose.prod.yml down -v` ⚠️ efface la base |
| Se reconnecter en SSH | `ssh marco@192.168.1.42` |
| Voir l'IP Tailscale du serveur | `tailscale ip -4` |

---

## Notes de sécurité (rapides, pas bloquantes pour démarrer)

- Comme tu passes par **Tailscale** pour l'accès distant, tu n'as **aucun
  port à ouvrir sur ta box internet** — le serveur n'est pas exposé sur
  Internet public, seuls tes appareils connectés à ton tailnet peuvent le
  joindre. C'est nettement plus sûr qu'une redirection de port classique.
- Change bien le mot de passe PostgreSQL par défaut dans `.env` (fait à
  l'étape 7.3).
- Le pare-feu Ubuntu (`ufw`) n'est pas activé par défaut sur ce genre
  d'installation ; ce n'est pas nécessaire pour démarrer étant donné l'usage
  via Tailscale, mais évite d'activer `ufw` en cohabitation avec Docker sans
  te renseigner d'abord (Docker et `ufw` interagissent mal si mal configurés
  — mieux vaut laisser de côté pour l'instant).

---

## Concernant le patch `docker-compose.prod.yml`

Le patch fourni séparément (`0001-docker-hub-deploy.patch`) ajoute ce
nouveau fichier `docker-compose.prod.yml` (utilisé uniquement sur le
mini-PC, avec `image:` au lieu de `build:`) sans toucher au
`docker-compose.yml` existant, que tu peux garder pour continuer à builder
et tester en local sur ta machine Windows. Pense à bien remplacer
`TON_PSEUDO_DOCKERHUB/myfamilybudget:latest` par le vrai nom de ton image
avant de pousser/déployer.