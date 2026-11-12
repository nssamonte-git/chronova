// Sync use cases and the SyncService port (the account's sync plus the reconnect prompt). Pure Kotlin.
plugins {
    id("chronova.jvm.library")
}

dependencies {
    api(projects.core.domain)
}
