plugins {
    alias(libs.plugins.pixelbuddy.android.library)
    alias(libs.plugins.pixelbuddy.android.compose)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
