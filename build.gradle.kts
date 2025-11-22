import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("fabric-loom")
    id("maven-publish")
    id("org.jetbrains.kotlin.plugin.serialization")
    java
    id("com.diffplug.spotless")
}
group = property("maven_group")!!
version = property("mod_version")!!

repositories {
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "babbaj-repo"
        url = uri("https://babbaj.github.io/maven/")
    }
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("maven.modrinth:modmenu:${property("mod_menu_version")}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("dev.babbaj:nether-pathfinder:${property("nether_pathfinder_version")}")
    modImplementation("meteordevelopment:baritone:${property("baritone_version")}")
    implementation("org.lwjgl:lwjgl-stb:${property("lwjgl_version")}")
    implementation("com.squareup.okhttp3:okhttp:${property("ok_http_version")}")
    implementation("org.apache.maven:maven-artifact:${property("maven_artifact_version")}")
}
tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(getProperties())
            expand(mutableMapOf("version" to project.version))
        }
    }

    loom {
        splitEnvironmentSourceSets()
        accessWidenerPath = file("src/main/resources/infinite.accesswidener")
    }

    fabricApi {
        configureDataGeneration {
            client = true
        }
    }
    java {
        // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
        // if it is present.
        // If you remove this line, sources will not be generated.
        withSourcesJar()

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    jar {
        val archiveName = project.base.archivesName
        inputs.property("archivesName", archiveName)
        from(sourceSets["client"].output)
        from("LICENSE") {
            rename { fileName -> "${fileName}_$archiveName" }
        }
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifact(remapJar) {
                    builtBy(remapJar)
                }
                artifact(kotlinSourcesJar) {
                    builtBy(remapSourcesJar)
                }
            }
        }

        // select the repositories you want to publish to
        repositories {
            mavenLocal()
        }
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    compileKotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
    }
    groovyGradle {
        target("**/build.gradle")
        greclipse()
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint()
    }
}
