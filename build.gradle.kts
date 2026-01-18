plugins {
    kotlin("jvm") version "2.0.21"
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.ssquadteam"
version = "1.0.0"

repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    compileOnly("com.github.ssquadteam:TaleLib:main-SNAPSHOT")
    compileOnly(files("libs/VideoMaps.jar"))
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.shadowJar {
    archiveBaseName.set("TempleMaps")
    archiveVersion.set("")
    archiveClassifier.set("")

    exclude("kotlin/**")
    exclude("META-INF/kotlin*")
    exclude("META-INF/*.kotlin_module")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
