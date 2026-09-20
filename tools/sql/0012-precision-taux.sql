-- Patch 0012 : precision des colonnes de taux (fractions).
--
-- Contexte : sans @Column explicite, Hibernate cree les colonnes BigDecimal en NUMERIC(38,2).
-- Un taux stocke en fraction (0,0251 pour 2,51 %) etait donc arrondi a 0,03 a l'ecriture en
-- base, et « 2,5 % » revenait « 3 % » au rechargement. Les entites declarent maintenant
-- NUMERIC(19,8), mais `ddl-auto: update` ne modifie JAMAIS une colonne existante : ce script
-- l'aligne sur une base deja creee. Il est idempotent (le rejouer ne change rien) et sans perte
-- de donnees (on elargit le scale). Les valeurs deja arrondies restent celles qui sont en base :
-- il faut re-saisir les taux concernes apres l'avoir execute.
--
-- Compatible PostgreSQL et H2. Exemple PostgreSQL (docker) :
--   docker exec -i myfamilybudget-db psql -U myfamilybudget -d myfamilybudget \
--     -v ON_ERROR_STOP=1 -1 < tools/sql/0012-precision-taux.sql

ALTER TABLE loan ALTER COLUMN rate SET DATA TYPE NUMERIC(19,8);

ALTER TABLE placement ALTER COLUMN rate_pess SET DATA TYPE NUMERIC(19,8);
ALTER TABLE placement ALTER COLUMN rate_corr SET DATA TYPE NUMERIC(19,8);
ALTER TABLE placement ALTER COLUMN rate_opti SET DATA TYPE NUMERIC(19,8);

ALTER TABLE real_estate ALTER COLUMN annual_growth_rate SET DATA TYPE NUMERIC(19,8);

ALTER TABLE income ALTER COLUMN growth_rate SET DATA TYPE NUMERIC(19,8);
ALTER TABLE charge ALTER COLUMN growth_rate SET DATA TYPE NUMERIC(19,8);
ALTER TABLE variable_income ALTER COLUMN rate SET DATA TYPE NUMERIC(19,8);

ALTER TABLE settings ALTER COLUMN inflation_rate SET DATA TYPE NUMERIC(19,8);
ALTER TABLE settings ALTER COLUMN tax_abattement SET DATA TYPE NUMERIC(19,8);
ALTER TABLE settings ALTER COLUMN pass_growth_rate SET DATA TYPE NUMERIC(19,8);

ALTER TABLE retirement ALTER COLUMN pass_growth_rate SET DATA TYPE NUMERIC(19,8);
ALTER TABLE retirement ALTER COLUMN agirc_point_growth_rate SET DATA TYPE NUMERIC(19,8);

ALTER TABLE tax_bracket ALTER COLUMN rate SET DATA TYPE NUMERIC(19,8);
ALTER TABLE tax_rate_override ALTER COLUMN rate SET DATA TYPE NUMERIC(19,8);
