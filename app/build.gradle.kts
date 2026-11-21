plugins {
    alias(libs.plugins.pixelbuddy.android.application)
    alias(libs.plugins.pixelbuddy.android.compose)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy"

    defaultConfig {
        applicationId = "io.github.nobuddyorg.pixelbuddy"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        // Per-app language support: generates locales_config.xml from the values-* folders (#19).
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(projects.core.data)
    implementation(projects.core.weather)
    implementation(projects.core.designSystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.androidx.test.core)
}
