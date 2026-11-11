package noel.chronova.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import noel.chronova.app.data.local.ChronovaDatabase
import noel.chronova.app.data.repository.FirstLaunchFlag
import noel.chronova.app.data.repository.RoomProjectRepository
import noel.chronova.app.domain.repository.ProjectRepository

class ChronovaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.appScope.launch { container.projectRepository.seedSampleIfFirstLaunch() }
    }
}

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context) {
    /** Outlives screens; for work that must finish even if the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: ChronovaDatabase = ChronovaDatabase.create(context)

    private val firstLaunch = object : FirstLaunchFlag {
        private val prefs = context.getSharedPreferences("chronova", Context.MODE_PRIVATE)
        override fun isFirst() = !prefs.getBoolean(KEY_SEEDED, false)
        override fun markDone() = prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    val projectRepository: ProjectRepository = RoomProjectRepository(database, firstLaunch)

    private companion object {
        const val KEY_SEEDED = "sample_seeded"
    }
}
