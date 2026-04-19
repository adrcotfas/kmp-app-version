package io.github.adrcotfas

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KmpAppVersionPluginTest {

    @TempDir
    lateinit var projectDir: File

    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var versionCatalog: File

    @BeforeEach
    fun setup() {
        settingsFile = projectDir.resolve("settings.gradle.kts").also {
            it.writeText("rootProject.name = \"test-project\"")
        }
        buildFile = projectDir.resolve("build.gradle.kts")
        versionCatalog = projectDir.resolve("gradle/libs.versions.toml").also {
            it.parentFile.mkdirs()
        }
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*tasks, "--stacktrace")
            .forwardOutput()

    // ── syncIosVersion ────────────────────────────────────────────────────────

    @Test
    fun `syncIosVersion rewrites CURRENT_PROJECT_VERSION and MARKETING_VERSION`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "42"
            app-version-name = "2.1.0"
            """.trimIndent()
        )

        val xcconfig = projectDir.resolve("iosApp/Config.xcconfig").also {
            it.parentFile.mkdirs()
            it.writeText(
                """
                CURRENT_PROJECT_VERSION=1
                MARKETING_VERSION=1.0.0
                OTHER_KEY=unchanged
                """.trimIndent() + "\n"
            )
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        val result = runner("syncIosVersion").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":syncIosVersion")?.outcome)
        val lines = xcconfig.readLines()
        assertTrue(lines.any { it == "CURRENT_PROJECT_VERSION=42" })
        assertTrue(lines.any { it == "MARKETING_VERSION=2.1.0" })
        assertTrue(lines.any { it == "OTHER_KEY=unchanged" })
    }

    @Test
    fun `syncIosVersion preserves line endings and trailing newline`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "1"
            app-version-name = "1.0"
            """.trimIndent()
        )

        val xcconfig = projectDir.resolve("iosApp/Config.xcconfig").also {
            it.parentFile.mkdirs()
            it.writeBytes("CURRENT_PROJECT_VERSION=0\r\nMARKETING_VERSION=0.0.0\r\n".toByteArray())
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        runner("syncIosVersion").build()

        val raw = xcconfig.readBytes().toString(Charsets.UTF_8)
        assertTrue("\r\n" in raw, "CRLF line endings must be preserved")
        assertTrue(raw.endsWith("\r\n"), "Trailing newline must be preserved")
    }

    @Test
    fun `syncIosVersion auto-detects xcconfig file`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "5"
            app-version-name = "5.0.0"
            """.trimIndent()
        )

        val xcconfig = projectDir.resolve("iosApp/Configuration/Config.xcconfig").also {
            it.parentFile.mkdirs()
            it.writeText("CURRENT_PROJECT_VERSION=0\nMARKETING_VERSION=0.0\n")
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        runner("syncIosVersion").build()

        val content = xcconfig.readText()
        assertTrue("CURRENT_PROJECT_VERSION=5" in content)
        assertTrue("MARKETING_VERSION=5.0.0" in content)
    }

    @Test
    fun `missing version key produces clear error`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "1"
            """.trimIndent()
        )

        projectDir.resolve("iosApp/Config.xcconfig").also {
            it.parentFile.mkdirs()
            it.writeText("CURRENT_PROJECT_VERSION=0\n")
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        val result = runner("syncIosVersion").buildAndFail()

        assertTrue("app-version-name" in result.output)
    }

    // ── setupXcodeVersionSync ─────────────────────────────────────────────────

    @Test
    fun `setupXcodeVersionSync injects pre-action into xcscheme`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "1"
            app-version-name = "1.0"
            """.trimIndent()
        )

        val scheme = projectDir.resolve("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme").also {
            it.parentFile.mkdirs()
            it.writeText(minimalXcscheme())
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        val result = runner("setupXcodeVersionSync").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":setupXcodeVersionSync")?.outcome)
        assertTrue("syncIosVersion" in scheme.readText())
    }

    @Test
    fun `setupXcodeVersionSync is idempotent`() {
        versionCatalog.writeText(
            """
            [versions]
            app-version-code = "1"
            app-version-name = "1.0"
            """.trimIndent()
        )

        val scheme = projectDir.resolve("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme").also {
            it.parentFile.mkdirs()
            it.writeText(minimalXcscheme())
        }

        buildFile.writeText("plugins { id(\"io.github.adrcotfas.kmp-app-version\") }")

        runner("setupXcodeVersionSync").build()
        val afterFirst = scheme.readText()

        runner("setupXcodeVersionSync").build()
        val afterSecond = scheme.readText()

        assertEquals(afterFirst, afterSecond)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun minimalXcscheme(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Scheme LastUpgradeVersion="1540" version="1.7">
           <BuildAction parallelizeBuildables="YES">
              <BuildActionEntries>
                 <BuildActionEntry buildForTesting="YES">
                    <BuildableReference
                       BuildableIdentifier = "primary"
                       BlueprintIdentifier = "AABBCC"
                       BuildableName = "iosApp.app"
                       BlueprintName = "iosApp"
                       ReferencedContainer = "container:iosApp.xcodeproj">
                    </BuildableReference>
                 </BuildActionEntry>
              </BuildActionEntries>
           </BuildAction>
        </Scheme>
    """.trimIndent()
}
