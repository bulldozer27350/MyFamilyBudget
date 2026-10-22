package com.moe.myfamilybudget.infra.jpa;

import java.sql.Types;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * {@link JdbcType} pour {@code @Lob String} qui lit/ecrit la colonne comme une chaine ordinaire
 * ({@code ResultSet#getString}/{@code PreparedStatement#setString}, via {@link VarcharJdbcType}), tout
 * en conservant {@link SqlTypes#CLOB} comme code DDL.
 *
 * <p>Complement indispensable de {@link TextClobPostgreSQLDialect} : surcharger
 * {@code Dialect#columnType(int)} seul (CLOB -> {@code text}) ne change que la generation du DDL. A
 * l'execution, Hibernate reste enregistre sur {@code ClobJdbcType} pour le code {@code CLOB}, qui
 * utilise {@code ResultSet#getClob()}/{@code PreparedStatement#setClob()} quel que soit le type
 * physique reel de la colonne ; chez PostgreSQL, {@code getClob()} passe toujours par le mecanisme
 * « Large Object » (gestionnaire d'objets volumineux, colonne {@code oid}), meme sur une colonne
 * {@code text} — d'ou l'echec {@code PSQLException: Large Objects may not be used in auto-commit mode}
 * observe en lecture (CI du 22 oct. 2026, lectures hors transaction explicite).
 *
 * <p>Cette classe enregistre donc, pour le code {@code CLOB}, un acces en lecture/ecriture identique a
 * celui de {@link VarcharJdbcType} ({@link #getBinder}/{@link #getExtractor} delegues), tout en gardant
 * {@link #getDdlTypeCode()} sur {@code CLOB} : {@code JdbcType#getDdlTypeCode()} est decouple de
 * {@link #getJdbcTypeCode()} dans l'API Hibernate 6 precisement pour ce genre de cas — la generation du
 * DDL continue donc de passer par {@code Dialect#columnType(CLOB)} (donc {@code text}, sans contrainte
 * de longueur), independamment du mode d'acces choisi ici (chaine, pas flux).
 *
 * <p>Enregistree uniquement par {@link TextClobPostgreSQLDialect#contribute}, donc seulement en profil
 * {@code docker} : H2 (profil par defaut, tests) garde le {@code ClobJdbcType} standard, qui n'a pas ce
 * probleme sur cette base.
 */
public class TextJdbcType implements JdbcType {

    public static final TextJdbcType INSTANCE = new TextJdbcType();

    @Override
    public int getJdbcTypeCode() {
        return Types.VARCHAR;
    }

    @Override
    public int getDdlTypeCode() {
        return SqlTypes.CLOB;
    }

    @Override
    public String getFriendlyName() {
        return "TEXT";
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return VarcharJdbcType.INSTANCE.getBinder(javaType);
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return VarcharJdbcType.INSTANCE.getExtractor(javaType);
    }
}
