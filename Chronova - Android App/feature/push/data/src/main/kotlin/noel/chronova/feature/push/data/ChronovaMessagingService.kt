package noel.chronova.feature.push.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Firebase's new tokens and the server's data messages, and hands them to the app
 * ([PushHost], the Application), which registers the token or acts on the message.
 */
class ChronovaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (applicationContext as? PushHost)?.onPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        (applicationContext as? PushHost)?.onPushMessage(message.data)
    }
}

/** The Application, which owns what a push needs (sign-in, the reminder poster, notifications). */
interface PushHost {
    fun onPushToken(token: String)

    /** Called on a background thread; Android gives a few seconds, so work is handed to the app's scope. */
    fun onPushMessage(data: Map<String, String>)
}
