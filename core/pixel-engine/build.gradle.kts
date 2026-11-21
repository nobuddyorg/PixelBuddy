plugins {
    alias(libs.plugins.pixelbuddy.jvm.library)
    alias(libs.plugins.pitest)
}

dependencies {
    testImplementation(libs.junit.jupiter.params)
}

// The engine is pure logic: keep it thoroughly tested (#6).
kover {
    reports {
        verify {
            rule("Pixel engine line coverage") {
                minBound(90)
            }
        }
    }
}

// Mutation testing, run nightly (.github/workflows/nightly.yml): ./gradlew :core:pixel-engine:pitest
pitest {
    pitestVersion = libs.versions.pit.get()
    junit5PluginVersion =
        libs.versions.junit5.pit
            .get()
    targetClasses = setOf("io.github.nobuddyorg.pixelbuddy.core.pixelengine.*")
    threads = 4
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
    // The golden-image test reads files relative to the module directory.
    jvmArgs = listOf("-Dpixelengine.record=false")
}

tasks.test {
    // Forwards -Dpixelengine.record=true to re-record the golden images.
    systemProperty("pixelengine.record", System.getProperty("pixelengine.record") ?: "false")
}
