/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.data.teradata;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.geotools.jdbc.AutoGeneratedPrimaryKeyColumn;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.NonIncrementingPrimaryKeyColumn;
import org.geotools.jdbc.PrimaryKey;
import org.geotools.jdbc.PrimaryKeyColumn;
import org.geotools.jdbc.PrimaryKeyFinder;
import org.geotools.util.logging.Logging;

/**
 * The Terradata Key Finder
 *
 * @author Stéphane Brunner @ camptocamp
 */
@SuppressWarnings("PMD.CheckResultSet")
class TeradataPrimaryKeyFinder extends PrimaryKeyFinder {
    private static final Logger LOGGER = Logging.getLogger(TeradataPrimaryKeyFinder.class);

    public PrimaryKey getPrimaryKey(JDBCDataStore store, String schema, String table, Connection cx)
            throws SQLException {

        List<PrimaryKeyColumn> columns = tryForPrimaryKey1(schema, table, cx);
        if (columns.isEmpty()) {
            columns = tryForPrimaryKey(schema, table, cx);
        }
        if (columns.isEmpty()) {
            columns = tryForSequence(schema, table, cx);
        }
        if (columns.isEmpty()) {
            columns = tryAsView(schema, table, cx);
        }

        if (columns.isEmpty()) {
            return null;
        } else {
            return new PrimaryKey(table, columns);
        }
    }

    private List<PrimaryKeyColumn> tryAsView(String schema, String table, Connection cx)
            throws SQLException {
        List<PrimaryKeyColumn> columns = new ArrayList<PrimaryKeyColumn>();
        StringBuilder sql = new StringBuilder("SELECT RequestText FROM DBC.tables WHERE ");
        if (schema != null) {
            sql.append("DatabaseName = '").append(schema).append("' AND ");
        }
        sql.append("TableName = '").append(table).append("' AND TableKind='V'");
        try (Statement st = cx.createStatement();
                ResultSet result = st.executeQuery(sql.toString())) {
            if (result.next()) {
                String createViewSql = result.getString("RequestText");
                int as = createViewSql.toLowerCase().indexOf("as");
                String[] parts =
                        new String[] {
                            createViewSql.substring(0, as), createViewSql.substring(as + 2)
                        };
                /*
                         String viewID = parts[0];
                         String[] viewColumnNames = null;

                int openIndex = viewID.indexOf("(");
                if (openIndex > -1 && viewID.indexOf(")", openIndex) > -1) {
                    String columnString = viewID.substring(openIndex + 1,
                            viewID.indexOf(")", openIndex)).trim();
                    if (columnString.startsWith("\"")) {
                        columnString = columnString.substring(1).trim();
                    }
                    if (columnString.endsWith("\"")) {
                        columnString = columnString.substring(0,columnString.length() - 1).trim();
                    }
                    viewColumnNames = columnString.split("\"?\\s*,\\s*\"?");
                }
                */
                String select = parts[1].substring(parts[1].toLowerCase().indexOf("sel"));

                try (ResultSet viewResults = st.executeQuery(select)) {
                    ResultSetMetaData md = viewResults.getMetaData();

                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        if (md.isAutoIncrement(i)) {
                            String columnLabel;
                            //                        columnLabel = viewColumnNames[i - 1];
                            columnLabel = md.getColumnLabel(i);
                            Class<?> columnType;
                            try {
                                columnType =
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .loadClass(md.getColumnClassName(i));
                            } catch (ClassNotFoundException e) {
                                columnType = Object.class;
                            }
                            columns.add(new AutoGeneratedPrimaryKeyColumn(columnLabel, columnType));
                        }
                    }
                } catch (SQLException e) {
                    String from = "'" + table + "'";
                    if (schema != null) {
                        from = "'" + schema + "'." + from;
                    }
                    LOGGER.warning(
                            "Unable to perform select used to create view "
                                    + from
                                    + ".\nSQL: "
                                    + select);
                }
            }
        }
        return columns;
    }

    private List<PrimaryKeyColumn> tryForSequence(String schema, String table, Connection cx)
            throws SQLException {
        List<PrimaryKeyColumn> columns = new ArrayList<PrimaryKeyColumn>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ColumnName FROM DBC.columns WHERE ");
        if (schema != null) {
            sql.append("DatabaseName = '").append(schema).append("' AND ");
        }
        sql.append("TableName = '")
                .append(table)
                .append("' AND (IdColType='GA' or IdColType='GD')");
        try (Statement st = cx.createStatement();
                ResultSet result = st.executeQuery(sql.toString())) {
            boolean next = result.next();
            TableMetadata tableMetadata = new TableMetadata(st, schema, table);

            while (next) {
                String columnName = result.getString("ColumnName").trim();
                int ordinal = tableMetadata.ordinal(columnName);
                Class<?> columnClass = tableMetadata.columnClass(ordinal);
                if (tableMetadata.isAutoIncrement(ordinal)) {
                    columns.add(new AutoGeneratedPrimaryKeyColumn(columnName, columnClass));
                }
                next = result.next();
            }
        } catch (SQLException e) {
        }

        return columns;
    }

    private List<PrimaryKeyColumn> tryForPrimaryKey(String schema, String table, Connection cx)
            throws SQLException {
        List<PrimaryKeyColumn> columns = new ArrayList<PrimaryKeyColumn>();
        StringBuilder sql =
                new StringBuilder("select ColumnName,ColumnPosition from dbc.indices WHERE ");
        if (schema != null) {
            sql.append("DatabaseName = '").append(schema).append("' AND ");
        }
        sql.append("TableName = '").append(table).append("' AND UniqueFlag = 'Y'");
        try (Statement st = cx.createStatement();
                ResultSet result = st.executeQuery(sql.toString())) {
            boolean next = result.next();
            TableMetadata tableMetadata = new TableMetadata(st, schema, table);

            while (next) {
                int ordinal = Integer.parseInt(result.getString("ColumnPosition").trim());
                String columnName = result.getString("ColumnName").trim();
                Class<?> columnClass = tableMetadata.columnClass(ordinal);
                if (tableMetadata.isAutoIncrement(ordinal)) {
                    columns.add(new AutoGeneratedPrimaryKeyColumn(columnName, columnClass));
                } else {
                    columns.add(new NonIncrementingPrimaryKeyColumn(columnName, columnClass));
                }
                next = result.next();
            }
        } catch (SQLException e) {
        }

        return columns;
    }

    private List<PrimaryKeyColumn> tryForPrimaryKey1(String schema, String table, Connection cx)
            throws SQLException {
        List<PrimaryKeyColumn> columns = new ArrayList<PrimaryKeyColumn>();
        try (ResultSet md = cx.getMetaData().getPrimaryKeys(null, schema, table)) {
            boolean next = md.next();
            if (next) {
                try (Statement stmt = cx.createStatement()) {
                    TableMetadata tableMetadata = new TableMetadata(stmt, schema, table);
                    while (next) {
                        String columnName = md.getString("COLUMN_NAME").trim();
                        int ordinal = tableMetadata.ordinal(columnName);
                        if (ordinal >= 0) {
                            Class<?> columnClass = tableMetadata.columnClass(ordinal);
                            if (tableMetadata.isAutoIncrement(ordinal)) {
                                columns.add(
                                        new AutoGeneratedPrimaryKeyColumn(columnName, columnClass));
                            } else {
                                columns.add(
                                        new NonIncrementingPrimaryKeyColumn(
                                                columnName, columnClass));
                            }
                        }
                        next = md.next();
                    }
                } catch (SQLException e) {
                }
            }
        }
        return columns;
    }

    static class TableMetadata {
        final Statement stmt;
        final ResultSet resultSet;
        final ResultSetMetaData tableMetadata;

        private TableMetadata(Statement stmt, String schema, String table) throws SQLException {
            this.stmt = stmt;
            String from = "\"" + table + "\"";
            if (schema != null) {
                from = "\"" + schema + "\"." + from;
            }
            resultSet = stmt.executeQuery("select * from " + from + " where 1=2");
            tableMetadata = resultSet.getMetaData();
        }

        public int ordinal(String columnName) throws SQLException {
            return resultSet.findColumn(columnName);
        }

        public Class<?> columnClass(int ordinal) throws SQLException {
            try {
                return Thread.currentThread()
                        .getContextClassLoader()
                        .loadClass(tableMetadata.getColumnClassName(ordinal));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isAutoIncrement(int ordinal) throws SQLException {
            return tableMetadata.isAutoIncrement(ordinal);
        }
    }
}
