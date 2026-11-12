package noel.chronova.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import noel.chronova.app.auth.ApiAuthRepository
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.BackendConfig
import noel.chronova.app.auth.DisabledAuthRepository
import noel.chronova.app.auth.KeystoreTokenStore
import noel.chronova.app.data.local.ChronovaDatabase
import noel.chronova.app.data.local.RoomNotificationRepository
import noel.chronova.app.data.remote.OkHttpSyncApi
import noel.chronova.app.data.remote.OkHttpVoiceApi
import noel.chronova.app.data.remote.VoiceApi
import noel.chronova.app.data.repository.FirstLaunchFlag
import noel.chronova.app.data.repository.RoomProjectRepository
import noel.chronova.app.data.sync.RoomLocalSyncStore
import noel.chronova.app.data.sync.SyncCoordinator
import noel.chronova.app.data.sync.SyncEngine
import noel.chronova.app.data.sync.keepOnlyFor
import noel.chronova.app.data.sync.onlineFlow
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.notifications.NotificationRepository
import noel.chronova.app.domain.repository.ProjectRepository
import noel.chronova.app.notifications.SystemNotifications
import noel.chronova.app.presentation.voice.AssistantPhrases
import noel.chronova.app.presentation.voice.ResourcePhrases
import noel.chronova.app.voice.AndroidSpeechPlayer
import noel.chronova.app.voice.AndroidVoiceRecorder
import noel.chronova.app.voice.AssistantSettings
import noel.chronova.app.voice.PreferencesAssistantSettings
import noel.chronova.app.voice.SpeechPlayer
import noel.chronova.app.voice.VoiceRecorder
import okhttp3.OkHttpClient

class ChronovaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.appScope.launch { container.projectRepository.seedSampleIfFirstLaunch() }
        container.appScope.launch { container.authRepository.refresh() }
    }
}

/** Manual dependency container; one instance per process. */
class AppContainer(private val context: Context) {
    /** Outlives screens; for work that must finish even if the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: ChronovaDatabase = ChronovaDatabase.create(context)

    private val firstLaunch = object : FirstLaunchFlag {
        private val prefs = context.getSharedPreferences("chronova", Context.MODE_PRIVATE)
        override fun isFirst() = !prefs.getBoolean(KEY_SEEDED, false)
        override fun markDone() = prefs.edit { putBoolean(KEY_SEEDED, true) }
    }

    val projectRepository: ProjectRepository = RoomProjectRepository(database, firstLaunch)

    val backendConfig: BackendConfig = BackendConfig.fromBuildConfig()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** What happened (sync, assistant, account), for the notifications page; important ones also reach the shade. */
    val notificationRepository: NotificationRepository =
        RoomNotificationRepository(database.notificationDao(), poster = SystemNotifications(context))

    private val notify: suspend (NotificationKind, Map<String, String>) -> Unit = { kind, args -> notificationRepository.add(kind, args) }

    /** Server sign-in when this build has a server configured; otherwise offline-only. */
    val authRepository: AuthRepository =
        if (backendConfig.isComplete) {
            ApiAuthRepository(
                baseUrl = backendConfig.apiUrl,
                client = httpClient,
                store = KeystoreTokenStore(context.getSharedPreferences("chronova_auth", Context.MODE_PRIVATE)),
                // Another account's data, left when its session ended, never shows to the next one.
                beforeSignIn = { userId -> syncStore.keepOnlyFor(userId) },
                notify = notify,
            )
        } else {
            DisabledAuthRepository()
        }

    private val syncStore = RoomLocalSyncStore(database, context.getSharedPreferences("chronova_sync", Context.MODE_PRIVATE))

    // A build without a backend never calls it (auth stays NotConfigured), but the URL must still parse.
    private val syncApi = OkHttpSyncApi(backendConfig.apiUrl.ifBlank { "https://localhost" }, httpClient)

    /** Voice/typed commands; the server holds the provider keys. Model calls can take a while. */
    val voiceApi: VoiceApi = OkHttpVoiceApi(
        backendConfig.apiUrl.ifBlank { "https://localhost" },
        httpClient.newBuilder().readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build(),
    )

    /** A microphone recorder for one assistant (one recording at a time). */
    fun newVoiceRecorder(): VoiceRecorder = AndroidVoiceRecorder(context)

    /** A player for one assistant's spoken answers. */
    fun newSpeechPlayer(): SpeechPlayer = AndroidSpeechPlayer(context)

    val assistantSettings: AssistantSettings = PreferencesAssistantSettings(context.getSharedPreferences("chronova_voice", Context.MODE_PRIVATE))

    /** The assistant's own words (spoken problems, goodbyes), from string resources. */
    val assistantPhrases: AssistantPhrases = ResourcePhrases(context.resources)

    /** Whether the internet is reachable (each collector gets its own callback). */
    val online: Flow<Boolean> = onlineFlow(context)

    private val syncEngine = SyncEngine(syncStore, syncApi, accessToken = { authRepository.accessToken() })

    val syncCoordinator = SyncCoordinator(
        scope = appScope,
        auth = authRepository,
        local = syncStore,
        api = syncApi,
        online = online,
        runSync = syncEngine::sync,
        notify = notify,
    )

    private companion object {
        const val KEY_SEEDED = "sample_seeded"
    }
}
