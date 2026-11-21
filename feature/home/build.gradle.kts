plugins {
    alias(libs.plugins.pixelbuddy.android.library)
    alias(libs.plugins.pixelbuddy.android.compose)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.feature.home"
}

dependencies {
    implementation(projects.core.designSystem)
    implementation(projects.core.data)
}
