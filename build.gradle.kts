plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "net.internetisalie"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    // Goland
    version.set("2024.3.4")
    type.set("GO")

    // IntelliJ Community
//    version.set("2024.3.4")
//    type.set("IC")

    plugins.set(listOf(/* Plugin Dependencies */))

}

sourceSets {
    main {
        java.srcDir("src/main/gen")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    prepareSandbox {
        from(layout.projectDirectory.dir("src/main/resources/platform")) {
            into(pluginName.map { "$it/platform" })
        }
        from(layout.projectDirectory.dir("src/main/lua")) {
            into(pluginName.map { "$it/lua"})
        }
    }

    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("243.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
