package org.qdrant.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.List;

public class QdrantDatabaseMetaData implements InvocationHandler {
    private final QdrantConnection connection;

    private QdrantDatabaseMetaData(QdrantConnection connection) {
        this.connection = connection;
    }

    public static DatabaseMetaData create(QdrantConnection connection) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class[]{DatabaseMetaData.class},
                new QdrantDatabaseMetaData(connection)
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        // Реализуем только то, что реально нужно DBeaver для работы
        switch (name) {
            case "getTables":
                return getTables((String)args[0], (String)args[1], (String)args[2], (String[])args[3]);
            case "getColumns":
                return getColumns((String)args[0], (String)args[1], (String)args[2], (String)args[3]);
            case "getDatabaseProductName": return "Qdrant";
            case "getDatabaseProductVersion": return "1.17.0";
            case "getDriverName": return "Qdrant JDBC Driver";
            case "getDriverVersion": return "1.0";
            case "getDriverMajorVersion": return 1;
            case "getDriverMinorVersion": return 0;
            case "getJDBCMajorVersion": return 4;
            case "getJDBCMinorVersion": return 0;
            case "getIdentifierQuoteString": return "\"";
            case "getSearchStringEscape": return "\\";
            case "supportsTransactions": return false;
            case "getDefaultTransactionIsolation": return Connection.TRANSACTION_NONE;
            case "supportsBatchUpdates": return false;
            case "getTableTypes": return new TableTypesResultSet();
            case "getSchemas": case "getCatalogs": case "getProcedures": case "getFunctions":
            case "getClientInfoProperties":
                return new EmptyResultSet();
            case "toString": return "QdrantDatabaseMetaData";
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == args[0];
        }

        // Для всего остального возвращаем дефолтные значения
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == String.class) return "";
        if (ResultSet.class.isAssignableFrom(returnType)) return new EmptyResultSet();
        return null;
    }

    private ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) throws SQLException {
        return new TableListResultSet(connection.listCollections());
    }

    private ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        return new ColumnListResultSet(tableNamePattern);
    }

    // Внутренние классы для выдачи результатов
    private abstract static class ListResultSet extends BaseResultSet {
        protected int cursor = -1;
        @Override public boolean next() throws SQLException { cursor++; return hasCurrent(); }
        protected abstract boolean hasCurrent();
        @Override public ResultSetMetaData getMetaData() throws SQLException { return null; }
    }

    private static class TableListResultSet extends ListResultSet {
        private final List<String> names;
        TableListResultSet(List<String> names) { this.names = names; }
        @Override protected boolean hasCurrent() { return cursor < names.size(); }
        @Override public String getString(int columnIndex) throws SQLException {
            if (columnIndex == 3) return names.get(cursor);
            if (columnIndex == 4) return "TABLE";
            return null;
        }
        @Override public String getString(String columnLabel) throws SQLException {
            if ("TABLE_NAME".equalsIgnoreCase(columnLabel)) return names.get(cursor);
            if ("TABLE_TYPE".equalsIgnoreCase(columnLabel)) return "TABLE";
            return null;
        }
    }

    private static class ColumnListResultSet extends ListResultSet {
        private final String tableName;
        private final String[] columns = {"id", "payload", "vector"};
        ColumnListResultSet(String tableName) { this.tableName = tableName; }
        @Override protected boolean hasCurrent() { return cursor < columns.length; }
        @Override public String getString(int columnIndex) throws SQLException {
            if (columnIndex == 3) return tableName;
            if (columnIndex == 4) return columns[cursor];
            if (columnIndex == 5) return String.valueOf(java.sql.Types.VARCHAR);
            if (columnIndex == 6) return "VARCHAR";
            return null;
        }
        @Override public String getString(String columnLabel) throws SQLException {
            if ("TABLE_NAME".equalsIgnoreCase(columnLabel)) return tableName;
            if ("COLUMN_NAME".equalsIgnoreCase(columnLabel)) return columns[cursor];
            if ("DATA_TYPE".equalsIgnoreCase(columnLabel)) return String.valueOf(java.sql.Types.VARCHAR);
            if ("TYPE_NAME".equalsIgnoreCase(columnLabel)) return "VARCHAR";
            return null;
        }
    }

    private static class TableTypesResultSet extends ListResultSet {
        @Override protected boolean hasCurrent() { return cursor < 1; }
        @Override public String getString(int columnIndex) throws SQLException { return "TABLE"; }
    }

    private static class EmptyResultSet extends ListResultSet {
        @Override protected boolean hasCurrent() { return false; }
    }
}
