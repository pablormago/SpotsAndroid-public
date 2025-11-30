import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.kapt")

}

android {
    namespace = "com.spotitfly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spotitfly.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Optional Firebase only if JSON exists
if (File(projectDir, "google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    val composeUi = "1.7.4"
    val material3 = "1.4.0"

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-graphics:$composeUi")
    implementation("androidx.compose.ui:ui-tooling-preview:$composeUi")
    implementation("androidx.compose.material3:material3:$material3")
    implementation("androidx.compose.material:material-icons-extended:${composeUi}")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling:$composeUi")

    // Material Components for Theme.Material3 in manifest
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.core:core-ktx:1.12.0")

    // Google Maps SDK (Java) — no maps-compose to avoid Kotlin binary mismatch
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("androidx.compose.runtime:runtime-saveable:${composeUi}")

    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx") // si vas a subir avatar luego
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    // Google Maps (Compose)
    implementation("com.google.maps.android:maps-compose:4.4.1")

// (Por si no la tienes ya) SDK clásico de Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation ("com.google.firebase:firebase-messaging-ktx")


}
