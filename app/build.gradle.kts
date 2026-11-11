plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKey = rootProject.file("app/glassmail-release.p12")
val localSigning = java.util.Properties().apply {
    rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.dormantxxy.glassmail"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dormantxxy.glassmail"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("String", "RELEASE_REPOSITORY", "\"dormantxxy/glassmail-android\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKey.exists()) {
                storeFile = releaseKey
                storeType = "PKCS12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: localSigning.getProperty("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: localSigning.getProperty("SIGNING_KEY_ALIAS") ?: "glassmail"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                    ?: System.getenv("SIGNING_STORE_PASSWORD")
                    ?: localSigning.getProperty("SIGNING_KEY_PASSWORD")
                    ?: localSigning.getProperty("SIGNING_STORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (releaseKey.exists()) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions.jvmTarget = "17"

    packaging {
        resources.excludes += setOf(
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE",
            "META-INF/LICENSE",
            "META-INF/DEPENDENCIES"
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        xmlReport = true
        htmlReport = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
