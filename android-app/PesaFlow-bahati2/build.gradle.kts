// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Plugin versions live in gradle/libs.versions.toml — bump there, all modules follow.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kover) apply false
}
