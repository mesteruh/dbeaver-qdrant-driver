# Qdrant Driver for DBeaver

Open Qdrant in DBeaver without suffering.

Русская инструкция:

- [README.ru.md](/Users/radzhab/IdeaProjects/dbeaver-qdrant-plugin/README.ru.md)

This project gives DBeaver a small JDBC bridge for Qdrant, so collections show up like tables and you can inspect data with normal table browsing or a simple query.

What works:

- collection list in DBeaver
- table browsing
- `SELECT * FROM collection_name`
- REST / HTTPS endpoints
- API key auth
- `verify=false` for self-signed TLS
- direct gRPC mode if you really need it

What this is not:

- not a full SQL engine
- not full JDBC coverage
- not for writes yet

## Why It Exists

Qdrant is great.
DBeaver is great.
Out of the box they do not vibe together.

This repo fixes that.

## Quick Start

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

then use this in DBeaver:

- Driver class: `org.qdrant.jdbc.QdrantDriver`
- JDBC URL: `jdbc:qdrant://localhost:15672`

Driver properties:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

That is the main happy path.

## Install In DBeaver

### 1. Download the jar

Best option:

- open GitHub `Releases`
- download the latest jar

If you want the newest CI build instead:

- open GitHub `Actions`
- open the latest green run on `main`
- download the `Artifact`

Rule of thumb:

- `Releases` for humans
- `Artifacts` for testing

### 2. Create the driver

In DBeaver:

1. Open `Database` -> `Driver Manager`
2. Click `New`
3. Choose `Generic`

![Driver manager](docs/screenshots/driver-manager.png)

Set:

- Driver Name: `Qdrant`
- Class Name: `org.qdrant.jdbc.QdrantDriver`

![Driver settings](docs/screenshots/driver-settings.png)

### 3. Add the jar

Open the `Libraries` tab and add the downloaded jar.

![Driver libraries](docs/screenshots/driver-libraries.png)

### 4. Create the connection

Use:

```text
jdbc:qdrant://localhost:15672
```

Then add these driver properties:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### 5. Open data

If everything is correct:

- collections appear as tables
- opening a table loads points from that collection

## Copy-Paste Configs

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

Use this only if you actually expose Qdrant gRPC directly.

JDBC URL:

```text
jdbc:qdrant://127.0.0.1:6334
```

Properties:

```text
transport=grpc
https=false
```

## SQL Support

Supported right now:

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
- rich query parsing

## Build From Source

Requirements:

- Java installed locally
- Gradle wrapper is already in the repo

Build:

```bash
GRADLE_USER_HOME=$PWD/.gradle-local ./gradlew clean shadowJar
```

Output:

```text
build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar
```

The project builds locally with Java 21 but emits Java 11 bytecode, so the jar works with DBeaver on Java 17.

## Release Flow

This repo ships jar files in two places:

- push to `main` => GitHub Actions `Artifacts`
- push tag `v*` => GitHub `Release` with attached jar

Create a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Troubleshooting

### `No subject alternative DNS name matching localhost found`

Your certificate does not contain `localhost` in SAN.

Use one of these:

- connect with the real hostname from the certificate
- set `verify=false`

### `HTTP 404` / `UNIMPLEMENTED`

You are probably talking to a REST endpoint with gRPC mode.

Set:

```text
transport=rest
```

### DBeaver acts like it still loads an old driver

Remove old Qdrant jars from the driver settings, add only the latest jar, then restart DBeaver.

## Roadmap

Good next upgrades:

- `LIMIT`
- selecting specific columns
- better DBeaver metadata
- payload field mapping
- pagination
- tests against a real Qdrant container

## Project Layout

Main code:

- `src/main/java/org/qdrant/jdbc/QdrantDriver.java`
- `src/main/java/org/qdrant/jdbc/QdrantConnection.java`
- `src/main/java/org/qdrant/jdbc/QdrantDatabaseMetaData.java`
- `src/main/java/org/qdrant/jdbc/QdrantStatement.java`
- `src/main/java/org/qdrant/jdbc/QdrantResultSet.java`

Build config:

- `build.gradle.kts`
