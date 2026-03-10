# Qdrant JDBC Driver for DBeaver

Minimal JDBC driver that lets DBeaver browse Qdrant collections and run basic reads.

The project started as a simple gRPC JDBC bridge and was extended to support the more practical DBeaver case:

- Qdrant over REST `http/https`
- API key authentication
- self-signed / internal TLS with `verify=false`
- collection discovery in DBeaver
- basic `SELECT * FROM <collection>`

It is intentionally small and not JDBC-complete.

## Status

What works now:

- driver loading in DBeaver
- listing Qdrant collections as tables
- showing columns: `id`, `payload`, `vector`
- reading points with `SELECT * FROM <collection>`
- REST mode for `http/https` endpoints
- gRPC mode for direct Qdrant gRPC endpoints
- API key support
- TLS on/off
- disabled certificate verification for internal/self-signed setups

What does not work yet:

- writes: `INSERT`, `UPDATE`, `DELETE`
- advanced SQL parsing
- filtering / pushdown
- joins
- prepared statements
- full JDBC metadata coverage

## Build

Requirements:

- Java 21 installed locally is fine for building
- output bytecode is compiled with `--release 11`, so the jar is compatible with DBeaver running on Java 17

Build the fat jar:

```bash
GRADLE_USER_HOME=$PWD/.gradle-local ./gradlew clean shadowJar
```

Result:

```text
build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar
```

## Distribution

The repository is set up to publish the jar in two places:

- GitHub Actions `Artifacts` on every push to `main`
- GitHub `Releases` on tag push like `v0.1.0`

Recommended usage:

- `Artifacts` are for testing CI builds
- `Releases` are for actual user downloads

To publish a release jar:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## DBeaver Setup

Create a new custom driver and configure:

- Driver class: `org.qdrant.jdbc.QdrantDriver`
- Driver type: `Generic`

Add library:

- `build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar`

### Option 1: REST/HTTPS endpoint

Use this when your working Python config looks like:

```python
QdrantClient(
    url="https://localhost:15672",
    api_key="...",
    https=True,
    verify=False,
)
```

JDBC URL:

```text
jdbc:qdrant://localhost:15672
```

Driver properties:

- `transport=rest`
- `https=true`
- `verify=false`
- `api_key=YOUR_API_KEY`

You can also use:

```text
jdbc:qdrant:https://localhost:15672?verify=false
```

but keeping transport explicit in driver properties is clearer:

- `transport=rest`

### Option 2: Direct gRPC endpoint

Use this for plain Qdrant gRPC, usually on `6334`.

JDBC URL:

```text
jdbc:qdrant://127.0.0.1:6334
```

Optional properties:

- `transport=grpc`
- `https=true|false`
- `verify=true|false`
- `api_key=...`

## SQL Support

Currently the driver supports only one query form:

```sql
SELECT * FROM my_collection
```

Returned columns:

- `id`
- `payload`
- `vector`

In REST mode the driver uses:

- `GET /collections`
- `POST /collections/{name}/points/scroll`

In gRPC mode it uses:

- `listCollections`
- `scroll`

## JDBC Properties

Supported driver properties:

- `api_key`: Qdrant API key
- `https`: enable TLS
- `verify`: verify TLS certificates and hostname
- `transport`: `rest`, `http`, or `grpc`

Selection rules:

- `jdbc:qdrant:http://...` or `jdbc:qdrant:https://...` => REST mode
- `transport=rest` => force REST mode
- `transport=grpc` => force gRPC mode
- plain `jdbc:qdrant://host:port` defaults to gRPC unless REST-oriented properties make REST mode a better match

## Notes

- `verify=false` is intended for internal/self-signed environments
- if your TLS certificate does not include `localhost` in SAN, keep `verify=false` or connect via a hostname that is actually present in the certificate
- if DBeaver appears to use an old driver version, remove old Qdrant jars from the driver settings and re-add the current jar

## Project Layout

Main implementation lives in:

- `src/main/java/org/qdrant/jdbc/QdrantDriver.java`
- `src/main/java/org/qdrant/jdbc/QdrantConnection.java`
- `src/main/java/org/qdrant/jdbc/QdrantDatabaseMetaData.java`
- `src/main/java/org/qdrant/jdbc/QdrantStatement.java`
- `src/main/java/org/qdrant/jdbc/QdrantResultSet.java`

Build config:

- `build.gradle.kts`

## Next Improvements

Reasonable next steps if you continue the project:

- support `SELECT id, payload FROM <collection>`
- add `LIMIT`
- add REST pagination support in the result set
- map payload fields into virtual columns
- improve DBeaver metadata so table browsing feels more native
- add tests against a local Qdrant container
