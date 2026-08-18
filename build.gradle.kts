// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9.1 bundles Kotlin 2.2.10; the project uses Kotlin 2.3.10, so keep the
// compiler, Compose plugin, and KSP plugin on one compatible toolchain.
buildscript {
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.10")
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
