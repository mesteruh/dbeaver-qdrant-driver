plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.qdrant"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.qdrant:client:1.17.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.grpc:grpc-core:1.75.0")
    implementation("io.grpc:grpc-netty-shaded:1.75.0")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles() // КРИТИЧНО: склеивает файлы в META-INF/services/
    manifest {
        attributes["Main-Class"] = "org.qdrant.jdbc.QdrantDriver"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
