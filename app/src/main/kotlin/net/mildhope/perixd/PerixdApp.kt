package net.mildhope.perixd

import android.app.Application
import android.content.Context
import net.mildhope.perixd.data.Store
import net.mildhope.perixd.notify.Reminders
import net.mildhope.perixd.sync.Sync
import java.io.File

class PerixdApp : Application() {
    lateinit var store: Store

    override fun onCreate() {
        super.onCreate()
        store = Store(File(filesDir, "perixd.json"))
        Reminders.createChannels(this)
        Sync.init(this)
        store.onChange = { Reminders.sync(this) }
        store.onLocalChange = { Sync.request(this) }
    }
}

val Context.store get() = (applicationContext as PerixdApp).store
