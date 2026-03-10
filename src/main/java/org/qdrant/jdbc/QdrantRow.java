package org.qdrant.jdbc;

final class QdrantRow {
    private final String id;
    private final String payload;
    private final String vector;

    QdrantRow(String id, String payload, String vector) {
        this.id = id;
        this.payload = payload;
        this.vector = vector;
    }

    String id() {
        return id;
    }

    String payload() {
        return payload;
    }

    String vector() {
        return vector;
    }
}
