# Qdrant Driver для DBeaver

Нормальный способ открыть Qdrant в DBeaver без боли.

Этот драйвер нужен для простого сценария:

- увидеть коллекции Qdrant в DBeaver как таблицы
- открыть данные
- сделать базовый `SELECT * FROM collection_name`

Сейчас проект заточен именно под просмотр и простое чтение.
Не под полный SQL, не под запись, не под магию.

## За 3 минуты до рабочего коннекта

Если у тебя уже работает такой Python-клиент:

```python
from qdrant_client import QdrantClient

client = QdrantClient(
    url="https://localhost:15672",
    api_key="YOUR_API_KEY",
    https=True,
    verify=False,
)
```

то в DBeaver тебе нужен вот такой конфиг:

JDBC URL:

```text
jdbc:qdrant://localhost:15672
```

Свойства драйвера:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

Класс драйвера:

```text
org.qdrant.jdbc.QdrantDriver
```

Это основной и самый важный happy path.

Вот так примерно должен выглядеть заполненный драйвер:

![Готовый конфиг драйвера](docs/screenshots/driver-ready-config.png)

## Как добавить драйвер в DBeaver

### 1. Скачай jar

Лучший вариант:

- открой GitHub `Releases`
- скачай последний jar

Если нужен самый свежий билд:

- открой GitHub `Actions`
- зайди в последний успешный прогон на `main`
- скачай `Artifact`

Запомни просто:

- `Releases` для людей
- `Artifacts` для проверки свежей сборки

### 2. Открой управление драйверами

В DBeaver:

1. Открой меню `Database`
2. Нажми `Управление драйверами`
3. Создай новый драйвер на базе `Generic`

![Управление драйверами](docs/screenshots/driver-manager.png)

Заполни:

- Имя драйвера: `Qdrant`
- Имя класса: `org.qdrant.jdbc.QdrantDriver`

![Настройки драйвера](docs/screenshots/driver-settings.png)

### 3. Добавь jar

Во вкладке `Библиотеки` добавь скачанный jar.

![Библиотеки драйвера](docs/screenshots/driver-libraries.png)

### 4. Настрой URL

Используй:

```text
jdbc:qdrant://localhost:15672
```

### 5. Добавь свойства драйвера

Укажи:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### 6. Проверь, что всё взлетело

Если всё хорошо:

- коллекции появятся как таблицы
- таблицы будут открываться
- `SELECT * FROM your_collection` начнёт работать

## Готовые конфиги

### HTTPS + API key + self-signed TLS

JDBC URL:

```text
jdbc:qdrant://localhost:15672
```

Свойства:

```text
transport=rest
https=true
verify=false
api_key=YOUR_API_KEY
```

### HTTPS + API key + валидный сертификат

JDBC URL:

```text
jdbc:qdrant://qdrant.example.com:443
```

Свойства:

```text
transport=rest
https=true
verify=true
api_key=YOUR_API_KEY
```

### Прямой gRPC endpoint

Используй только если у тебя реально открыт gRPC Qdrant, обычно `6334`.

JDBC URL:

```text
jdbc:qdrant://127.0.0.1:6334
```

Свойства:

```text
transport=grpc
https=false
```

## Что поддерживается по SQL

Сейчас поддерживается:

```sql
SELECT * FROM my_collection
```

Возвращаются колонки:

- `id`
- `payload`
- `vector`

Пока не поддерживается:

- `INSERT`
- `UPDATE`
- `DELETE`
- `JOIN`
- подготовленные выражения
- произвольный SQL

## Если что-то сломалось

### Ошибка про `localhost` и сертификат

Если видишь что-то вроде:

```text
No subject alternative DNS name matching localhost found
```

значит в сертификате нет `localhost`.

Что делать:

- использовать реальный hostname из сертификата
- или поставить `verify=false`

### Ошибка `HTTP 404` / `UNIMPLEMENTED`

Обычно это значит, что ты попал в REST endpoint, но драйвер пытается говорить по gRPC.

Проверь, что у тебя стоит:

```text
transport=rest
```

### DBeaver как будто грузит старую версию драйвера

Сделай жёстко:

1. удали старые Qdrant jars из настроек драйвера
2. добавь только новый jar
3. перезапусти DBeaver

## Как собрать самому

Сборка:

```bash
GRADLE_USER_HOME=$PWD/.gradle-local ./gradlew clean shadowJar
```

Результат:

```text
build/libs/dbeaver-qdrant-plugin-1.0-SNAPSHOT.jar
```

Проект локально собирается на Java 21, но отдаёт байткод Java 11, поэтому jar нормально грузится в DBeaver на Java 17.

## Как публикуется jar

В репе настроено так:

- push в `main` => jar попадает в GitHub Actions `Artifacts`
- push тега `v*` => создаётся GitHub `Release` и jar прикладывается туда

Создать релиз:

```bash
git tag v0.1.0
git push origin v0.1.0
```

## Что это за проект по-честному

- это практичный мост между Qdrant и DBeaver
- это не полноценная SQL-база
- это не полный JDBC-драйвер
- это удобный способ быстро посмотреть данные
