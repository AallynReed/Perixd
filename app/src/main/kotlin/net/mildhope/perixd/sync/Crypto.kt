package net.mildhope.perixd.sync

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keys derived from the 32-byte secret the paired devices share. The server only ever sees the space id,
 * a hash of the auth token, opaque record ids and AES-GCM ciphertext.
 */
class SyncKeys(secret: ByteArray) {
    val spaceId: String = hkdf(secret, "perixd space", 16).joinToString("") { "%02x".format(it) }
    val auth: String = b64(hkdf(secret, "perixd auth", 32))
    private val data = SecretKeySpec(hkdf(secret, "perixd data", 32), "AES")
    private val ids = hkdf(secret, "perixd ids", 32)

    fun recordId(key: String): String = b64(hmac(ids, key.toByteArray()).copyOf(16))

    fun seal(id: String, plain: ByteArray): String {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, data, GCMParameterSpec(128, nonce))
        cipher.updateAAD(id.toByteArray())
        return b64(nonce + cipher.doFinal(plain))
    }

    fun open(id: String, blob: String): ByteArray {
        val bytes = Base64.getUrlDecoder().decode(blob)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, data, GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(id.toByteArray())
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }

    companion object {
        private val random = SecureRandom()

        fun newSecret() = ByteArray(32).also { random.nextBytes(it) }

        fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)

        /** HKDF-SHA256 with an empty salt, as in RFC 5869. */
        fun hkdf(secret: ByteArray, info: String, length: Int): ByteArray {
            val prk = hmac(ByteArray(32), secret)
            var block = ByteArray(0)
            val out = mutableListOf<Byte>()
            var counter = 1
            while (out.size < length) {
                block = hmac(prk, block + info.toByteArray() + counter.toByte())
                out += block.toList()
                counter++
            }
            return out.take(length).toByteArray()
        }
    }
}

/** What a QR code or pairing link carries: the server and the shared secret. */
data class Pairing(val server: String, val secret: ByteArray) {
    fun link(): String = "perixd://pair?u=${java.net.URLEncoder.encode(server, "UTF-8")}&k=${SyncKeys.b64(secret)}"

    companion object {
        fun parse(text: String): Pairing? = runCatching {
            val uri = java.net.URI(text.trim())
            if (uri.scheme != "perixd" || uri.host != "pair") return null
            val query = uri.rawQuery.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            val secret = Base64.getUrlDecoder().decode(query.getValue("k"))
            val server = query.getValue("u").trimEnd('/')
            if (secret.size != 32 || !(server.startsWith("https://") || server.startsWith("http://"))) null else Pairing(server, secret)
        }.getOrNull()
    }

    override fun equals(other: Any?) = other is Pairing && other.server == server && other.secret.contentEquals(secret)
    override fun hashCode() = server.hashCode() * 31 + secret.contentHashCode()
}
