plugins {
    alias(libs.plugins.pixelbuddy.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.core.weather"
}

// The only module that uses the network (#11).
dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
}
