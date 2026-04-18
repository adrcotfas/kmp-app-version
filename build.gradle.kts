plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.github.adrcotfas"
version = "1.0.0"

val pluginName = "KMP App Version"
val pluginDescription =
    "Single source of truth for KMP app versions: drives Android versionCode/versionName and iOS xcconfig from libs.versions.toml"
val pluginUrl = "https://github.com/adrcotfas/kmp-app-version"

gradlePlugin {
    website = pluginUrl
    vcsUrl  = pluginUrl
    plugins {
        create("kmpAppVersion") {
            id                  = "io.github.adrcotfas.kmp-app-version"
            implementationClass = "io.github.adrcotfas.KmpAppVersionPlugin"
            displayName         = pluginName
            description         = pluginDescription
            tags                = listOf("kotlin-multiplatform", "kmp", "versioning", "ios", "android", "gradle-plugin")
        }
    }
}

dependencies {
    compileOnly(libs.agp)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "kmp-app-version", version.toString())
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name.endsWith("PluginMarkerMaven")) return@configureEach
        pom {
            name = pluginName
            description = pluginDescription
            inceptionYear = "2026"
            url = pluginUrl
            licenses {
                license {
                    name = "The Apache License, Version 2.0"
                    url  = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id           = "adrcotfas"
                    name         = "Adrian Cotfas"
                    email        = "adrcotfas@duck.com"
                    url          = "https://github.com/adrcotfas"
                }
            }
            scm {
                url                 = pluginUrl
                connection          = "scm:git:git://github.com/adrcotfas/kmp-app-version.git"
                developerConnection = "scm:git:ssh://git@github.com/adrcotfas/kmp-app-version.git"
            }
        }
    }
}
