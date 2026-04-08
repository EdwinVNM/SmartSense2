plugins {
    alias(libs.plugins.android.application)
    //id("com.android.application")
    // Firebase (optional now; we can wire later)
    //id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.smartsense2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartsense2"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {//UI
    //implementation("androidx.appcompat:appcompat:1.7.0")
    //implementation("com.google.android.material:material:1.12.0")
    //implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Gauges (Gruzer)
    implementation("com.github.Gruzer:simple-gauge-android:0.3.1")

    // Charts (MPAndroidChart)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // WebSocket server (TooTallNate Java-WebSocket)
    implementation("org.java-websocket:Java-WebSocket:1.5.6")

    // Lifecycle (clean UI updates)
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")

    // Room (store readings locally for CSV export later)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation(libs.firebase.database)
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Firebase (optional; we’ll wire later when you’re ready)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    //implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.work:work-runtime:2.9.1")

}