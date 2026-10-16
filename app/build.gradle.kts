plugins {
    id("com.android.application")
}

android {
    namespace = "dev.solodev.disable_apps_network"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.solodev.disable_apps_network"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
