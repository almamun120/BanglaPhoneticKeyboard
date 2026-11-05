plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.popykeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.popykeyboard"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "15").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.5"
    }

    // Release সাইনিং: এনভায়রনমেন্ট ভেরিয়েবল (GitHub Secrets) থেকে পড়া হয়।
    // সেট না থাকলে release APK অসাইন্ড থাকে, debug বিল্ডে কোনো প্রভাব পড়ে না।
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    val hasSigning = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // কোনো AndroidX/AppCompat লাগে না — শুধু Android framework ও Kotlin stdlib
    testImplementation("junit:junit:4.13.2")
}
