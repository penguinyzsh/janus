plugins {
    // Kotlin JVM plugin (version already on classpath via AGP/android.builtInKotlin)
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // org.json is provided at runtime by Android (when consumed from :app/:hook),
    // but we need it on the compile classpath and in JVM tests.
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    testImplementation(libs.junit4)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
