# Qdrant Driver for DBeaver

JDBC driver that lets DBeaver open Qdrant collections like tables.

What it does right now:

- shows Qdrant collections in DBeaver
- opens collection data
- runs basic reads with `SELECT * FROM collection_name`
- works with REST/HTTPS endpoints
- supports API key auth
- supports `verify=false` for self-signed TLS

This project is intentionally small.
It is for browsing and simple reads first, not for full SQL compatibility.

## The Easy Path

If you just want to make it work in DBeaver:

1. Download the jar from GitHub `Releases`.
2. Create a custom `Generic` driver in DBeaver.
3. Set driver class to `org.qdrant.jdbc.QdrantDriver`.
4. Add the jar on the `Libraries` tab.
5. Create a connection with your Qdrant URL and API key.

If your Qdrant already works from Python like this:

```python
from qdrant_client import QdrantClient

client = QdrantClient(
    url="https://localhost:15672",
    api_key="YOUR_API_KEY",
    https=True,
    verify=False,
)
```

then DBeaver should be configured like this:

- JDBC URL: `jdbc:qdrant://localhost:15672`
- `transport=rest`
- `https=true`
- `verify=false`
- `api_key=YOUR_API_KEY`

## Download

Preferred option:

- open GitHub `Releases`
- download the latest jar

Alternative:

- open GitHub `Actions`
- open the latest successful run on `main`
- download the build `Artifact`

Use `Releases` if you are a normal user.
Use `Artifacts` if you want the freshest CI build.

## DBeaver Setup

### 1. Create Driver

In DBeaver:

1. Open `Database` -> `Driver Manager`
2. Click `New`
3. Choose `Generic`

Fill these fields:

- Driver Name: `Qdrant`
- Class Name: `org.qdrant.jdbc.QdrantDriver`

### 2. Add Jar

Open the `Libraries` tab and add the downloaded jar.

### 3. Create Connection

Create a new connection using that driver.

For a REST/HTTPS Qdrant endpoint, use:

- URL: `jdbc:qdrant://localhost:15672`

Driver properties:

- `transport=rest`
- `https=true`
- `verify=false`
- `api_key=YOUR_API_KEY`

If your certificate is valid and trusted, use:

- `verify=true`

If your certificate is self-signed or internal, use:

- `verify=false`

### 4. Open Collections

After connection:

- collections should appear as tables
- opening a table should load points from that collection

## SQL Support

Currently supported:

```sql
SELECT * FROM my_collection
```

Returned columns:

- `id`
- `payload`
- `vector`

Not supported yet:

- `INSERT`
- `UPDATE`
- `DELETE`
- joins
- arbitrary SQL
- prepared statements

## Working Connection Examples

### HTTPS + API key + self-signed TLS

JDBC URL:

```text
jdbc:qdrant://localhost:15672
```

Properties:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### HTTPS + API key + trusted certificate

JDBC URL:

```text
jdbc:qdrant://qdrant.example.com:443
```

Properties:

```text
transport=rest
https=true
verify=true
api_key=YOUR_API_KEY
```

### Direct gRPC endpoint

Use this only if you really expose Qdrant gRPC directly.

JDBC URL:

```text
jdbc:qdrant://127.0.0.1:6334
```

Properties:

```text
transport=grpc
https=false
```

## Build From Source

Requirements:

- Java installed locally
- Gradle wrapper is already included

Build:

```bash
GRADLE_USER_HOME=$PWD/.gradle-local ./gradlew clean shadowJar
```

Output jar:

```text
build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar
```

The project builds with Java 21 locally but emits Java 11 bytecode, so the jar works with DBeaver on Java 17.

## Release Flow

This repo is set up like this:

- push to `main` => build jar in GitHub Actions `Artifacts`
- push tag `v*` => create GitHub `Release` and attach the jar

Create a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Troubleshooting

### `No subject alternative DNS name matching localhost found`

Your certificate does not contain `localhost`.

Use one of these:

- connect with the real hostname from the certificate
- set `verify=false`

### `HTTP 404` / `UNIMPLEMENTED`

You are probably hitting a REST endpoint with gRPC mode.

Set:

```text
transport=rest
```

### DBeaver still behaves like it uses an old driver

Remove all old Qdrant jars from the driver settings, add only the latest jar, and restart DBeaver.

## What This Project Is

Important expectation setting:

- this is a practical DBeaver bridge for Qdrant
- it is not a full SQL engine
- it is not a complete JDBC implementation
- it is meant to make browsing and simple reads easy

## Project Structure

Main code:

- `src/main/java/org/qdrant/jdbc/QdrantDriver.java`
- `src/main/java/org/qdrant/jdbc/QdrantConnection.java`
- `src/main/java/org/qdrant/jdbc/QdrantDatabaseMetaData.java`
- `src/main/java/org/qdrant/jdbc/QdrantStatement.java`
- `src/main/java/org/qdrant/jdbc/QdrantResultSet.java`

Build config:

- `build.gradle.kts`

## Next Good Improvements

- `LIMIT`
- selecting specific columns
- better metadata for DBeaver
- payload field mapping
- pagination
- tests against a real Qdrant container
