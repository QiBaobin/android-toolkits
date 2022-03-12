package bob.android.toolkits

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

class GradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.apply {
            it.plugin("com.android.library")
            it.plugin("kotlin-android")
        }
        val catalogs = project.extensions.getByType(VersionCatalogsExtension::class.java)
        val libs = catalogs.named("libs")

        project.dependencies.run {
            add("testImplementation", libs.findDependency("junit").get())
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test:${libs.findVersion("kotlin").get()}")
        }

        project.extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
            val jdkVersion: String = libs.findVersion("jdk").get().toString()
            val androidCompileSdkVersion: String =
                libs.findVersion("androidCompileSdk").get().toString()
            val androidMinSdkVersion: String = libs.findVersion("androidMinSdk").get().toString()
            val androidTargetSdkVersion: String =
                libs.findVersion("androidTargetSdk").get().toString()
            val composeVersion: String = libs.findVersion("compose").get().toString()

            it.run {
                compileSdk = androidCompileSdkVersion.toInt()
                defaultConfig {
                    minSdk = androidMinSdkVersion.toInt()
                    targetSdk = androidTargetSdkVersion.toInt()

                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                    resourceConfigurations.clear()
                    resourceConfigurations.add("en")
                }

                compileOptions {
                    targetCompatibility = JavaVersion.toVersion(jdkVersion)
                    sourceCompatibility = JavaVersion.toVersion(jdkVersion)
                }

                buildTypes {
                    getByName("release") {
                        it.isMinifyEnabled = false
                        it.proguardFiles(
                            getDefaultProguardFile("proguard-android.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
                publishing {
                    singleVariant("release")
                }

                resourcePrefix(
                    project.name.split("-").joinToString("") { it.take(1).toLowerCase() })

                composeOptions {
                    kotlinCompilerExtensionVersion = composeVersion
                }
            }
        }
    }
}
