plugins {
    id("groovy")
    kotlin("jvm")
}

repositories {
    google()
}
dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation("com.android.tools.build:gradle:7.1.2")
}