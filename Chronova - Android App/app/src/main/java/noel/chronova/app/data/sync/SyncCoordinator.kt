package noel.chronova.app.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import noel.chronova.app.domain.auth.AuthRepository
import noel.chronova.app.domain.auth.AuthState
import noel.chronova.app.data.remote.SyncApi
import noel.chronova.app.data.remote.SyncApiException
import noel.chronova.app.domain.notifications.NotificationKind
import noel.chronova.app.domain.sync.SyncStatus
import noel.chronova.app.domain.sync.SyncFailure
import noel.chronova.app.domain.sync.SyncPrompt
import noel.chronova.app.domain.sync.AccountSync

/**
 * App-wide sync state. Sync never starts on its own: when the device comes back
 * online with unsynced changes, [prompt] asks the user, and only [syncNow] (from
 * the prompt or a button) runs it. Runs in the application scope, so a sync isn't
 * cut off by leaving a screen.
 */
class SyncCoordinator(
    private val scope: CoroutineScope,
    private val auth: AuthRepository,
    private val local: LocalSyncStore,
    private val api: SyncApi,
    online: Flow<Boolean>,
    private val runSync: suspend (userId: String) -> SyncOutcome,
    private val clock: () -> Long = System::currentTimeMillis,
    private val busyRetryDelayMillis: Long = 2_000,
    /** Records what happened for the notifications page. */
    private val notify: suspend (NotificationKind, Map<String, String>) -> Unit = { _, _ -> },
) : AccountSync {
    override val pendingCount: StateFlow<Int> = local.pendingCount().stateIn(scope, SharingStarted.Eagerly, 0)

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _prompt = MutableStateFlow<SyncPrompt?>(null)
    val prompt: StateFlow<SyncPrompt?> = _prompt.asStateFlow()

    private var job: Job? = null

    init {
        scope.launch {
            var previous: Boolean? = null
            online.collect { now ->
                // Only a real offline -> online transition counts, not the first reading.
                if (previous == false && now) onReconnected()
                previous = now
            }
        }
    }

    /** When the signed-in user last finished a sync on this device. */
    override fun lastSyncedAt(): Long? = (auth.state.value as? AuthState.SignedIn)?.let { local.lastSyncedAt(it.userId) }

    private suspend fun onReconnected() {
        val state = auth.state.value
        if (state == AuthState.NotConfigured || job?.isActive == true) return
        val pending = local.pendingCount().first()
        if (pending > 0) {
            _prompt.value = SyncPrompt(pending, signedIn = state is AuthState.SignedIn)
            notify(NotificationKind.SYNC_WAITING, mapOf("count" to "$pending"))
        }
    }

    /** "Later": the prompt comes back on the next reconnect, not before. */
    fun dismissPrompt() {
        _prompt.value = null
    }

    override fun syncNow() {
        _prompt.value = null
        if (job?.isActive == true) return
        val user = auth.state.value as? AuthState.SignedIn
        if (user == null) {
            _status.value = SyncStatus.Failed(SyncFailure.NOT_SIGNED_IN)
            return
        }
        job = scope.launch {
            _status.value = SyncStatus.Syncing
            var attempt = 0
            while (true) {
                val outcome = runSync(user.userId)
                // Another device of this user is syncing: wait and try again a few times.
                if (outcome is SyncOutcome.Failed && outcome.kind == SyncApiException.Kind.BUSY && attempt < BUSY_RETRIES) {
                    attempt++
                    delay(busyRetryDelayMillis * attempt)
                    continue
                }
                // The server ended the session: back to the sign-in page, data kept, and nothing
                // of this failure left for whoever signs in next.
                if (outcome is SyncOutcome.Failed && outcome.kind == SyncApiException.Kind.UNAUTHORIZED) {
                    auth.endSession()
                    _status.value = SyncStatus.Idle
                } else {
                    _status.value = statusOf(outcome)
                    record(outcome)
                }
                break
            }
        }
    }

    /** Signs out and removes this device's copy of the data (it stays on the server). */
    override suspend fun signOutAndClear() {
        job?.cancel()
        auth.signOut()
        local.clearAll()
        _status.value = SyncStatus.Idle
        _prompt.value = null
    }

    /** Deletes the account on the server, then signs out and clears this device. */
    override suspend fun deleteAccount(): SyncFailure? {
        val token = auth.accessToken() ?: return SyncFailure.SIGN_IN_AGAIN
        try {
            api.deleteAccount(token)
        } catch (error: SyncApiException) {
            if (error.kind == SyncApiException.Kind.UNAUTHORIZED) {
                auth.endSession()
                _status.value = SyncStatus.Idle
            }
            return failureOf(error.kind)
        }
        signOutAndClear()
        return null
    }

    private suspend fun record(outcome: SyncOutcome) {
        when (outcome) {
            is SyncOutcome.Synced -> {
                notify(NotificationKind.SYNCED, mapOf("sent" to "${outcome.pushed}", "received" to "${outcome.pulled}"))
                if (outcome.replaced > 0) notify(NotificationKind.SYNC_CONFLICT, mapOf("count" to "${outcome.replaced}"))
            }
            is SyncOutcome.Failed -> notify(NotificationKind.SYNC_FAILED, mapOf("reason" to failureOf(outcome.kind).name))
            SyncOutcome.NotSignedIn -> Unit
        }
    }

    private fun statusOf(outcome: SyncOutcome): SyncStatus = when (outcome) {
        is SyncOutcome.Synced -> SyncStatus.Synced(clock(), outcome.pushed, outcome.pulled)
        SyncOutcome.NotSignedIn -> SyncStatus.Failed(SyncFailure.SIGN_IN_AGAIN)
        is SyncOutcome.Failed -> SyncStatus.Failed(failureOf(outcome.kind))
    }

    private fun failureOf(kind: SyncApiException.Kind): SyncFailure = when (kind) {
        SyncApiException.Kind.UNAUTHORIZED -> SyncFailure.SIGN_IN_AGAIN
        SyncApiException.Kind.BUSY -> SyncFailure.BUSY
        SyncApiException.Kind.NETWORK -> SyncFailure.OFFLINE
        SyncApiException.Kind.REJECTED, SyncApiException.Kind.SERVER -> SyncFailure.SERVER
    }

    private companion object {
        const val BUSY_RETRIES = 3
    }
}
