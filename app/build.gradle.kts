
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.example.swapstyleproject"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.swapstyleproject"
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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    //Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    //Firebase AuthUI
    implementation (libs.firebase.ui.auth)

    // Also add the dependency for the Google Play services library and specify its version
    implementation(libs.play.services.auth)

    //Firestore
    implementation (libs.firebase.firestore.ktx)

    //Storage
    implementation(libs.firebase.storage)

    implementation (libs.androidx.credentials.v150alpha05)

    implementation (libs.androidx.navigation.fragment.ktx)
    implementation (libs.androidx.navigation.ui.ktx)

    //Circleimageview
    implementation (libs.circleimageview)

    implementation (libs.ucrop)

    // Google Maps
    implementation (libs.places)
    implementation (libs.play.services.location)

    //lottie
    implementation(libs.lottie)

    // SwipeRefreshLayout
    implementation(libs.androidx.swiperefreshlayout)

}