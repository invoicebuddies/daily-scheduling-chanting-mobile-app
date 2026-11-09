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
        versionCode = 2
        versionName = "1.1"
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

    // Release APKs carry the user-facing name the README's download table
    // references (ChantingBuddy-vX.X-<abi>.apk / -universal.apk).
    // VariantOutput.outputFileName was removed in AGP 8, so rename after
    // packaging instead.
    afterEvaluate {
        val appVersion = android.defaultConfig.versionName ?: "dev"
        tasks.matching { it.name.startsWith("package") && it.name.endsWith("Release") }
            .configureEach {
                doLast {
                    val outDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
                    outDir.listFiles { f: File -> f.isFile && f.name.startsWith("app-") && f.extension == "apk" }
                        ?.forEach { apk ->
                            val abi = apk.name.removePrefix("app-").removeSuffix("-release.apk")
                            val target = File(outDir, "ChantingBuddy-v$appVersion-$abi.apk")
                            if (target != apk && !target.exists()) apk.renameTo(target)
                        }
                }
            }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
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
    debugImplementation(libs.compose.ui.tooling)
}
