package org.qdrant.jdbc;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class QdrantDriver implements Driver {
    private static final String PREFIX = "jdbc:qdrant:";

    static {
        try {
            DriverManager.registerDriver(new QdrantDriver());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        return new QdrantConnection(url, info);
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        DriverPropertyInfo apiKey = new DriverPropertyInfo("api_key", info == null ? null : info.getProperty("api_key"));
        apiKey.description = "Qdrant API key sent via gRPC metadata.";
        apiKey.required = false;

        DriverPropertyInfo https = new DriverPropertyInfo("https", info == null ? null : info.getProperty("https"));
        https.description = "Enable TLS for the gRPC connection.";
        https.required = false;
        https.choices = new String[] {"true", "false"};

        DriverPropertyInfo verify = new DriverPropertyInfo("verify", info == null ? null : info.getProperty("verify"));
        verify.description = "Verify TLS certificates. Set false for self-signed/internal endpoints.";
        verify.required = false;
        verify.choices = new String[] {"true", "false"};

        DriverPropertyInfo transport = new DriverPropertyInfo("transport", info == null ? null : info.getProperty("transport"));
        transport.description = "Transport mode: rest/http for HTTP API, grpc for gRPC.";
        transport.required = false;
        transport.choices = new String[] {"rest", "http", "grpc"};

        return new DriverPropertyInfo[] {apiKey, https, verify, transport};
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
