package org.qdrant.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public class QdrantResultSetMetaData implements ResultSetMetaData {
    @Override
    public int getColumnCount() throws SQLException {
        return 3;
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        switch (column) {
            case 1: return "id";
            case 2: return "payload";
            case 3: return "vector";
            default: throw new SQLException("Invalid column index");
        }
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumnLabel(column);
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        return Types.VARCHAR;
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        return "VARCHAR";
    }

    @Override public boolean isAutoIncrement(int column) throws SQLException { return false; }
    @Override public boolean isCaseSensitive(int column) throws SQLException { return true; }
    @Override public boolean isSearchable(int column) throws SQLException { return true; }
    @Override public boolean isCurrency(int column) throws SQLException { return false; }
    @Override public int isNullable(int column) throws SQLException { return columnNoNulls; }
    @Override public boolean isSigned(int column) throws SQLException { return false; }
    @Override public int getColumnDisplaySize(int column) throws SQLException { return 255; }
    @Override public String getSchemaName(int column) throws SQLException { return ""; }
    @Override public int getPrecision(int column) throws SQLException { return 0; }
    @Override public int getScale(int column) throws SQLException { return 0; }
    @Override public String getTableName(int column) throws SQLException { return ""; }
    @Override public String getCatalogName(int column) throws SQLException { return ""; }
    @Override public boolean isReadOnly(int column) throws SQLException { return true; }
    @Override public boolean isWritable(int column) throws SQLException { return false; }
    @Override public boolean isDefinitelyWritable(int column) throws SQLException { return false; }
    @Override public String getColumnClassName(int column) throws SQLException { return String.class.getName(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
}
