package net.mildhope.perixd.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.mildhope.perixd.data.Store
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class FcmConfig(val projectId: String, val appId: String, val apiKey: String, val senderId: String)

@Serializable
data class WireRecord(val id: String, val t: Long, val seq: Long = 0, val blob: String)

@Serializable
data class Page(val seq: Long, val records: List<WireRecord>, val more: Boolean)

@Serializable
private data class Upload(val device: String, val records: List<WireRecord>)

@Serializable
private data class Pushed(val seq: Long, val changed: Int)

@Serializable
private data class DeviceToken(val token: String?)

@Serializable
private data class ServerConfig(val fcm: FcmConfig? = null)

class HttpException(val status: Int, message: String) : IOException("HTTP $status: $message")

/** The sync server's API; every call blocks, so call it off the main thread. */
class Api(private val server: String, private val keys: SyncKeys) {
    private val space = "$server/v1/spaces/${keys.spaceId}"

    /** Creates the space if it doesn't exist yet; true when it was created. */
    fun openSpace(): Boolean = request("PUT", space).first == 201

    fun pull(since: Long): Page = json.decodeFromString(request("GET", "$space/records?since=$since").second)

    fun push(device: String, records: List<WireRecord>): Long =
        json.decodeFromString<Pushed>(request("POST", "$space/records", json.encodeToString(Upload(device, records))).second).seq

    fun registerDevice(device: String, token: String?) {
        request("PUT", "$space/devices/$device", json.encodeToString(DeviceToken(token)))
    }

    fun removeDevice(device: String) {
        request("DELETE", "$space/devices/$device")
    }

    fun deleteSpace() {
        request("DELETE", space)
    }

    private fun request(method: String, url: String, body: String? = null): Pair<Int, String> = call(method, url, body, keys.auth)

    companion object {
        private val json = Store.json

        fun config(server: String): FcmConfig? = json.decodeFromString<ServerConfig>(call("GET", "$server/v1/config", null, null).second).fcm

        private fun call(method: String, url: String, body: String?, token: String?): Pair<Int, String> {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                val status = connection.responseCode
                val text = (if (status < 400) connection.inputStream else connection.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                if (status >= 400) throw HttpException(status, text.take(200))
                return status to text
            } finally {
                connection.disconnect()
            }
        }
    }
}
