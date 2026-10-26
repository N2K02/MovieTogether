package com.movietogether.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomProfileTest {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("room_profile", Context.MODE_PRIVATE)

    @Before fun setup() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
    }

    @Test fun firstLaunchGeneratesAndPersistsValidName() {
        val name = RoomProfile.getOrCreate(context)
        assertTrue(name.matches(Regex("[A-Za-z]+[0-9]{2}")))
        assertTrue(name.length <= 30)
        assertEquals(name, prefs.getString("displayName", null))
    }

    @Test fun repeatedLaunchAndOtherScreenUseSameName() {
        val first = RoomProfile.getOrCreate(context)
        repeat(5) { assertEquals(first, RoomProfile.getOrCreate(context)) }
    }

    @Test fun preexistingCustomNameSurvivesUpgrade() {
        prefs.edit().putString("displayName", "ExistingPerson").commit()
        assertEquals("ExistingPerson", RoomProfile.getOrCreate(context))
    }

    @Test fun editedNamePersistsAcrossRestart() {
        RoomProfile.getOrCreate(context)
        assertEquals("NewName", RoomProfile.setName(context, " NewName "))
        assertEquals("NewName", RoomProfile.getOrCreate(context))
    }

    @Test fun blankNameCannotReplaceExistingName() {
        RoomProfile.setName(context, "Keeper")
        assertEquals("Keeper", RoomProfile.setName(context, "  "))
        assertEquals("Keeper", RoomProfile.getOrCreate(context))
    }

    @Test fun emptySavedNameIsReplacedWithGeneratedName() {
        prefs.edit().putString("displayName", "").commit()
        assertTrue(RoomProfile.getOrCreate(context).isNotBlank())
    }

    @Test fun clearingAppPreferencesCreatesAnotherValidProfile() {
        RoomProfile.setName(context, "OldProfile")
        prefs.edit().clear().commit()
        val generated = RoomProfile.getOrCreate(context)
        assertNotEquals("OldProfile", generated)
        assertEquals(generated, prefs.getString("displayName", null))
    }

    @Test fun nameLengthIsLimitedToProtocolMaximum() {
        assertEquals(30, RoomProfile.setName(context, "A".repeat(60)).length)
    }

    @Test fun sharedPreferencesNotifiesOtherOpenScreens() {
        RoomProfile.getOrCreate(context)
        var notified = false
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "displayName") notified = true
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        RoomProfile.setName(context, "ChangedElsewhere")
        assertTrue(notified)
        assertEquals("ChangedElsewhere", RoomProfile.getOrCreate(context))
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test fun generatorHasDeterministicBounds() {
        assertEquals("CosmicFox10", RoomProfile.generateName { 0 })
        assertEquals("CleverPenguin99", RoomProfile.generateName { it - 1 })
    }

    @Test fun noStaleActivityWritesAndHostingKeepsName() {
        val main = java.io.File("src/main/java/com/movietogether/app/MainActivity.kt").readText()
        val bubble = java.io.File("src/main/java/com/movietogether/app/ChatBubbleActivity.kt").readText()
        for (source in listOf(main, bubble)) {
            assertTrue(source.contains("registerOnSharedPreferenceChangeListener"))
            assertTrue(source.contains("unregisterOnSharedPreferenceChangeListener"))
            assertTrue(source.contains("RoomProfile.setName("))
            assertFalse(source.contains("LaunchedEffect(displayName)"))
        }
        assertFalse(main.contains("if (displayName == \"Guest\") displayName = \"Host\""))
    }
}
