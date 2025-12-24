plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "net.lumalyte"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.dmulloy2.net/repository/public/")
    mavenLocal()
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // LumaGuilds (compile against the shadowJar which contains all LumaGuilds classes)
    compileOnly(files("../bell-claims/build/libs/LumaGuilds-0.6.0.jar"))

    // Koin (LumaGuilds uses Koin for DI - we need it to access services)
    compileOnly("io.insert-koin:koin-core:4.0.2")

    // Kotlin stdlib (for JvmClassMappingKt to convert Java Class to Kotlin KClass)
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")

    // Advanced Region Market (compile-time stubs included in src/main/java/net/alex9849)
    // IMPORTANT: The actual ARM plugin JAR (v3.5.4+) must be installed on the server at runtime
    // Download from: https://github.com/alex9849/advanced-region-market/releases

    // WorldGuard
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("ARM-Guilds-Bridge")
        archiveClassifier.set("")

        // Exclude ARM stub classes (compile-time only)
        exclude("net/alex9849/**")

        // Relocate dependencies to avoid conflicts
        relocate("org.slf4j", "net.lumalyte.armbridge.lib.slf4j")

        manifest {
            attributes(
                "Implementation-Title" to "ARM-Guilds-Bridge",
                "Implementation-Version" to version
            )
        }
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
