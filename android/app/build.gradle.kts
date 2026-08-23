import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.reader(Charsets.UTF_8).use { reader: java.io.Reader ->
        localProperties.load(reader)
    }
}

val flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "17"
val flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "1.1.6"
val crashR2Endpoint = System.getenv("HAN1ME_CRASH_R2_ENDPOINT") ?: ""
val crashR2Bucket = System.getenv("HAN1ME_CRASH_R2_BUCKET") ?: ""
val crashR2AccessKey = System.getenv("HAN1ME_CRASH_R2_ACCESS_KEY") ?: ""
val crashR2SecretKey = System.getenv("HAN1ME_CRASH_R2_SECRET_KEY") ?: ""

android {
    namespace = "com.liar.han1meplus"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    defaultConfig {
        applicationId = "com.liar.han1meplus"
        minSdk = 27
        targetSdk = 37
        versionCode = flutterVersionCode.toInt()
        versionName = flutterVersionName
        buildConfigField("String", "CRASH_R2_ENDPOINT", "\"$crashR2Endpoint\"")
        buildConfigField("String", "CRASH_R2_BUCKET", "\"$crashR2Bucket\"")
        buildConfigField("String", "CRASH_R2_ACCESS_KEY", "\"$crashR2AccessKey\"")
        buildConfigField("String", "CRASH_R2_SECRET_KEY", "\"$crashR2SecretKey\"")
    }

    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.10")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
}
