package com.movietogether.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings layout must retain the mRadio-style grouping and updates submenu. */
class SettingsConsistencyContractTest {
    @Test fun settingsRowsAndUpdatesFollowConsistentOrder() {
        val relative = "app/src/main/java/com/movietogether/app/MainActivity.kt"
        val source = listOf(File(relative), File("../$relative"))
            .firstOrNull { it.isFile }?.readText() ?: error("Missing $relative")
        val menu = source.substringAfter("                Screen.SETTINGS -> {", "")
            .substringBefore("                Screen.UPDATES -> {")
        assertTrue("Settings section missing", menu.isNotBlank())
        assertTrue("Settings group title missing", menu.contains("\"App information\""))
        assertTrue("Hosting diagnostics missing", menu.contains("headlineContent = { Text(\"Hosting and connectivity\") }"))
        assertTrue("Updates item missing", menu.contains("headlineContent = { Text(\"About & updates\") }"))
        assertTrue("Divider before updates missing", menu.contains("HorizontalDivider("))
        assertTrue(
            "Divider must precede updates item",
            menu.indexOf("HorizontalDivider(") < menu.indexOf("headlineContent = { Text(\"About & updates\") }"),
        )
    }
}
