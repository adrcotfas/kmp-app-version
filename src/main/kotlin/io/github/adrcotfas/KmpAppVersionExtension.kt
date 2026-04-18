package io.github.adrcotfas

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class KmpAppVersionExtension {
    abstract val versionCodeKey: Property<String>
    abstract val versionNameKey: Property<String>
    abstract val xcconfigFile: RegularFileProperty
}
