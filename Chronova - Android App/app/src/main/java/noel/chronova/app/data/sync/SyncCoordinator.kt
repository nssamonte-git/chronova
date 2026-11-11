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
import noel.chronova.app.auth.AuthRepository
import noel.chronova.app.auth.AuthState
import noel.chronova.app.data.remote.SyncApi
import noel.chronova.app.data.remote.SyncApiException

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long, val pushed: Int, val pulled: Int) : SyncStatus
    data class Failed(val reason: SyncFailure) : SyncStatus
}

enum class SyncFailure { NOT_SIGNED_IN, SIGN_IN_AGAIN, OFFLINE, SERVER, BUSY }

/** "You're back online with unsynced changes — sync now?" */
data class SyncPrompt(val pendingChanges: Int, val signedIn: Boolean)

/** What the account screen needs from sync; implemented by [SyncCoordinator], faked in tests. */
interface AccountSync {
    val pendingCount: StateFlow<Int>
    val status: StateFlow<SyncStatus>
    fun lastSyncedAt(): Long?
    fun syncNow()
    suspend fun signOutAndClear()
    suspend fun deleteAccount(): SyncFailure?
}

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
        if (pending > 0) _prompt.value = SyncPrompt(pending, signedIn = state is AuthState.SignedIn)
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
                _status.value = statusOf(outcome)
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
            return failureOf(error.kind)
        }
        signOutAndClear()
        return null
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
