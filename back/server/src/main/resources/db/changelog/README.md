# Liquibase — R-01 (`24-backlog-reste-a-faire.md`)

## Lot A (ce patch)

- Dépendance `liquibase-core` ajoutée à `server`, `spring.liquibase.enabled: false` dans les trois
  profils (`application.yml`, `application-docker.yml`, `application.yml` de test) : aucun changement
  de comportement, `ddl-auto` reste la seule source de schéma.
- Un changelog maître (`db.changelog-master.xml`) qui inclut un changelog vide par silo, chacun dans
  les ressources du module `*-core` propriétaire des tables (ou de `persistence` pour les tables
  encore legacy : `budget_data`, `settings`, `placement`, `real_estate`, `asset_category`,
  `placement_history_entry`).
- Un profil Maven `liquibase-baseline`, désactivé par défaut (jamais déclenché par `mvn clean test`),
  qui porte `liquibase-maven-plugin` pour le lot B.

## Lot B (à faire, hors de ce patch)

1. Arrêter l'application. Générer la baseline sur la base H2 de dev :
   ```
   mvn -f back/server/pom.xml liquibase:generateChangeLog -Pliquibase-baseline ^
     -Dliquibase.url=jdbc:h2:file:./data/myfamilybudget ^
     -Dliquibase.username=sa -Dliquibase.password= ^
     -Dliquibase.outputChangeLogFile=target/baseline-h2.xml
   ```
2. Faire de même sur PostgreSQL (prod), avec les identifiants du `.env` du mini-PC :
   ```
   mvn -f back/server/pom.xml liquibase:generateChangeLog -Pliquibase-baseline ^
     -Dliquibase.url=jdbc:postgresql://localhost:5432/myfamilybudget ^
     -Dliquibase.username=myfamilybudget -Dliquibase.password=*** ^
     -Dliquibase.driver=org.postgresql.Driver ^
     -Dliquibase.outputChangeLogFile=target/baseline-postgres.xml
   ```
3. Comparer les deux sorties (elles doivent décrire le même schéma à ce stade) ; répartir les tables
   entre les 11 changelogs de silo déjà créés au lot A, en reprenant la table de correspondance de la
   section « Lot A » ci-dessus et du commentaire de `db.changelog-master.xml`.
4. Dans chaque base existante (H2 locale et PostgreSQL du mini-PC), marquer la baseline comme déjà
   appliquée sans la rejouer : `mvn liquibase:changelogSync` (une seule fois, après avoir copié les
   fichiers définitifs à leur place).
5. Basculer `spring.liquibase.enabled: true` (profils `application.yml` et `application-docker.yml`,
   pas celui de test) et `spring.jpa.hibernate.ddl-auto: validate`.
6. Vérifier un redémarrage sur les deux bases avant de merger (DA-03).

Les changesets suivants (ajouts/retraits de colonnes des lots B des silos R-20 à R-61) viennent
s'ajouter dans le changelog du silo concerné, jamais dans le changelog maître.
