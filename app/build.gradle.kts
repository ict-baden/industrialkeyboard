import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "de.ictbaden.industrialkeyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.ictbaden.industrialkeyboard"
        minSdk = 26
        targetSdk = 36
        // The CI passes -PversionCode (run number) and -PversionName (e.g. 1.0.0-ci.12).
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "2.2.1"
        buildConfigField("String", "COMMIT", "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "local"}\"")
    }

    // Release key from the CI secrets (richtlinien/05-signieren.md); without it the release APK stays unsigned.
    val keystore = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            // Rugged Android devices from Android 8 on are ARM only. Without x86 the OCR library of
            // ML Kit (about 11 MB per ABI) and zxing-cpp are in the APK twice instead of four times.
            // Debug builds keep x86/x86_64 for the emulator.
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    // The language can be switched in the app and by the MDM (richtlinien/13): an app bundle from
    // Play must therefore contain every language, not only the one of the device.
    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        // Covered by the bundle setting above; Play Core on-demand languages are not needed.
        disable += "AppBundleLocaleChanges"
        // "1-2-3" and "7-8-9" are rows of keys, not ranges: an en dash would be wrong.
        disable += "TypographyDashes"
        // Release is ARM only on purpose (see buildTypes.release.ndk): no rugged ChromeOS devices.
        disable += "ChromeOsAbiSupport"
        // Kept visible, but no warning: targetSdk 37 waits until it can be tested on an Android 17
        // device (Play requires it from August 2027); library updates come through Dependabot.
        informational += listOf("OldTargetApi", "GradleDependency")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Screenshot tests (docs images) only run on request: ./gradlew recordRoborazziDebug -Pscreenshots
                if (!project.hasProperty("screenshots")) it.exclude("**/screenshots/**")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("com.google.android.material:material:1.14.0")
    // ExploreByTouchHelper: screen-reader nodes for the canvas-drawn keys. Already transitive (and in
    // THIRD-PARTY-NOTICES.txt); listed to pin the API in use.
    implementation("androidx.customview:customview:1.2.0")
    // Inline autofill suggestions (Android 11+): the styles of the chips the autofill service draws.
    implementation("androidx.autofill:autofill:1.3.0")
    // Camera scan on devices without a scan engine (issue #17): CameraX for the picture,
    // zxing-cpp (the same engine as Lupe) for decoding. Both Apache-2.0.
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2") {
        // Only PreviewView is used; video recording (with media3) is not needed.
        exclude(group = "androidx.camera", module = "camera-video")
        exclude(group = "androidx.media3")
    }
    implementation("io.github.zxing-cpp:android:3.1.1")
    // Emoji panel like Gboard (issue #21). Without emoji2-bundled: its own emoji font (~10 MB) is not
    // needed, Android's font draws the emoji.
    implementation("androidx.emoji2:emoji2-emojipicker:1.7.0") {
        exclude(group = "androidx.emoji2", module = "emoji2-bundled")
    }
    // QR code of the configuration (export as barcode). MIT, one class, no dependencies.
    implementation("io.nayuki:qrcodegen:1.8.0")
    // Text recognition (OCR, issues #19): Google ML Kit with the Latin model inside the APK, offline.
    // Not open source (ML Kit terms); Till decided for it. Its usage statistics cannot leave the
    // device: the manifest removes the internet permission ML Kit brings.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit-ktx:1.3.0")
}

// Licence texts for the Info section (richtlinien/07): the repo-root files, added to the APK assets.
// AGP 9 only accepts generated asset folders through the variant API.
abstract class CopyNotices : DefaultTask() {
    @get:InputFiles
    abstract val notices: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        notices.forEach { it.copyTo(File(dir, it.name)) }
    }
}

val copyNotices = tasks.register<CopyNotices>("copyNotices") {
    notices.from(rootProject.file("LICENSE"), rootProject.file("THIRD-PARTY-NOTICES.txt"))
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(copyNotices, CopyNotices::outputDir) }
}
