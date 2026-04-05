plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "org.pysh.janus"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.pysh.janus"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.moduleVersionCode.get().toInt()
        versionName = libs.versions.moduleVersion.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Project modules
    implementation(project(":core"))
    implementation(project(":hook"))
    implementation(project(":hook-api"))

    // MIUIX UI
    implementation(libs.miuix.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.navigation3)

    // Drag-and-drop reorderable LazyColumn
    implementation(libs.reorderable)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)

    // Navigation3
    implementation(libs.androidx.navigation3.runtime)

    // Navigation Event (required by MIUIX SearchBar's NavigationBackHandler)
    implementation(libs.androidx.navigationevent.compose)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // libxposed Modern API (compile-only; still referenced from hook code
    // currently living in :app — will be removed at Step 4 after migration)
    compileOnly(libs.libxposed.api)

    // libxposed service — used by JanusApplication (service binding)
    // and WhitelistManager (dynamic scope requests)
    implementation(libs.libxposed.service)

    // Unit testing (Robolectric)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)

    // Compose UI testing (instrumented)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// ADB-based device tests (require connected device with module activated)
tasks.register<Exec>("testE2E") {
    group = "verification"
    description = "Run ADB-based E2E tests (module loading, hook status, stability)"
    commandLine("bash", "${rootProject.projectDir}/_scripts/test_e2e.sh", "--no-build")
}

tasks.register<Exec>("testBehavior") {
    group = "verification"
    description = "Run ADB-based behavioral correctness tests"
    commandLine("bash", "${rootProject.projectDir}/_scripts/test_behavior.sh")
}

// — Code quality —

ktlint {
    android = true
    verbose = true
    outputToConsole = true
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.SARIF)
    }
    filter {
        exclude("**/generated/**")
    }
}

detekt {
    config.setFrom("${rootProject.projectDir}/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
    // Uncomment after first run to generate baseline:
    // baseline = file("detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}
