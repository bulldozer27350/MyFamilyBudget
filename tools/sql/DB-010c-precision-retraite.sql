-- DB-010c : precision des colonnes de la retraite.
--
-- Contexte : sans @Column explicite, Hibernate cree les colonnes BigDecimal en NUMERIC(38,2).
-- La valeur du point Agirc-Arrco (ex. 1,4386) revenait 1,44 et le ratio points/euro (ex. 0,0051)
-- revenait 0,01 apres rechargement depuis la base (redemarrage). Les entites declarent maintenant
-- NUMERIC(19,8), mais `ddl-auto: update` ne modifie JAMAIS une colonne existante : ce script
-- l'aligne sur une base deja creee. Il est idempotent (le rejouer ne change rien) et sans perte
-- de donnees (on elargit le scale). Les valeurs deja arrondies restent celles qui sont en base :
-- il faut re-saisir la valeur du point et le ratio apres l'avoir execute.
--
-- Compatible PostgreSQL et H2. Exemple PostgreSQL (docker) :
--   docker exec -i myfamilybudget-db psql -U myfamilybudget -d myfamilybudget \
--     -v ON_ERROR_STOP=1 -1 < tools/sql/DB-010c-precision-retraite.sql

ALTER TABLE retirement ALTER COLUMN agirc_point_value SET DATA TYPE NUMERIC(19,8);

ALTER TABLE retirement_person ALTER COLUMN ratio_points_par_euro SET DATA TYPE NUMERIC(19,8);
