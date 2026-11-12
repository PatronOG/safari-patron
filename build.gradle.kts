import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("net.fabricmc.fabric-loom") version "1.17.21"
    kotlin("jvm") version "2.4.20"
}

val targetMinecraft = property("minecraft_version").toString()
version = property("mod_version").toString()
group = "safaripatron"
base.archivesName = "safari-patron"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://maven.azureaaron.net/releases") }
        filter { includeModule("net.azureaaron", "hm-api") }
    }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$targetMinecraft")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    // Hypixel Mod API (parti üyeleri için). Jar'ın içine gömülür, ayrıca kurmaya gerek yok.
    implementation(include("net.azureaaron:hm-api:${property("hm_api_version")}")!!)
    // Mod Menu: only needed to compile the config button, not bundled
    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
}

tasks.processResources {
    val props = mapOf("version" to version.toString(), "minecraft" to targetMinecraft)
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.jar {
    from("LICENSE")
    from("licenses") { into("licenses") }
}

tasks.withType<JavaCompile>().configureEach { options.release = 25 }
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_25 } }
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
