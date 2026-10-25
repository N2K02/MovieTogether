package com.movietogether.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protect the mRadio-inspired Settings grouping while retaining app-specific pages. */
class SettingsConsistencyContractTest {
    @Test fun settingsRowsAndUpdatesFollowConsistentOrder() {
        val relative = "app/src/main/java/com/movietogether/app/MainActivity.kt"
        val source = listOf(File(relative), File("../$relative"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing $relative")
        val menu = source.substringAfter("} else if (!settingsPage) {", "")
        assertTrue("Settings section missing", menu.isNotBlank())
        assertTrue("Settings group title is missing", menu.contains("App information"))
        assertTrue("Settings menu item is missing", menu.contains("headlineContent = { Text(\"About & updates\") }"))
        assertTrue("Updates or diagnostic footer missing", menu.contains("headlineContent = { Text(\"About & updates\") }"))
        
    }
}
