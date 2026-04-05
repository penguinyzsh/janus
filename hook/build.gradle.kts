import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.library)
}

val catalogModuleVersion: String = libs.versions.moduleVersion.get()
val catalogModuleVersionCode: String = libs.versions.moduleVersionCode.get()

abstract class GenerateModulePropTask : DefaultTask() {
    @get:Input
    abstract val moduleVersion: Property<String>

    @get:Input
    abstract val moduleVersionCode: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val metaInfDir = outputDir.get().asFile.resolve("META-INF/xposed")
        metaInfDir.mkdirs()
        metaInfDir.resolve("module.prop").writeText(
            """
            |id=org.pysh.janus
            |name=Janus
            |version=${moduleVersion.get()}
            |versionCode=${moduleVersionCode.get()}
            |author=penguinyzsh
            |description=LSPosed module for Xiaomi rear screen enhancement. Hooks com.xiaomi.subscreencenter to bypass system restrictions.
            |minApiVersion=101
            |targetApiVersion=101
            |staticScope=true
            |
            """.trimMargin(),
        )
    }
}

android {
    namespace = "org.pysh.janus.hook"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "MODULE_VERSION",
            "\"$catalogModuleVersion\"",
        )
        buildConfigField(
            "int",
            "MODULE_VERSION_CODE",
            catalogModuleVersionCode,
        )
    }

    buildFeatures {
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
}

// Wire the generator into each variant via the AGP Variant API.
extensions.configure<LibraryAndroidComponentsExtension>("androidComponents") {
    onVariants { variant ->
        val generateTask =
            tasks.register<GenerateModulePropTask>(
                "generate${variant.name.replaceFirstChar { it.uppercase() }}ModuleProp",
            ) {
                moduleVersion.set(catalogModuleVersion)
                moduleVersionCode.set(catalogModuleVersionCode)
                outputDir.set(
                    layout.buildDirectory.dir("generated/xposed/${variant.name}"),
                )
            }

        variant.sources.resources?.addGeneratedSourceDirectory(
            generateTask,
            GenerateModulePropTask::outputDir,
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":hook-api"))

    // libxposed Modern API (provided by the host framework at runtime)
    compileOnly(libs.libxposed.api)
}
