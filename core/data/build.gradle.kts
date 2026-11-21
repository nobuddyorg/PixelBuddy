plugins {
    alias(libs.plugins.pixelbuddy.android.library)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.core.data"
}

dependencies {
    implementation(projects.core.pixelEngine)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
}
