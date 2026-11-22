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
    implementation(projects.core.weather)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
