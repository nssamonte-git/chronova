// The app's one Room database: entities, DAOs, migrations and exported schemas (schemas/).
// Feature and core data modules build repositories on top of it.
plugins {
    id("chronova.android.library")
    id("chronova.android.room")
}

android {
    namespace = "noel.chronova.core.database"
    // MigrationTest builds each old database from the schema Room exported for it.
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
}

dependencies {
    // DAOs and RoomDatabase (withTransaction) are part of this module's API.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
