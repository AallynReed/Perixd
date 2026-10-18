package net.mildhope.perixd.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import net.mildhope.perixd.data.Store
import net.mildhope.perixd.notify.Reminders
import net.mildhope.perixd.store
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class SyncState(
    val server: String,
    val secret: String,
    val device: String,
    val seq: Long = 0,
    /** The stamp of each record as the server last had it from or for this device. */
    val uploaded: Map<String, Long> = emptyMap(),
    val fcm: FcmConfig? = null,
    val pushToken: String? = null,
    val lastSync: Long? = null,
    val lastError: String? = null,
) {
    val pairing get() = Pairing(server, Base64.getUrlDecoder().decode(secret))
    val keys get() = SyncKeys(pairing.secret)
}

@Serializable
private data class Sealed(val k: String, val v: JsonElement, val t: Long)

/** Optional end-to-end encrypted sync between a person's devices through a Perixd server. */
object Sync {
    const val DEFAULT_SERVER = "https://perixd.mildhope.net"
    private val json = Store.json
    private val worker = Executors.newSingleThreadExecutor()
    private val queued = AtomicBoolean(false)
    private val mutable = MutableStateFlow<SyncState?>(null)
    val state: StateFlow<SyncState?> = mutable
    private lateinit var file: File

    fun init(context: Context) {
        file = File(context.filesDir, "sync.json")
        mutable.value = runCatching { json.decodeFromString<SyncState>(file.readText()) }.getOrNull()
        state.value?.fcm?.let { PushSetup.start(context, it) }
    }

    val enabled get() = state.value != null

    /** Starts a new sync space with this device's data. Blocking. */
    fun create(context: Context, server: String) {
        val pairing = Pairing(server.trim().trimEnd('/'), SyncKeys.newSecret())
        val keys = SyncKeys(pairing.secret)
        Api(pairing.server, keys).openSpace()
        val now = System.currentTimeMillis()
        context.store.update(stamp = false) { d -> d.copy(stamps = Records.keys(d).filter { it !in d.stamps }.associateWith { now } + d.stamps) }
        begin(context, pairing)
    }

    /** Joins the space another device shows as a QR code or link. Blocking. */
    fun join(context: Context, pairing: Pairing) {
        Api(pairing.server, SyncKeys(pairing.secret)).openSpace()
        begin(context, pairing, joining = true)
    }

    private fun begin(context: Context, pairing: Pairing, joining: Boolean = false) {
        val fcm = runCatching { Api.config(pairing.server) }.getOrNull()
        save(SyncState(pairing.server, SyncKeys.b64(pairing.secret), UUID.randomUUID().toString(), fcm = fcm))
        run(context, joining)
        fcm?.let { PushSetup.start(context, it) }
    }

    /** Stops syncing on this device; with [wipe] the server forgets the space for every device. Blocking. */
    fun leave(context: Context, wipe: Boolean) {
        val s = state.value ?: return
        runCatching {
            val api = Api(s.server, s.keys)
            if (wipe) api.deleteSpace() else api.removeDevice(s.device)
        }
        PushSetup.stop(context)
        file.delete()
        mutable.value = null
        Reminders.sync(context)
    }

    /** Syncs soon on the background worker; calls made while one is waiting fold into it. */
    fun request(context: Context) {
        if (!enabled || !queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            runCatching { run(context) }
        }
    }

    /** Syncs now and waits at most [timeoutMs]; false if it didn't finish or failed. */
    fun syncNow(context: Context, timeoutMs: Long): Boolean {
        if (!enabled) return true
        return runCatching { worker.submit { run(context) }.get(timeoutMs, TimeUnit.MILLISECONDS) }.isSuccess
    }

    fun registerToken(context: Context, token: String) {
        worker.execute {
            val s = state.value ?: return@execute
            runCatching {
                Api(s.server, s.keys).registerDevice(s.device, token)
                save(s.copy(pushToken = token))
            }
        }
    }

    @Synchronized
    private fun run(context: Context, joining: Boolean = false) {
        var s = state.value ?: return
        val keys = s.keys
        val api = Api(s.server, keys)
        try {
            if (joining) s = pull(context, api, keys, s, winTies = true)
            val data = context.store.data.value
            val dirty = data.stamps.filter { (key, t) -> t > (s.uploaded[key] ?: -1) }
            for (chunk in dirty.entries.chunked(200)) {
                val records = chunk.map { (key, t) ->
                    val id = keys.recordId(key)
                    WireRecord(id, t, blob = keys.seal(id, json.encodeToString(Sealed(key, Records.value(data, key), t)).toByteArray()))
                }
                api.push(s.device, records)
                s = s.copy(uploaded = s.uploaded + chunk.associate { it.key to it.value })
            }
            s = pull(context, api, keys, s, winTies = false)
            save(s.copy(lastSync = System.currentTimeMillis(), lastError = null))
        } catch (e: Exception) {
            save(s.copy(lastError = e.message ?: e.javaClass.simpleName))
            throw e
        } finally {
            Reminders.sync(context)
        }
    }

    private fun pull(context: Context, api: Api, keys: SyncKeys, start: SyncState, winTies: Boolean): SyncState {
        var s = start
        do {
            val page = api.pull(s.seq)
            val remotes = page.records.mapNotNull { r ->
                runCatching { json.decodeFromString<Sealed>(keys.open(r.id, r.blob).decodeToString()) }.getOrNull()
                    ?.let { Remote(it.k, it.v, it.t) }
            }
            context.store.update(stamp = false) { Records.merge(it, remotes, winTies) }
            val stamps = context.store.data.value.stamps
            s = s.copy(
                seq = page.records.maxOfOrNull { it.seq } ?: page.seq,
                uploaded = s.uploaded + remotes.filter { stamps[it.key] == it.t }.associate { it.key to it.t },
            )
        } while (page.more)
        return s
    }

    private fun save(s: SyncState) {
        synchronized(this) {
            val tmp = File("${file.path}.tmp")
            tmp.writeText(json.encodeToString(s))
            tmp.renameTo(file)
            mutable.value = s
        }
    }
}
