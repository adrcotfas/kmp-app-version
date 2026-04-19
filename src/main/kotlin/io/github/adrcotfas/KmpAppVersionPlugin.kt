package io.github.adrcotfas

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import java.io.File

private const val VERSION_CODE_KEY = "app-version-code"
private const val VERSION_NAME_KEY = "app-version-name"

class KmpAppVersionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val catalog: Provider<VersionCatalog> = project.providers.provider {
            val catalogs = project.rootProject.extensions.findByType(VersionCatalogsExtension::class.java)
            catalogs?.find("libs")?.orElse(null)
                ?: error("kmp-app-version: no version catalog named 'libs' found")
        }
        val versionCode: Provider<String> = catalog.map { c ->
            c.findVersion(VERSION_CODE_KEY).orElse(null)?.requiredVersion
                ?: error("kmp-app-version: '$VERSION_CODE_KEY' not found in libs.versions.toml")
        }
        val versionName: Provider<String> = catalog.map { c ->
            c.findVersion(VERSION_NAME_KEY).orElse(null)?.requiredVersion
                ?: error("kmp-app-version: '$VERSION_NAME_KEY' not found in libs.versions.toml")
        }

        project.pluginManager.withPlugin("com.android.application") {
            val androidComponents =
                project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                variant.outputs.forEach { output ->
                    output.versionCode.set(versionCode.map { it.toInt() })
                    output.versionName.set(versionName)
                }
            }
        }

        if (project == project.rootProject) {
            val projectDir: File = project.projectDir
            val xcconfigProvider: Provider<File> = project.providers.provider {
                findXcconfig(projectDir)
                    ?: error("kmp-app-version: no xcconfig with CURRENT_PROJECT_VERSION found.")
            }

            project.tasks.register("syncIosVersion") {
                group       = "versioning"
                description = "Rewrites xcconfig with versions from libs.versions.toml"
                doLast {
                    val code     = versionCode.get()
                    val name     = versionName.get()
                    val xcconfig = xcconfigProvider.get()
                    rewriteXcconfig(xcconfig, code, name)
                    println("kmp-app-version: $name($code) → ${xcconfig.relativeTo(projectDir)}")
                }
            }

            val syncIosTask = project.tasks.named("syncIosVersion")
            for (sub in project.subprojects) {
                sub.tasks.configureEach {
                    if ((name.startsWith("link") && name.contains("ios", ignoreCase = true)) ||
                        name == "embedAndSignAppleFrameworkForXcode") {
                        dependsOn(syncIosTask)
                    }
                }
            }

            project.tasks.register("setupXcodeVersionSync") {
                group       = "versioning"
                description = "Injects syncIosVersion pre-action into all Xcode scheme files (run once)"
                doLast {
                    val schemes = projectDir.walkTopDown()
                        .onEnter { it.name !in setOf("build", ".gradle", ".git", "node_modules", "Pods", "DerivedData") }
                        .filter { it.extension == "xcscheme" }
                        .toList()
                    if (schemes.isEmpty()) {
                        println("kmp-app-version: no .xcscheme files found under ${projectDir.name}")
                        return@doLast
                    }
                    schemes.forEach { injectPreAction(it, projectDir) }
                }
            }
        }
    }

    private fun findXcconfig(dir: File): File? =
        dir.walkTopDown()
            .onEnter { it.name !in setOf("build", ".gradle", ".git", "node_modules", "Pods", "DerivedData") }
            .filter { it.extension == "xcconfig" }
            .firstOrNull { f -> f.readLines().any { it.startsWith("CURRENT_PROJECT_VERSION=") } }

    private fun rewriteXcconfig(xcconfig: File, versionCode: String, versionName: String) {
        val original = xcconfig.readText()
        val lineEnding = if ("\r\n" in original) "\r\n" else "\n"
        val trailingNewline = original.endsWith("\n")
        val updated = original.lines().joinToString(lineEnding) { line ->
            when {
                line.startsWith("CURRENT_PROJECT_VERSION=") -> "CURRENT_PROJECT_VERSION=$versionCode"
                line.startsWith("MARKETING_VERSION=")       -> "MARKETING_VERSION=$versionName"
                else                                         -> line
            }
        }.let { if (trailingNewline) it + lineEnding else it }
        xcconfig.writeText(updated)
    }

    private fun injectPreAction(scheme: File, projectDir: File) {
        val content = scheme.readText()

        if ("syncIosVersion" in content) {
            println("kmp-app-version: pre-action already present in ${scheme.relativeTo(projectDir)}")
            return
        }

        // Extract the primary BuildableReference to wire into EnvironmentBuildable
        val refMatch = Regex(
            """<BuildableReference\s[^/]*?BuildableIdentifier\s*=\s*"primary".*?</BuildableReference>""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).find(content)

        if (refMatch == null) {
            println("kmp-app-version: no primary BuildableReference found in ${scheme.relativeTo(projectDir)}, skipping")
            return
        }

        val dollar = "$"
        val preActions = """   <PreActions>
         <ExecutionAction
            ActionType = "Xcode.IDEStandardExecutionActionsCore.ExecutionActionType.ShellScriptAction">
            <ActionContent
               title = "Sync KMP Version"
               scriptText = "cd &quot;${dollar}SRCROOT/..&quot;&#xa;./gradlew syncIosVersion&#xa;">
               <EnvironmentBuildable>
                  ${refMatch.value}
               </EnvironmentBuildable>
            </ActionContent>
         </ExecutionAction>
      </PreActions>
      """

        val updated = content.replaceFirst("<BuildActionEntries>", "$preActions<BuildActionEntries>")
        if (updated == content) {
            println("kmp-app-version: <BuildActionEntries> not found in ${scheme.relativeTo(projectDir)}, skipping")
            return
        }

        scheme.writeText(updated)
        println("kmp-app-version: pre-action injected into ${scheme.relativeTo(projectDir)}")
    }
}
