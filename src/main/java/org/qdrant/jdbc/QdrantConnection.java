package org.qdrant.jdbc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points;

import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class QdrantConnection implements Connection {
    private static final Gson GSON = new Gson();

    private final String url;
    private final EndpointConfig endpoint;
    private final QdrantClient grpcClient;
    private final SSLContext httpSslContext;
    private boolean closed = false;

    public QdrantConnection(String url, Properties info) throws SQLException {
        this.url = url;
        try {
            this.endpoint = parseEndpoint(url, info);
            if (endpoint.restMode()) {
                this.httpSslContext = buildHttpSslContext(endpoint);
                this.grpcClient = null;
            } else {
                this.grpcClient = buildGrpcClient(endpoint);
                this.httpSslContext = null;
            }
        } catch (Exception e) {
            throw new SQLException("Failed to connect to Qdrant at " + url, e);
        }
    }

    public List<String> listCollections() throws SQLException {
        if (endpoint.restMode()) {
            try {
                JsonObject root = getJson("/collections");
                JsonArray collections = root.getAsJsonObject("result").getAsJsonArray("collections");
                List<String> names = new ArrayList<String>();
                for (JsonElement element : collections) {
                    names.add(element.getAsJsonObject().get("name").getAsString());
                }
                return names;
            } catch (Exception e) {
                throw new SQLException("Failed to list collections from Qdrant", e);
            }
        }

        try {
            return grpcClient.listCollectionsAsync().get();
        } catch (Exception e) {
            throw new SQLException("Failed to list collections from Qdrant", e);
        }
    }

    public List<QdrantRow> scrollCollection(String collectionName) throws SQLException {
        if (endpoint.restMode()) {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("limit", 100);
                body.addProperty("with_payload", true);
                body.addProperty("with_vector", true);
                JsonObject root = postJson("/collections/" + urlEncode(collectionName) + "/points/scroll", body);
                JsonArray points = root.getAsJsonObject("result").getAsJsonArray("points");
                List<QdrantRow> rows = new ArrayList<QdrantRow>();
                for (JsonElement element : points) {
                    JsonObject point = element.getAsJsonObject();
                    rows.add(new QdrantRow(
                            jsonToString(point.get("id")),
                            jsonToString(point.get("payload")),
                            firstNonNull(jsonToString(point.get("vector")), jsonToString(point.get("vectors")))
                    ));
                }
                return rows;
            } catch (Exception e) {
                throw new SQLException("Failed to execute query on Qdrant", e);
            }
        }

        try {
            Points.ScrollPoints scrollPoints = Points.ScrollPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit(100)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                    .build();
            List<Points.RetrievedPoint> points = grpcClient.scrollAsync(scrollPoints).get().getResultList();
            List<QdrantRow> rows = new ArrayList<QdrantRow>();
            for (Points.RetrievedPoint point : points) {
                rows.add(new QdrantRow(
                        point.getId().toString(),
                        GSON.toJson(point.getPayloadMap()),
                        point.getVectors().toString()
                ));
            }
            return rows;
        } catch (Exception e) {
            throw new SQLException("Failed to execute query on Qdrant", e);
        }
    }

    public QdrantClient getQdrantClient() {
        return grpcClient;
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new QdrantStatement(this);
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            if (grpcClient != null) {
                grpcClient.close();
            }
            closed = true;
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        return QdrantDatabaseMetaData.create(this);
    }

    private static QdrantClient buildGrpcClient(EndpointConfig endpoint) throws Exception {
        NettyChannelBuilder channelBuilder = NettyChannelBuilder
                .forAddress(endpoint.host(), endpoint.port())
                .nameResolverFactory(new DnsNameResolverProvider());

        if (endpoint.secure()) {
            if (endpoint.verifyTls()) {
                channelBuilder.useTransportSecurity();
            } else {
                SslContext sslContext = GrpcSslContexts.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
                channelBuilder.sslContext(sslContext);
            }
        } else {
            channelBuilder.usePlaintext();
        }

        ManagedChannel channel = channelBuilder.build();
        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(channel);
        if (endpoint.apiKey() != null && !endpoint.apiKey().isBlank()) {
            grpcBuilder.withApiKey(endpoint.apiKey());
        }
        return new QdrantClient(grpcBuilder.build());
    }

    private static SSLContext buildHttpSslContext(EndpointConfig endpoint) throws Exception {
        if (endpoint.secure() && !endpoint.verifyTls()) {
            TrustManager[] trustAllManagers = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllManagers, new SecureRandom());
            return sslContext;
        }
        return null;
    }

    private static EndpointConfig parseEndpoint(String url, Properties info) throws URISyntaxException {
        final String prefix = "jdbc:qdrant:";
        if (url == null || !url.startsWith(prefix)) {
            throw new URISyntaxException(String.valueOf(url), "URL must start with " + prefix);
        }

        String remainder = url.substring(prefix.length()).trim();
        if (remainder.isEmpty()) {
            return fromProperties("localhost", 6334, false, true, new HashMap<String, String>(), info, false, null);
        }

        if (remainder.startsWith("http://") || remainder.startsWith("https://")) {
            URI uri = URI.create(remainder);
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            return fromProperties(
                    uri.getHost() == null ? "localhost" : uri.getHost(),
                    uri.getPort() == -1 ? (secure ? 443 : 80) : uri.getPort(),
                    secure,
                    true,
                    parseQueryParams(uri.getRawQuery()),
                    info,
                    true,
                    uri.getScheme() + "://" + uri.getAuthority()
            );
        }

        if (remainder.startsWith("//")) {
            URI uri = new URI("qdrant:" + remainder);
            return fromProperties(
                    uri.getHost() == null ? "localhost" : uri.getHost(),
                    uri.getPort() == -1 ? 6334 : uri.getPort(),
                    false,
                    true,
                    parseQueryParams(uri.getRawQuery()),
                    info,
                    false,
                    null
            );
        }

        String hostPortPart = remainder;
        Map<String, String> query = new HashMap<String, String>();
        int queryIndex = remainder.indexOf('?');
        if (queryIndex >= 0) {
            hostPortPart = remainder.substring(0, queryIndex);
            query = parseQueryParams(remainder.substring(queryIndex + 1));
        }

        String host = hostPortPart;
        int port = 6334;
        int colonIndex = hostPortPart.lastIndexOf(':');
        if (colonIndex >= 0) {
            host = hostPortPart.substring(0, colonIndex);
            port = Integer.parseInt(hostPortPart.substring(colonIndex + 1));
        }
        if (host.isBlank()) {
            host = "localhost";
        }

        return fromProperties(host, port, false, true, query, info, false, null);
    }

    private static EndpointConfig fromProperties(
            String host,
            int port,
            boolean secure,
            boolean verifyTls,
            Map<String, String> query,
            Properties info,
            boolean restMode,
            String baseUrl
    ) {
        String apiKey = firstNonBlank(
                property(info, "api_key"),
                property(info, "apiKey"),
                query.get("api_key"),
                query.get("apiKey")
        );

        boolean resolvedSecure = parseBoolean(
                firstNonBlank(
                        property(info, "https"),
                        property(info, "ssl"),
                        query.get("https"),
                        query.get("ssl")
                ),
                secure
        );

        boolean resolvedVerify = parseBoolean(
                firstNonBlank(
                        property(info, "verify"),
                        property(info, "verify_tls"),
                        property(info, "verifyTls"),
                        query.get("verify"),
                        query.get("verify_tls"),
                        query.get("verifyTls")
                ),
                verifyTls
        );

        String transport = firstNonBlank(
                property(info, "transport"),
                query.get("transport")
        );

        boolean inferredRestMode = restMode
                || "http".equalsIgnoreCase(transport)
                || "rest".equalsIgnoreCase(transport)
                || hasAnyNonBlank(
                        property(info, "api_key"),
                        property(info, "apiKey"),
                        query.get("api_key"),
                        query.get("apiKey"),
                        property(info, "verify"),
                        property(info, "verify_tls"),
                        property(info, "verifyTls"),
                        query.get("verify"),
                        query.get("verify_tls"),
                        query.get("verifyTls")
                )
                || resolvedSecure;

        String resolvedBaseUrl = baseUrl;
        if (inferredRestMode && resolvedBaseUrl == null) {
            resolvedBaseUrl = (resolvedSecure ? "https" : "http") + "://" + host + ":" + port;
        }

        return new EndpointConfig(host, port, resolvedSecure, resolvedVerify, apiKey, inferredRestMode, resolvedBaseUrl);
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> result = new HashMap<String, String>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex < 0) {
                result.put(pair, "");
            } else {
                result.put(pair.substring(0, equalsIndex), pair.substring(equalsIndex + 1));
            }
        }
        return result;
    }

    private JsonObject getJson(String path) throws Exception {
        HttpURLConnection connection = openRestConnection(path, "GET");
        return parseHttpResponse(connection);
    }

    private JsonObject postJson(String path, JsonObject body) throws Exception {
        HttpURLConnection connection = openRestConnection(path, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        byte[] payload = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload);
        }
        return parseHttpResponse(connection);
    }

    private HttpURLConnection openRestConnection(String path, String method) throws Exception {
        URL requestUrl = URI.create(endpoint.baseUrl() + path).toURL();
        HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);

        if (connection instanceof HttpsURLConnection) {
            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
            if (httpSslContext != null) {
                httpsConnection.setSSLSocketFactory(httpSslContext.getSocketFactory());
                httpsConnection.setHostnameVerifier(disabledHostnameVerifier());
            }
        }

        applyHttpHeaders(connection);
        return connection;
    }

    private void applyHttpHeaders(HttpURLConnection connection) {
        if (endpoint.apiKey() != null && !endpoint.apiKey().isBlank()) {
            connection.setRequestProperty("api-key", endpoint.apiKey());
        }
    }

    private JsonObject parseHttpResponse(HttpURLConnection connection) throws Exception {
        int statusCode = connection.getResponseCode();
        String body;
        try (InputStream stream = statusCode >= 200 && statusCode < 300 ? connection.getInputStream() : connection.getErrorStream()) {
            body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new SQLException("Qdrant REST API returned HTTP " + statusCode + ": " + body);
        }

        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json == null) {
            throw new SQLException("Qdrant REST API returned an empty response body");
        }
        return json;
    }

    private static HostnameVerifier disabledHostnameVerifier() {
        return (hostname, session) -> true;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private static String property(Properties properties, String key) {
        return properties == null ? null : properties.getProperty(key);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasAnyNonBlank(String... values) {
        return firstNonBlank(values) != null;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private static String jsonToString(JsonElement element) {
        return element == null || element.isJsonNull() ? null : GSON.toJson(element);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class EndpointConfig {
        private final String host;
        private final int port;
        private final boolean secure;
        private final boolean verifyTls;
        private final String apiKey;
        private final boolean restMode;
        private final String baseUrl;

        private EndpointConfig(String host, int port, boolean secure, boolean verifyTls, String apiKey, boolean restMode, String baseUrl) {
            this.host = host;
            this.port = port;
            this.secure = secure;
            this.verifyTls = verifyTls;
            this.apiKey = apiKey;
            this.restMode = restMode;
            this.baseUrl = baseUrl;
        }

        private String host() {
            return host;
        }

        private int port() {
            return port;
        }

        private boolean secure() {
            return secure;
        }

        private boolean verifyTls() {
            return verifyTls;
        }

        private String apiKey() {
            return apiKey;
        }

        private boolean restMode() {
            return restMode;
        }

        private String baseUrl() {
            return baseUrl;
        }
    }

    @Override public void setAutoCommit(boolean autoCommit) throws SQLException {}
    @Override public boolean getAutoCommit() throws SQLException { return true; }
    @Override public void commit() throws SQLException {}
    @Override public void rollback() throws SQLException {}
    @Override public void setReadOnly(boolean readOnly) throws SQLException {}
    @Override public boolean isReadOnly() throws SQLException { return false; }
    @Override public void setCatalog(String catalog) throws SQLException {}
    @Override public String getCatalog() throws SQLException { return null; }
    @Override public void setTransactionIsolation(int level) throws SQLException {}
    @Override public int getTransactionIsolation() throws SQLException { return TRANSACTION_NONE; }
    @Override public SQLWarning getWarnings() throws SQLException { return null; }
    @Override public void clearWarnings() throws SQLException {}
    @Override public PreparedStatement prepareStatement(String sql) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public CallableStatement prepareCall(String sql) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String nativeSQL(String sql) throws SQLException { return sql; }
    @Override public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return null; }
    @Override public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {}
    @Override public void setHoldability(int holdability) throws SQLException {}
    @Override public int getHoldability() throws SQLException { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public Savepoint setSavepoint() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Savepoint setSavepoint(String name) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void rollback(Savepoint savepoint) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void releaseSavepoint(Savepoint savepoint) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Clob createClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob createBlob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob createNClob() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML createSQLXML() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isValid(int timeout) throws SQLException { return !closed; }
    @Override public void setClientInfo(String name, String value) throws SQLClientInfoException {}
    @Override public void setClientInfo(Properties properties) throws SQLClientInfoException {}
    @Override public String getClientInfo(String name) throws SQLException { return null; }
    @Override public Properties getClientInfo() throws SQLException { return new Properties(); }
    @Override public Array createArrayOf(String typeName, Object[] elements) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Struct createStruct(String typeName, Object[] attributes) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void setSchema(String schema) throws SQLException {}
    @Override public String getSchema() throws SQLException { return null; }
    @Override public void abort(Executor executor) throws SQLException { close(); }
    @Override public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {}
    @Override public int getNetworkTimeout() throws SQLException { return 0; }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
}
