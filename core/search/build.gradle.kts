plugins {
    alias(libs.plugins.pixelbuddy.jvm.library)
    alias(libs.plugins.pitest)
}

dependencies {
    testImplementation(libs.junit.jupiter.params)
}

// Search ranking is pure logic: keep it thoroughly tested (#10).
kover {
    reports {
        verify {
            rule("Search line coverage") {
                minBound(90)
            }
        }
    }
}

// Mutation testing, run nightly (.github/workflows/nightly.yml): ./gradlew :core:search:pitest
pitest {
    pitestVersion = libs.versions.pit.get()
    junit5PluginVersion =
        libs.versions.junit5.pit
            .get()
    targetClasses = setOf("io.github.nobuddyorg.pixelbuddy.core.search.*")
    threads = 4
    outputFormats = setOf("HTML", "XML")
    timestampedReports = false
}
