plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.mcpbeet"
version = "1.0.0"


dependencies {
    // MCP Kotlin SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")
    
    // Ktor for HTTP clients
    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-cio:3.1.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.1")
    implementation("io.ktor:ktor-client-logging:3.1.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Kotlinx IO for MCP transport
    implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")
    
    // Date/Time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.ktor:ktor-client-mock:2.3.7")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
}

application {
    mainClass.set("com.mcpbeet.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.shadowJar {
    archiveBaseName.set("mcp-beet-server")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "com.mcpbeet.MainKt"
    }
}