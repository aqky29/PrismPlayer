package id.flufclip.player

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SavedAccount(val id: String, val cookies: String, val profile: YouTubeUserProfile)

/** Account snapshots are encrypted at rest; the existing active-session preferences remain compatible. */
internal object AccountVault {
    private const val ALIAS = "flufplay_saved_accounts_v1"
    private const val PREFS = "saved_youtube_accounts"
    private const val DATA = "encrypted_accounts"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun read(context: Context): List<SavedAccount> {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, null) ?: return emptyList()
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            SavedAccount(item.getString("id"), item.getString("cookies"), YouTubeUserProfile(
                item.optString("name"), item.optString("handle"), item.optString("avatar").ifBlank { null }))
        }
    }

    fun write(context: Context, accounts: List<SavedAccount>) {
        val json = JSONArray()
        accounts.forEach { account -> json.put(JSONObject().put("id", account.id).put("cookies", account.cookies)
            .put("name", account.profile.name).put("handle", account.profile.emailOrHandle)
            .put("avatar", account.profile.avatarUrl.orEmpty())) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val value = Base64.encodeToString(cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DATA, value).commit())
    }
}
