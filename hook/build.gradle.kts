plugins {
    id("com.android.library")
}

android {
    namespace = "org.pysh.janus.hook"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":core"))

    // libxposed Modern API
    compileOnly("io.github.libxposed:api:101.0.0")
}
