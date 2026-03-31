plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.8"
    application
}

group = "com.greenharvest"
version = "1.0.0"

application {
    mainClass.set("com.greenharvest.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server Core & Netty Engine
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    
    // Content Negotiation & Jackson (JSON)
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    
    // Logging (logback handles output, no Ktor CallLogging plugin needed)
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Firebase Admin SDK for Push Notifications
    implementation("com.google.firebase:firebase-admin:9.2.0")

    // Ktor Client for Self-Wake Mechanism
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-cio-jvm")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}
