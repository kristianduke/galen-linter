import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val pluginId = "dev.kristian.galenlinter"

/** Stand-in host, so a forgotten `-PpluginRepositoryUrl` produces an obviously wrong URL, not a plausible one. */
val PLACEHOLDER_REPOSITORY_URL = "https://REPLACE-WITH-YOUR-HOST.invalid/idea-plugins"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.2")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // Form/@NotNull bytecode instrumentation only matters for Java sources and .form files, of
    // which this plugin has none. It also requires a JetBrains Runtime layout that a stock JDK
    // does not provide, so leaving it on breaks the build on a plain JDK 21.
    instrumentCode = false

    pluginConfiguration {
        id = pluginId
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "243"
            // Open-ended: the plugin uses no experimental APIs, so don't pin an upper bound.
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    test {
        // IntelliJ platform tests are JUnit 3/4 style (BasePlatformTestCase).
        useJUnit()
        // Platform assertions are how the IDE reports contract violations; tests must fail on
        // them rather than pass quietly.
        jvmArgs("-ea")
    }
}

/**
 * Pin the distribution's file name.
 *
 * The release workflow has to know the archive name before the build runs, because that name is
 * baked into the download URL inside `updatePlugins.xml`. Deriving it from the plugin's display
 * name would couple the URL to a cosmetic setting.
 */
tasks.named<Zip>("buildPlugin") {
    archiveFileName = providers.gradleProperty("pluginVersion").map { "galen-linter-$it.zip" }
}

/**
 * Generates the `updatePlugins.xml` that turns a plain HTTPS directory into an IntelliJ plugin
 * repository. Upload it next to the plugin zip; developers add its URL once under
 * Settings | Plugins | gear | Manage Plugin Repositories and get update notifications from then on.
 *
 * The download URL is `pluginRepositoryUrl` + the zip's file name. Hosts that do not serve the
 * archive from a predictable path — a GitHub release asset, a signed S3 link — can override the
 * whole thing with `pluginDownloadUrl`.
 */
val generateUpdatePlugins by tasks.registering {
    // Everything the execution phase needs is captured here, as values: the configuration cache
    // cannot serialize references back into this script.
    val placeholderUrl = PLACEHOLDER_REPOSITORY_URL
    val archiveFileName = tasks.named<Zip>("buildPlugin").flatMap { it.archiveFileName }
    val repositoryUrl = providers.gradleProperty("pluginRepositoryUrl").orElse(placeholderUrl)
    val downloadUrlOverride = providers.gradleProperty("pluginDownloadUrl")
    val id = pluginId
    val pluginName = providers.gradleProperty("pluginName")
    val pluginVersion = providers.gradleProperty("pluginVersion")
    val output = layout.buildDirectory.file("distributions/updatePlugins.xml")

    inputs.property("downloadUrl", downloadUrlOverride.orElse(repositoryUrl))
    inputs.property("version", pluginVersion)
    outputs.file(output)

    doLast {
        val downloadUrl = downloadUrlOverride.orNull
            ?: repositoryUrl.get().trimEnd('/') + '/' + archiveFileName.get()
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <plugins>
              <plugin id="$id" url="$downloadUrl" version="${pluginVersion.get()}">
                <name>${pluginName.get()}</name>
                <vendor>Kristian</vendor>
                <idea-version since-build="243"/>
                <description><![CDATA[Editor support and linting for Galen Framework page specs (.gspec).]]></description>
              </plugin>
            </plugins>

        """.trimIndent()
        output.get().asFile.writeText(xml)
        if (downloadUrlOverride.orNull == null && repositoryUrl.get() == placeholderUrl) {
            logger.warn(
                "updatePlugins.xml was written with a placeholder host. Rebuild with " +
                    "-PpluginRepositoryUrl=https://your-host/path before uploading it.",
            )
        }
    }
}

tasks.named("buildPlugin") {
    finalizedBy(generateUpdatePlugins)
}
