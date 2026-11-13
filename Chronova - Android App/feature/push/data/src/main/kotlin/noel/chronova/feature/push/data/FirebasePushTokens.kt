package noel.chronova.feature.push.data

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import noel.chronova.feature.push.domain.PushTokens

/**
 * This device's Firebase token. Asking turns on Firebase's token handling (off until then, so a
 * signed-out app makes none); null when it can't be had (no Play services, offline).
 */
class FirebasePushTokens : PushTokens {
    override suspend fun current(): String? = suspendCancellableCoroutine { continuation ->
        val messaging = try {
            FirebaseMessaging.getInstance().also { it.isAutoInitEnabled = true }
        } catch (_: IllegalStateException) {
            null // Firebase isn't set up in this build
        }
        if (messaging == null) {
            continuation.resume(null)
        } else {
            messaging.token.addOnCompleteListener { task ->
                if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}
