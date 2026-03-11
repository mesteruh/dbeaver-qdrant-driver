# Qdrant Driver for DBeaver

Open Qdrant in DBeaver without pain.

This project gives DBeaver a small JDBC bridge for Qdrant, so collections show up like tables and you can browse data without building your own admin UI.

Russian guide:

- [README.ru.md](README.ru.md)

## What it does

- shows Qdrant collections as tables
- opens collection data in DBeaver
- supports `SELECT * FROM collection_name`
- works with REST / HTTPS endpoints
- supports API key auth
- supports `verify=false` for self-signed TLS

## The fast path

If Qdrant already works for you like this:

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

```text
JDBC URL: jdbc:qdrant://localhost:15672

transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

Driver class:

```text
org.qdrant.jdbc.QdrantDriver
```

Final result should look roughly like this:

![Ready driver config](docs/screenshots/driver-ready-config.png)

## Add it to DBeaver

### 1. Download the jar

Best option:

- open GitHub `Releases`
- download the latest jar

If you want the newest CI build:

- open GitHub `Actions`
- open the latest successful run
- download the `Artifact`

Simple rule:

- `Releases` for normal use
- `Artifacts` for testing the latest build

### 2. Open driver management

In DBeaver:

1. open `Database`
2. open `Driver Manager`
3. create a new `Generic` driver

![Driver manager](docs/screenshots/driver-manager.png)

### 3. Fill the driver settings

Use:

- Driver Name: `Qdrant`
- Class Name: `org.qdrant.jdbc.QdrantDriver`

You can keep this URL template:

```text
jdbc:qdrant:{host}:{port}
```

![Driver settings](docs/screenshots/driver-settings.png)

### 4. Add the jar

Open the `Libraries` tab.

Then:

1. click `Add File`
2. choose the downloaded jar
3. make sure it appears in the library list

![How to add the jar](docs/screenshots/driver-add-file.png)

After that it should look like this:

![Driver libraries](docs/screenshots/driver-libraries.png)

### 5. Create the connection

Use:

```text
jdbc:qdrant://localhost:15672
```

And set these driver properties:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### 6. Done

If everything is correct:

- collections appear as tables
- tables open
- `SELECT * FROM your_collection` works

## Copy-paste configs

### HTTPS + API key + self-signed TLS

```text
jdbc:qdrant://localhost:15672

transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### HTTPS + API key + trusted certificate

```text
jdbc:qdrant://qdrant.example.com:443

transport=rest
https=true
verify=true
api_key=YOUR_API_KEY
```

### Direct gRPC

Only use this if you actually expose Qdrant gRPC directly.

```text
jdbc:qdrant://127.0.0.1:6334

transport=grpc
https=false
```

## SQL support

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
- complex SQL

## If it breaks

### `No subject alternative DNS name matching localhost found`

Your certificate does not include `localhost` in SAN.

Fix:

- use the real hostname from the certificate
- or set `verify=false`

### `HTTP 404` / `UNIMPLEMENTED`

You are probably talking to a REST endpoint with gRPC mode.

Check:

```text
transport=rest
```

### DBeaver behaves like it still loads an old driver

Do this:

1. remove old Qdrant jars
2. add only the latest jar
3. restart DBeaver

## Build

```bash
GRADLE_USER_HOME=$PWD/.gradle-local ./gradlew clean shadowJar
```

Output jar:

```text
build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar
```

The project builds locally with Java 21 but emits Java 11 bytecode, so the jar works with DBeaver on Java 17.

## Release flow

This repo publishes jars in two places:

- push to `main` => GitHub Actions `Artifacts`
- push tag `v*` => GitHub `Release` with jar attached

Create a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```
