package id.flufclip.player

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchHistoryPositionTest {

    private class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            removed.remove(key)
            return this
        }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            removed.remove(key)
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            removed.remove(key)
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            removed.remove(key)
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            removed.add(key)
            temp.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            data.clear()
            temp.clear()
            removed.clear()
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removed.forEach { data.remove(it) }
            data.putAll(temp)
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testRecordAndGetResumePosition() {
        val prefs = FakeSharedPreferences()
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        WatchHistory.recordPlaybackPosition(prefs, url, 42000L, 212000L)

        val resume = WatchHistory.getResumePosition(prefs, url)
        assertEquals(42000L, resume)
        assertEquals(42000L, prefs.getLong("resume_dQw4w9WgXcQ", -1L))
    }

    @Test
    fun testPositionNearEndResetsToZero() {
        val prefs = FakeSharedPreferences()
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        // Position within 5 seconds of duration resets to 0
        WatchHistory.recordPlaybackPosition(prefs, url, 58000L, 60000L)

        val resume = WatchHistory.getResumePosition(prefs, url)
        assertEquals(0L, resume)
    }

    @Test
    fun testFallbackToHistoryListWhenNoLivePosition() {
        val prefs = FakeSharedPreferences()
        val url = "https://www.youtube.com/watch?v=abc123xyz89"
        val fallback = listOf(
            WatchedVideo(
                url = url,
                title = "Test",
                uploader = "Tester",
                thumbnail = "",
                position = 15000L,
                duration = 60000L
            )
        )

        val resume = WatchHistory.getResumePosition(prefs, url, fallback)
        assertEquals(15000L, resume)
    }
}
