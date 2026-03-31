plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.remotecompose"

    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        disable += "RestrictedApiAndroidX"
        disable += "RestrictedApi"
        abortOnError = false
    }
}

dependencies {
    api(libs.jspecify)
    api(libs.androidx.annotation)
    implementation(libs.androidx.customview)
    implementation(libs.androidx.core.ktx)
}
