plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "hu.memomill.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "hu.memomill.app"
        minSdk = 26
        targetSdk = 34
        versionCode = run
        versionName = "2.0.$run"
    }

    /* Saját kulcs, hogy az új APK a régire telepíthető legyen (csak oldaltelepítéshez). */
    signingConfigs {
        create("memo") {
            storeFile = file("../memomill.jks")
            storePassword = "memomill"
            keyAlias = "memomill"
            keyPassword = "memomill"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("memo")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("memo")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}

/* A webes app (index.html) bemásolása az APK-ba */
val copyWeb by tasks.registering(Copy::class) {
    from(rootProject.file("../index.html"), rootProject.file("../manifest.json"))
    from(rootProject.file("../icons")) { into("icons") }
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(copyWeb) }
