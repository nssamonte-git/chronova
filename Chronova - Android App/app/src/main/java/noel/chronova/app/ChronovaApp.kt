package noel.chronova.app

import noel.chronova.feature.reminders.domain.Reminder
import noel.chronova.feature.push.domain.RegisterDevice
import noel.chronova.feature.push.data.OkHttpPushApi
import noel.chronova.feature.push.data.FirebasePushTokens
import kotlinx.coroutines.flow.distinctUntilChanged
import noel.chronova.feature.push.domain.HandleAlert
import noel.chronova.feature.push.domain.PushMessage
import noel.chronova.feature.push.data.PushHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import noel.chronova.core.data.reminders.PreferencesReminderSettings
import noel.chronova.core.domain.auth.AuthState
import noel.chronova.core.domain.reminders.ReminderSettings
import noel.chronova.feature.reminders.data.AlarmManagerReminderAlarm
import noel.chronova.feature.reminders.data.NotificationReminderPoster
import noel.chronova.feature.reminders.data.PreferencesReminderLog
import noel.chronova.feature.reminders.data.ReminderHost
import noel.chronova.feature.reminders.domain.ReminderEngine
import noel.chronova.app.di.ExportGraph
import android.app.Application
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import noel.chronova.app.auth.BackendConfig
import noel.chronova.app.di.AccountGraph
import noel.chronova.app.di.AssistantGraph
import noel.chronova.app.di.NotificationsGraph
import noel.chronova.app.di.ProjectsGraph
import noel.chronova.app.di.SyncGraph
import noel.chronova.app.di.TimelineGraph
import noel.chronova.core.data.connectivity.onlineFlow
import noel.chronova.core.data.appearance.PreferencesAppearanceRepository
import noel.chronova.core.data.repository.PreferencesFirstLaunchFlag
import noel.chronova.core.domain.appearance.AppearanceRepository
import noel.chronova.core.data.repository.RoomProjectRepository
import noel.chronova.core.database.ChronovaDatabase
import noel.chronova.core.domain.auth.AuthRepository
import noel.chronova.core.domain.notifications.NotificationKind
import noel.chronova.core.domain.notifications.NotificationRepository
import noel.chronova.core.domain.repository.ProjectRepository
import noel.chronova.core.network.defaultHttpClient
import noel.chronova.feature.account.data.ApiAuthRepository
import noel.chronova.feature.account.data.DisabledAuthRepository
import noel.chronova.feature.account.data.KeystoreTokenStore
import noel.chronova.feature.assistant.data.remote.OkHttpVoiceApi
import noel.chronova.feature.assistant.domain.VoiceApi
import noel.chronova.feature.notifications.data.RoomNotificationRepository
import noel.chronova.feature.notifications.presentation.SystemNotifications
import noel.chronova.feature.sync.data.RoomLocalSyncStore
import noel.chronova.feature.sync.data.SyncCoordinator
import noel.chronova.feature.sync.data.SyncEngine
import noel.chronova.feature.sync.data.keepOnlyFor
import noel.chronova.feature.sync.data.remote.OkHttpSyncApi

class ChronovaApp : Application(), ReminderHost, PushHost {
    val container: AppContainer by lazy { AppContainer(this) }

    override val reminderEngine: ReminderEngine get() = container.reminderEngine

    override fun onCreate() {
        super.onCreate()
        container.appScope.launch { container.projectRepository.seedSampleIfFirstLaunch() }
        container.appScope.launch { container.authRepository.refresh() }
        // The reminder alarm follows the open tasks, project names, the reminder choice and sign-in.
        container.appScope.launch {
            combine(
                container.projectRepository.observeOpenTasks(),
                container.projectRepository.observeProjects(),
                container.reminderSettings.lead,
                container.authRepository.state,
            ) { _, _, _, _ -> }.collectLatest {
                delay(RESCHEDULE_DEBOUNCE_MILLIS) // a burst of changes (a sync, an undo) reschedules once
                container.reminderEngine.reschedule()
            }
        }
        // The server can push to this device while signed in: registered at each start (time zone
        // included), on sign-in (another account too) and when the reminder choice changes.
        container.appScope.launch {
            combine(container.authRepository.state, container.reminderSettings.lead) { auth, lead ->
                (auth as? AuthState.SignedIn)?.userId to lead
            }.distinctUntilChanged().collectLatest { (userId, _) ->
                if (userId != null) container.registerDevice()
            }
        }
    }

    /** Firebase gave this device a new token: the server needs it to keep reaching the device. */
    override fun onPushToken(token: String) {
        container.appScope.launch { container.registerDevice(token) }
    }

    /** A push from the server (docs/push.md); one this app doesn't understand is ignored. */
    override fun onPushMessage(data: Map<String, String>) {
        val message = PushMessage.parse(data) ?: return
        container.appScope.launch { container.handlePush(message) }
    }

    private companion object {
        const val RESCHEDULE_DEBOUNCE_MILLIS = 500L
    }
}

/**
 * Manual dependency container; one instance per process. It holds what several features share
 * (database, HTTP, repositories, sign-in, sync, the voice API); each feature's use cases are wired
 * in its own graph under [noel.chronova.app.di].
 */
class AppContainer(context: Context) {
    /** Outlives screens; for work that must finish even if the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: ChronovaDatabase = ChronovaDatabase.create(context)

    val projectRepository: ProjectRepository = RoomProjectRepository(
        database,
        PreferencesFirstLaunchFlag(context.getSharedPreferences("chronova", Context.MODE_PRIVATE)),
    )

    /** When task reminders come; this device only, kept when signing out. */
    val reminderSettings: ReminderSettings = PreferencesReminderSettings(context.getSharedPreferences("chronova_reminders", Context.MODE_PRIVATE))

    /** This device's look (System / Light / Dark); its own preferences file, kept when signing out. */
    val appearance: AppearanceRepository = PreferencesAppearanceRepository(context.getSharedPreferences("chronova_appearance", Context.MODE_PRIVATE))

    val backendConfig: BackendConfig = BackendConfig.fromBuildConfig()

    private val httpClient = defaultHttpClient()

    /** What happened (sync, assistant, account), for the notifications page; important ones also reach the shade. */
    val notificationRepository: NotificationRepository = RoomNotificationRepository(
        database.notificationDao(),
        poster = SystemNotifications(context, openApp = { Intent(it, MainActivity::class.java) }),
    )

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

    // Each feature's wiring, built on the shared pieces above.
    val projects = ProjectsGraph(this)
    val timeline = TimelineGraph(this)
    val notifications = NotificationsGraph(this)
    val sync = SyncGraph(this)
    val account = AccountGraph(this)
    val assistant = AssistantGraph(this, context)
    val export = ExportGraph(this, context)

    /** Tells the server how to push to this device (Firebase token, time zone, reminder choice). */
    val registerDevice = RegisterDevice(
        auth = authRepository,
        api = OkHttpPushApi(backendConfig.apiUrl.ifBlank { "https://localhost" }, httpClient),
        tokens = FirebasePushTokens(),
        settings = reminderSettings,
    )

    private val handleAlert = HandleAlert(authRepository, notificationRepository)

    /** Acts on a push from the server (docs/push.md). */
    suspend fun handlePush(message: PushMessage) {
        when (message) {
            is PushMessage.Reminder -> reminderEngine.onPush(
                Reminder(message.taskId, message.projectId, message.projectName, message.title, message.isMilestone, message.startMillis, message.atMillis),
            )
            PushMessage.SyncAvailable, PushMessage.SignedIn, PushMessage.AccountChanged -> handleAlert(message)
        }
    }

    /** Posts task reminders from one alarm (see [ChronovaApp] for what reschedules it). */
    val reminderEngine = ReminderEngine(
        projects = projectRepository,
        settings = reminderSettings,
        signedIn = { authRepository.state.value is AuthState.SignedIn },
        alarm = AlarmManagerReminderAlarm(context.applicationContext),
        poster = NotificationReminderPoster(context.applicationContext, openProject = { c, projectId ->
            Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PROJECT, projectId)
        }),
        log = PreferencesReminderLog(context.getSharedPreferences("chronova_reminder_log", Context.MODE_PRIVATE)),
    )
}
