buildscript {
    repositories {
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath(kotlin("gradle-plugin"))
    }
}
plugins {
    `maven-publish`
    id("com.google.devtools.ksp").version("1.6.10-1.0.4").apply(false)
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
