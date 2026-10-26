package com.movietogether.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomProfileTest {
    @Test fun generatedNamesAreFriendlyAndBounded() {
        val name = RoomProfile.generateName { 0 }
        assertEquals("CosmicFox10", name)
        assertTrue(name.length <= 30)
    }

    @Test fun generatedNamesUseDifferentCombinations() {
        assertEquals("CleverPenguin99", RoomProfile.generateName { it - 1 })
        assertTrue(RoomProfile.generateName { 0 } != RoomProfile.generateName { it - 1 })
    }

    @Test fun bothEntryPointsUseSamePersistedProfile() {
        val main = java.io.File("src/main/java/com/movietogether/app/MainActivity.kt").readText()
        val bubble = java.io.File("src/main/java/com/movietogether/app/ChatBubbleActivity.kt").readText()
        assertTrue(main.contains("RoomProfile.getOrCreate(context)"))
        assertTrue(bubble.contains("RoomProfile.getOrCreate(this@ChatBubbleActivity)"))
        assertTrue(!main.contains("if (displayName == \"Guest\") displayName = \"Host\""))
        assertTrue(main.contains("profile.edit().putString(\"displayName\", displayName)"))
    }
}
