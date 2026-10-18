package net.mildhope.perixd.sync

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import net.mildhope.perixd.notify.Reminders

/** Firebase is set up at runtime from the sync server's settings, so the app ships no Google keys and stays offline without sync. */
object PushSetup {
    fun start(context: Context, fcm: FcmConfig) {
        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder().setProjectId(fcm.projectId).setApplicationId(fcm.appId)
                        .setApiKey(fcm.apiKey).setGcmSenderId(fcm.senderId).build(),
                )
            }
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.token.addOnSuccessListener { Sync.registerToken(context.applicationContext, it) }
        }
    }

    fun stop(context: Context) {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseMessaging.getInstance().apply { isAutoInitEnabled = false }.deleteToken()
        }
    }
}

class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        Sync.syncNow(applicationContext, 15_000)
        Reminders.sync(applicationContext)
    }

    override fun onNewToken(token: String) {
        Sync.registerToken(applicationContext, token)
    }
}
