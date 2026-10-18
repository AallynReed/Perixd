package net.mildhope.perixd

import android.app.Application
import android.content.Context
import net.mildhope.perixd.data.Store
import net.mildhope.perixd.notify.Reminders
import java.io.File

class PerixdApp : Application() {
    lateinit var store: Store

    override fun onCreate() {
        super.onCreate()
        store = Store(File(filesDir, "perixd.json"))
        Reminders.createChannels(this)
        store.onChange = { Reminders.sync(this) }
    }
}

val Context.store get() = (applicationContext as PerixdApp).store
