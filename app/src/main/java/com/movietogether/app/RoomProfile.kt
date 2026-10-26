package com.movietogether.app

import android.content.Context
import java.security.SecureRandom

/** One stable, locally stored nickname shared by the main screen and chat bubble. */
internal object RoomProfile {
    private const val PREFS = "room_profile"
    private const val KEY = "displayName"
    private val adjectives = listOf("Cosmic", "Happy", "Blue", "Sunny", "Brave", "Silver", "Mellow", "Swift", "Lucky", "Clever")
    private val animals = listOf("Fox", "Panda", "Tiger", "Otter", "Koala", "Falcon", "Rabbit", "Wolf", "Dolphin", "Penguin")
    private val random = SecureRandom()

    internal fun generateName(nextInt: (Int) -> Int = random::nextInt): String =
        adjectives[nextInt(adjectives.size)] + animals[nextInt(animals.size)] +
            (10 + nextInt(90)).toString()

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(this) {
            val saved = prefs.getString(KEY, null)
            if (!saved.isNullOrBlank()) return saved
            return generateName().also { prefs.edit().putString(KEY, it).commit() }
        }
    }
}
