// The app's one Room database: entities, DAOs, migrations and exported schemas (schemas/).
// Feature and core data modules build repositories on top of it.
plugins {
    id("chronova.android.library")
    id("chronova.android.room")
}

android {
    namespace = "noel.chronova.core.database"
}

dependencies {
    // DAOs and RoomDatabase (withTransaction) are part of this module's API.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
}
