import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    `maven-publish`
}

group = "pl.blizinski"
version = "0.1.0"

kotlin {
    android {
        namespace = "pl.blizinski.microsofttodostore"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {}.configure {
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The task model, wire DTOs, content adapter and MSAL token-provider interface are all
        // platform-agnostic and live in commonMain, so a future wasmJs target (once a browser
        // OAuth token provider exists — see the design doc's Stage 5 follow-up) only needs a
        // Ktor `MicrosoftGraphNetworkSourceWasm` + store factory, not a re-extraction.
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            // Resolved via JitPack normally; substituted for the local checkout when one exists
            // as a sibling directory — see settings.gradle.kts.
            implementation("com.github.automaciej:task-sync-kotlin:v0.4.0")
        }
        androidMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.room.ktx)
            implementation(libs.work.runtime.ktx)
            implementation(libs.okhttp)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
}
