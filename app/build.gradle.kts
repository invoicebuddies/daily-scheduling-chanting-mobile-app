import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.chantingscheduler.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chantingscheduler.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.4"

        // Update prompt (GitHub Releases check) — a future Play Store build
        // flips this to false, since Play forbids self-updating outside the store.
        buildConfigField("boolean", "GITHUB_UPDATE_CHECK", "true")

        // Ko-fi tip link on the Support screen — a future Play Store build
        // flips this to false (Play restricts external payment links).
        buildConfigField("boolean", "SHOW_TIP_LINK", "true")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Release APKs are additionally COPIED (not renamed) to
    // build/outputs/release-named/ with the user-facing names the README's
    // download table references. The original package outputs stay untouched
    // so adb install and Android Studio keep working, and rebuilds can never
    // leave a stale APK sitting under the release name.
    afterEvaluate {
        val appVersion = android.defaultConfig.versionName ?: "dev"
        val copyReleaseApks = tasks.register("copyReleaseApks", Copy::class) {
            from(layout.buildDirectory.dir("outputs/apk/release")) {
                // Unsigned CI builds (no keystore.properties) name outputs
                // app-<abi>-release-unsigned.apk; signed builds drop the suffix.
                // Unsigned APKs can never be installed, so their copies must
                // carry an unmistakable name — the update checker's asset
                // matcher also refuses to treat them as downloads.
                include("app-*-release*.apk")
                rename { name ->
                    val unsigned = name.removeSuffix(".apk").endsWith("-unsigned")
                    val abi = name.removePrefix("app-")
                        .removeSuffix("-release-unsigned.apk")
                        .removeSuffix("-release.apk")
                    "ChantingBuddy-v$appVersion-$abi${if (unsigned) "-UNSIGNED" else ""}.apk"
                }
            }
            into(layout.buildDirectory.dir("outputs/release-named"))
            doFirst {
                // Drop stale named APKs (e.g. a different version built earlier)
                // so release uploads can never include a previous build.
                delete(layout.buildDirectory.dir("outputs/release-named"))
            }
        }
        tasks.matching { it.name.startsWith("package") && it.name.endsWith("Release") }
            .configureEach { finalizedBy(copyReleaseApks) }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric Compose tests need merged resources.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:scheduling"))
    implementation(project(":core:player"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation(libs.compose.ui.tooling)
}
