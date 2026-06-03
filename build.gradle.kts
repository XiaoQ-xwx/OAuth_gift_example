plugins {
    id("java")
    id("com.gradleup.shadow") version "9.4.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly(files("D:/Projects/MC_LinuxDo_OAuth/MC-LinuxDO-OAuth-Link/MC-LinuxDO-OAuth-Link/build/libs/MC-LinuxDO-OAuth-Link-1.0-SNAPSHOT.jar"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    shadowJar {
        archiveClassifier = ""
    }

    build {
        dependsOn(shadowJar)
    }
}
