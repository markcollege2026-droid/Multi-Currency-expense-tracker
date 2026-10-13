// Top-level build file - updated for Android 15 (API 35) compatibility and Kotlin 2.0
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

task("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
