plugins {
    alias(libs.plugins.pixelbuddy.android.library)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.core.data"
}

dependencies {
    implementation(projects.core.pixelEngine)
}
