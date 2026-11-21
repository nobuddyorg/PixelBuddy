plugins {
    alias(libs.plugins.pixelbuddy.android.library)
    alias(libs.plugins.pixelbuddy.android.compose)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.feature.settings"
}

dependencies {
    implementation(projects.core.designSystem)
    implementation(projects.core.data)
}
