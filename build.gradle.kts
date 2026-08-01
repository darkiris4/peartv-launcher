// Top-level build file — plugin versions are declared here (via the version
// catalog) and applied per-module, never re-pinned at the module level.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
