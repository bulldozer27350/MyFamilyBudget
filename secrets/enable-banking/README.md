# Certificat Enable Banking

Ce dossier est monte en lecture seule dans le conteneur MyFamilyBudget
(voir `docker-compose.prod.yml`, variable `MYFAMILYBUDGET_ENABLE_BANKING_SECRETS_DIR`).

Pour activer la synchronisation bancaire automatique, deposer ici le
certificat prive de ton application Enable Banking (production), sous
le nom `private_key.pem`, au format PKCS#8
(`-----BEGIN PRIVATE KEY-----`).

Ce dossier reste volontairement vide dans le depot Git (voir
`.gitignore` local) : le fichier `private_key.pem` ne doit jamais etre
commite ni publie. Sans lui, la synchronisation est simplement
desactivee au demarrage de l'application (voir les logs).
