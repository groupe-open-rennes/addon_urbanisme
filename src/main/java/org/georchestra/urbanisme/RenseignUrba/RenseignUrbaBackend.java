/*
 * Copyright (C) 2009-2016 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.urbanisme.RenseignUrba;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.dbcp2.BasicDataSource;

/**
 * This class represent storage of Renseignement d'urbanisme.
 */
public class RenseignUrbaBackend {

    private String table;
    private String tableTheme;
    private String tableThemeGroupes;
    private String ordreTheme;
    private String jdbcUrl;
    private BasicDataSource basicDataSource;

    /**
     * Create a new instance of RenseignUrbaBackend and crate a BasicDataSource configured with jdbc URL
     *
     * @param table      name of table containing renseignement d'urbanisme (libelles)
     * @param tableTheme name of table containing theme description
     * @param ordreTheme theme codes order
     * @param jdbcUrl    jdbc URL used to connect to database. Example : jdbc:postgresql://localhost:5432/georchestra?user=www-data&password=www-data
     */
    public RenseignUrbaBackend(final String driverClassName,
            final String table, final String tableTheme, final String tableThemeGroupes, final String ordreTheme,
            final String jdbcUrl) {
        this.table = table;
        this.tableTheme = tableTheme;
        this.tableThemeGroupes = tableThemeGroupes;
        this.ordreTheme = ordreTheme;
        this.jdbcUrl = jdbcUrl;

        this.initDataSource(driverClassName);
    }

    private void initDataSource(String driverClassName) {
        this.basicDataSource = new BasicDataSource();
        this.basicDataSource.setDriverClassName(driverClassName);
        this.basicDataSource.setTestOnBorrow(true);
        this.basicDataSource.setPoolPreparedStatements(true);
        this.basicDataSource.setMaxOpenPreparedStatements(-1);
        this.basicDataSource.setDefaultReadOnly(true);
        this.basicDataSource.setDefaultAutoCommit(true);
        this.basicDataSource.setUrl(this.jdbcUrl);
    }

    /**
     * Get renseignement d'urbanisme for the given parcelle.
     *
     * @param parcelle Parcelle ID
     * @return RenseignUrba instance containing the libelles
     * @throws SQLException
     */
    public RenseignUrba getParcelle(String parcelle) throws SQLException {

        Connection connection = null;
        PreparedStatement queryLibellesByParcelle = null;

        List<String> libellesVal = new ArrayList<>();

        try {
            connection = this.basicDataSource.getConnection();
            String query = this.getNRUQuery();

            queryLibellesByParcelle = connection.prepareStatement(query);
            queryLibellesByParcelle.setString(1, parcelle);
            ResultSet rs = queryLibellesByParcelle.executeQuery();

            while (rs.next()) {
                String libelle = rs.getString("libelle");
                libellesVal.add(libelle);
            }
            return new RenseignUrba(parcelle, libellesVal);
        } finally {
            if ((queryLibellesByParcelle != null) && (!queryLibellesByParcelle.isClosed())) {
                queryLibellesByParcelle.close();
            }
            if ((connection != null) && (!connection.isClosed())) {
                connection.close();
            }
        }
    }

    /**
     * Nouveau service
     * Get renseignement d'urbanisme for the given parcelle.
     *
     * @param parcelle Parcelle ID
     * @return RenseignUrba instance containing the libelles, the ordres, the groupeRu
     * @throws SQLException
     */
    public RenseignUrba getParcelleNouvelleNRU(String parcelle) throws SQLException {

        List<String> libellesVal = new ArrayList<>();
        List<String> groupesRu = new ArrayList<>();
        List<Long> ordres = new ArrayList<>();


        String query = this.getNewNRUQuery();
        try (Connection connection =  this.basicDataSource.getConnection();
             PreparedStatement queryInfosByParcelle = connection.prepareStatement(query);
        ) {
            queryInfosByParcelle.setString(1, parcelle);
            ResultSet rs = queryInfosByParcelle.executeQuery();

            while (rs.next()) {
                String libelle = rs.getString("libelle");
                libellesVal.add(libelle);
                String groupeRu = rs.getString("groupe_ru");
                Long ordre = rs.getLong("ordre");
                groupesRu.add(groupeRu);
                ordres.add(ordre);
            }
            return new RenseignUrba(parcelle, libellesVal, groupesRu, ordres);
        }
    }

    /**
     * Requete SQL des nouveaux renseignements d'Urbanisme
     * @return Requete SQL à exécuter pour recuperer les Nouveaux Renseignement d'Urbanisme
     */
    private String getNRUQuery() {
        return "SELECT "
                + "     libelle "
                + "FROM "
                + "(  SELECT "
                + "       ru.libelle AS libelle,"
                + "       theme.ventilation_ddc AS ventilation_ddc,"
                + "       ru.numero AS numero "
                + "   FROM "
                + this.table + " AS ru "
                + "LEFT OUTER JOIN "
                + this.tableTheme + " AS theme "
                + "ON "
                + "  ru.nom_theme = theme.nom "
                + "WHERE "
                + "  id_parc = ?) AS libelles "
                + "LEFT JOIN (VALUES " + this.ordreTheme + ") AS ordre(code, priorite) "
                + "ON libelles.ventilation_ddc = ordre.code "
                + "ORDER BY ordre.priorite ASC, numero ASC ;";
    }

    /**
     * Requete SQL des nouveaux renseignements d'Urbanisme
     * @return Requete SQL à exécuter pour recuperer les Nouveaux Renseignement d'Urbanisme
     */
    private String getNewNRUQuery() {
        return "SELECT ru.libelle AS libelle, " +
                "theme.groupe_ru::text, " +
                "theme.ordre " +
        "FROM " + this.table + " AS ru " +
        "LEFT OUTER JOIN "+ this.tableThemeGroupes +" AS theme ON ru.nom_theme = theme.nom " +
        "WHERE id_parc = ? " +
        "ORDER BY groupe_ru, ordre, libelle;";
    }

}
