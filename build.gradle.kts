
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
    }

    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") }
}

sourceSets {
    main {
        java.srcDir("src/main/gen")
    }
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val ktlintConfig = configurations.create("ktlint")
val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

dependencies {
    ktlintConfig(libs.ktlint)
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation(libs.ide.starter)
    testImplementation("com.jetbrains.intellij.tools:ide-starter:253.29346.240")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.vintage:junit-vintage-engine:5.9.2")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.20.2")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins")
                .orNull
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
        )
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Go)
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract the plugin description from plugin-description.md and provide it for the plugin manifest
        description = providers.fileContents(layout.projectDirectory.file("plugin-description.md")).asText.map {
            markdownToHTML(it)
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    register("ktlintCheck") {
        group = "verification"
        description = "Check Kotlin code style with ktlint"
        doFirst {
            val ktlintJar = ktlintConfig.files.first { it.name.contains("ktlint-cli") }
            println("ktlint check with: ${ktlintJar.absolutePath}")
            println("Run: java -jar ${ktlintJar.absolutePath} --reporter=plain src/**/*.kt")
        }
    }

    register("ktlintFormat") {
        group = "formatting"
        description = "Format Kotlin code with ktlint"
        doFirst {
            val ktlintJar = ktlintConfig.files.first { it.name.contains("ktlint-cli") }
            println("ktlint format with: ${ktlintJar.absolutePath}")
            println("Run: java -jar ${ktlintJar.absolutePath} -F src/**/*.kt")
        }
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }

    prepareSandbox {
        from(layout.projectDirectory.dir("src/main/resources/platform")) {
            into(pluginName.map { "$it/platform" })
        }
        from(layout.projectDirectory.dir("src/main/resources/runtime")) {
            into(pluginName.map { "$it/runtime" })
        }
        from(layout.projectDirectory.dir("src/main/lua")) {
            into(pluginName.map { "$it/lua"})
        }
    }

    test {
        useJUnitPlatform()
        dependsOn(prepareSandbox)
        systemProperty("sandbox.home", layout.buildDirectory.dir("idea-sandbox").get().asFile.absolutePath)
        systemProperty("plugin.name", providers.gradleProperty("pluginName").get())
    }

    val integrationTest by intellijPlatformTesting.testIdeUi.registering {
        task {
            val integrationTestSourceSet = sourceSets.getByName("integrationTest")
            testClassesDirs = integrationTestSourceSet.output.classesDirs
            classpath = integrationTestSourceSet.runtimeClasspath
            useJUnitPlatform()
            dependsOn(prepareSandbox)
            systemProperty("path.to.build.plugin", layout.buildDirectory.dir("distributions").map { it.asFile.resolve("lunar-${providers.gradleProperty("pluginVersion").get()}.zip").absolutePath }.get())
        }
    }
}
