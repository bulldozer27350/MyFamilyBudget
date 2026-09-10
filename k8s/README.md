# MyFamilyBudget sur Kubernetes (minikube) - POC d'apprentissage

Ce dossier est un POC pedagogique : il ne remplace pas `docker-compose.prod.yml`
tant que tu ne l'as pas valide en conditions reelles. L'objectif est
d'apprendre les mecanismes Kubernetes utilises en prod (rolling update,
probes, Service, gestion des secrets) sur un cas d'usage volontairement
simple (1-2 utilisateurs, pas de contrainte de disponibilite).

## Architecture retenue

- **Kubernetes** : minikube (cluster mono-noeud, driver `docker`).
- **App** : `Deployment` Kubernetes, 1 replica, strategie `RollingUpdate`
  (`maxSurge: 1`, `maxUnavailable: 0`).
- **Base de donnees** : reste **hors du cluster**, en conteneur Docker
  classique via `docker-compose.prod.yml` (service `db` uniquement -
  retire `app` et `watchtower` de ce fichier une fois la migration
  validee, ou garde-les commentes pour revenir en arriere facilement).
- **Detection de nouvelle image** : [Keel](https://keel.sh) remplace
  Watchtower. Meme principe (polling Docker Hub, 5 min max), mais qui
  agit sur un objet `Deployment` Kubernetes plutot que sur un conteneur
  Docker directement.

Cette repartition (DB hors cluster, app dans le cluster) est un pattern
hybride reel : un cluster Kubernetes ne gere que ce qui est deploye
*dans* lui - tout le reste (ici la base) est "externe" et rejoint via un
`Service` sans selecteur + un objet `Endpoints` explicite.

## Pourquoi pas de StatefulSet pour Postgres tout de suite

Tu pourras migrer la base dans le cluster plus tard (StatefulSet +
PersistentVolumeClaim) une fois a l'aise avec les bases (Deployment,
Service, probes). Pour une base unique contenant les seules donnees du
foyer, la garder hors cluster limite le risque pendant l'apprentissage :
moins de nouvelles pieces mobiles autour de tes donnees financieres.

## Etape 1 - Installer minikube et kubectl

```powershell
# Sur le mini-PC (Linux) - via le gestionnaire de paquets ou :
curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube /usr/local/bin/

curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install kubectl /usr/local/bin/
```

## Etape 2 - Demarrer minikube

Le flag `--ports` publie le NodePort 30080 directement sur l'IP de la
machine hote (driver `docker`), pour qu'il reste joignable via Tailscale
exactement comme le port 8080 aujourd'hui.

```bash
minikube start --driver=docker --ports=30080:30080
```

A mettre dans un service systemd (`minikube start ...` au demarrage) si
tu veux que ca survive a un reboot du mini-PC, comme Watchtower
aujourd'hui via `restart: unless-stopped`.

## Etape 3 - Adresse IP de l'hote (pour joindre Postgres)

Avec le driver `docker`, minikube tourne lui-meme dans un conteneur :
depuis l'interieur, l'hote est vu comme une passerelle reseau.

```bash
minikube ssh -- ip route show default
# ex: default via 192.168.49.1 dev eth0 -> HOST_IP = 192.168.49.1
```

Remplace `<HOST_IP>` dans `k8s/postgres-external.yaml` par cette valeur
(elle est stable tant que tu ne recrees pas le profil minikube).

Raccourci propre a minikube (a la place de tout ce fichier) :
`host.minikube.internal` resout automatiquement vers l'hote. Le choix
Service+Endpoints ci-dessus est volontairement plus verbeux car c'est le
pattern qui fonctionne aussi sur un vrai cluster (k3s, cloud managed) -
`host.minikube.internal` ne fonctionnerait que sur minikube.

## Etape 4 - Namespace et secret (jamais commit un mot de passe)

```bash
kubectl apply -f k8s/namespace.yaml

kubectl create secret generic myfamilybudget-db-credentials \
  --namespace myfamilybudget \
  --from-literal=username=myfamilybudget \
  --from-literal=password='<TON_MOT_DE_PASSE_POSTGRES>'
```

Le secret n'est **pas** dans un fichier YAML versionne : c'est volontaire.
Un YAML de Secret contient le mot de passe encode en base64 - trivial a
decoder, donc pas plus sur qu'un fichier en clair s'il finit sur GitHub.
Plus tard, si tu veux versionner les secrets proprement, regarde Sealed
Secrets ou l'External Secrets Operator (etape d'apprentissage suivante).

## Etape 5 - Appliquer les manifests

```bash
kubectl apply -f k8s/postgres-external.yaml
kubectl apply -f k8s/app-deployment.yaml
kubectl apply -f k8s/app-service.yaml

kubectl get pods -n myfamilybudget -w
```

Test manuel : `curl http://<HOST_IP>:30080/myfamilybudget/heartbeat`
(ou l'IP Tailscale du mini-PC).

## Etape 6 - Installer Keel (detection auto de nouvelle image)

Le projet Keel recommande desormais Helm, avec en alternative un jeu de
manifests statiques numerotes (le vieux fichier unique
`deployment/deployment-rbac.yaml` n'existe plus). Sans Helm, applique-les
dans l'ordre :

```bash
kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/00-namespace.yaml
kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/10-service-account.yaml
kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/11-clusterrole.yaml
kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/12-clusterrolebinding.yaml

# Ne pas appliquer 20-secret.yaml (mot de passe en clair dans le fichier) :
kubectl -n keel create secret generic keel \
  --from-literal=BASIC_AUTH_PASSWORD='<ton mot de passe>'

kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/30-deployment.yaml
kubectl apply -f https://raw.githubusercontent.com/keel-hq/keel/master/docs/manifests/keel/40-service.yaml
```

Keel tourne dans son propre namespace `keel`, distinct de
`myfamilybudget` : son ClusterRole lui permet de surveiller des
Deployments dans n'importe quel namespace, dont le notre, sans
configuration supplementaire. Il lit les annotations `keel.sh/*` posees
sur `app-deployment.yaml` - rien d'autre a faire, comme Watchtower avec
son label aujourd'hui.

Verification : `kubectl -n keel get pods -l app=keel` doit montrer le
pod Running.

## Etape 7 - Observer un rolling update en conditions reelles

```bash
# Provoque un rollout manuellement (sans attendre Keel), pour voir le
# mecanisme decrit au debut :
kubectl rollout restart deployment/myfamilybudget-app -n myfamilybudget

# Dans un autre terminal, pendant la commande ci-dessus :
kubectl get pods -n myfamilybudget -w
# Tu dois voir le pod "new" apparaitre en Pending puis Running/1-1 Ready
# AVANT que le pod "old" ne passe en Terminating.

kubectl rollout status deployment/myfamilybudget-app -n myfamilybudget
```

Rollback si la nouvelle version est mauvaise :

```bash
kubectl rollout undo deployment/myfamilybudget-app -n myfamilybudget
```

## Point d'architecte : migrations de base pendant le rollout

Avec `maxSurge: 1` / `maxUnavailable: 0`, l'ancien pod (version N) et le
nouveau (version N+1) interrogent **la meme base en meme temps** pendant
quelques secondes. `ddl-auto: update` d'Hibernate applique les
changements de schema au demarrage du pod N+1 - si ce schema n'est pas
retro-compatible avec le code de la version N encore en train de
repondre au trafic, tu casses la version N pendant la transition.

Regle a suivre a partir de maintenant : pattern "expand/contract" -
une colonne/table est **ajoutee** dans une version, et n'est
**supprimee/renommee** que dans une version ulterieure, jamais dans le
meme déploiement qu'un changement de code qui la rend obligatoire.

## Pour aller plus loin (prochaines etapes d'apprentissage)

- **Blue-green explicite** : deux `Deployment` (v1/v2) + bascule
  manuelle du `selector` du `Service` - plus de controle qu'un
  RollingUpdate, utile pour des tests avant bascule complete.
- **GitOps (Argo CD / Flux)** : remplacer le "push" de Keel par un
  modele "pull" ou le cluster va lui-meme chercher l'etat desire depuis
  Git - competence tres recherchee pour un architecte en 2026.
- **Ingress + cert-manager** : remplacer le NodePort par un vrai nom de
  domaine avec HTTPS automatique.
- **Observabilite** : Prometheus + Grafana pour visualiser CPU/memoire
  par pod, utile pour affiner les `resources.limits` ci-dessus.
