# Liquibase — R-01 (`24-backlog-reste-a-faire.md`)

## Lot A (ce patch)

- Dépendance `liquibase-core` ajoutée à `server`, `spring.liquibase.enabled: false` dans les trois
  profils (`application.yml`, `application-docker.yml`, `application.yml` de test) : aucun changement
  de comportement, `ddl-auto` reste la seule source de schéma.
- Un changelog maître (`db.changelog-master.xml`) qui inclut un changelog vide par silo, chacun dans
  les ressources du module `*-core` propriétaire des tables (ou de `persistence` pour les tables
  encore legacy : `budget_data`, `settings`, `placement`, `placement_history_entry`).
- Un profil Maven `liquibase-baseline`, désactivé par défaut (jamais déclenché par `mvn clean test`),
  qui porte `liquibase-maven-plugin` pour le lot B. Son `outputChangeLogFile` est codé en dur dans le
  `pom.xml` (`${project.build.directory}/baseline.xml`) : un `-Dliquibase.outputChangeLogFile=...` en
  ligne de commande est ignoré, la sortie est toujours `target/baseline.xml` (renommer après coup).

## Lot B — fait en « reset propre » (DA-16 de `23-registre-decisions-actives.md`)

L'agent n'a ni Maven ni accès réseau à Maven Central dans son bac à sable (DA-13) : les deux
commandes ci-dessous ont été exécutées par l'exploitant, sur un poste avec Maven, pointées
directement sur le H2 de dev et le PostgreSQL de prod existants (lecture seule, `generateChangeLog`
n'écrit rien) :
```
mvn -f back/server/pom.xml liquibase:generateChangeLog -Pliquibase-baseline ^
  -Dliquibase.url=jdbc:h2:file:./data/myfamilybudget -Dliquibase.username=sa -Dliquibase.password=

mvn -f back/server/pom.xml liquibase:generateChangeLog -Pliquibase-baseline ^
  -Dliquibase.url=jdbc:postgresql://<hôte>:5432/myfamilybudget ^
  -Dliquibase.username=*** -Dliquibase.password=*** -Dliquibase.driver=org.postgresql.Driver
```

Comparaison des deux sorties : H2 et PostgreSQL ont divergé (historiques de migration différents),
**pas seulement de la dette commune aux deux** — d'où l'écart avec la section « Lot A (à faire) »
d'origine, qui prévoyait de comparer puis de répartir tel quel. 20 tables ont été écartées de la
baseline retenue (aucune n'a plus d'entité JPA dans le code actuel, voir DA-16 pour le détail) ; les
36 restantes se répartissent sans reste dans les 11 changelogs de silo, sans aucune clé étrangère
inter-silo. `spring.liquibase.enabled: true` et `spring.jpa.hibernate.ddl-auto: validate` sont
basculés (profils `application.yml` et `application-docker.yml`, pas celui de test).

Conséquence : pas de `changelogSync` (qui aurait marqué la baseline comme déjà appliquée sur un
schéma existant) — la base de prod doit au contraire être **vidée** avant le premier démarrage avec
ce patch, pour que Liquibase crée réellement le schéma propre depuis la baseline. `changelogSync` sur
une base non vidée échouerait de toute façon : les 20 tables écartées y sont encore présentes, hors
du suivi Liquibase.

`LegacySchemaCleanup` (nettoyage dynamique des clés étrangères obsolètes au démarrage) est retiré
dans ce même patch plutôt que porté en script : il dépend du métamodèle JPA (`EntityManagerFactory`),
indisponible au moment où un `CustomTaskChange` Liquibase s'exécute — et un schéma neuf n'a de toute
façon plus de FK obsolète à nettoyer.

## Déploiement — reste à faire, côté exploitant (mini-PC, pas de script agent possible)

1. Exporter la sauvegarde JSON (`GET /budget`) et faire un `pg_dump -Fc` de la base de prod (filet de
   secours, indépendant du JSON).
2. Arrêter `myfamilybudget-app`, vider entièrement la base PostgreSQL de prod (le schéma, pas
   seulement les données applicatives : les 20 tables legacy doivent disparaître aussi).
3. Déployer l'image construite à partir de ce patch. Liquibase crée le schéma propre depuis la
   baseline au démarrage (`ddl-auto: validate` ne fait alors que confirmer la cohérence).
4. Réimporter la sauvegarde JSON (`POST /budget/import`) ; vérifier avant de supprimer les sauvegardes.

Les changesets suivants (ajouts/retraits de colonnes des lots B des silos R-20 à R-61) viennent
s'ajouter dans le changelog du silo concerné, jamais dans le changelog maître.
