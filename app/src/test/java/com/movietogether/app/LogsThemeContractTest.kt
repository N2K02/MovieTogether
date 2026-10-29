package com.movietogether.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression checks for themed and fully reachable MovieTogether diagnostics. */
class LogsThemeContractTest {
    private fun source(path: String): String = listOf(File(path), File("../$path"))
        .firstOrNull { it.isFile }?.readText() ?: error("Missing $path")

    @Test fun logsUsesExistingAppThemeHeaderAndSettingsNavigation() {
        val main = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        val logs = source("app/src/main/java/com/movietogether/app/LogsActivity.kt")
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue(main.contains("MovieTogetherTheme {"))
        assertTrue(main.contains("Screen.LOGS -> Icons.Rounded.BugReport"))
        assertTrue(main.contains("Screen.LOGS -> \"Logs\""))
        assertTrue(main.contains("page = Screen.LOGS"))
        assertTrue(main.contains("if (page == Screen.LOGS)"))
        assertTrue(main.contains("MovieTogetherLogsPage("))
        assertTrue(main.contains("onBack = { page = Screen.SETTINGS }"))
        assertTrue(main.contains("AppLog.init(applicationContext)"))
        assertFalse(main.contains("LogsActivity::class.java"))
        assertFalse(logs.contains("class LogsActivity"))
        assertFalse(logs.contains("MaterialTheme {"))
        assertFalse(logs.contains("Scaffold("))
        assertFalse(manifest.contains("com.movietogether.app.LogsActivity"))
    }

    @Test fun logsControlsAndActionsRemainAccessibleWithScrollAndInsets() {
        val main = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        val logs = source("app/src/main/java/com/movietogether/app/LogsActivity.kt")
        assertTrue(main.contains("Box(Modifier.fillMaxSize().padding(padding))"))
        assertTrue(main.contains("if (page == Screen.LOGS)"))
        assertTrue(logs.contains("maxHeight < 430.dp"))
        assertTrue(logs.contains("Modifier.verticalScroll(rememberScrollState())"))
        assertTrue(logs.contains("Modifier.weight(1f)"))
        assertTrue(logs.contains("trailingContent = {"))
        assertTrue(logs.contains("Switch(checked = enabled"))
        assertTrue(logs.contains("onClick = AppLog::clear"))
        assertTrue(logs.contains("onClick = onBack"))
        assertTrue(logs.contains("MaterialTheme.colorScheme.surfaceContainer"))
    }

    @Test fun backButtonAndMainBottomNavRemainReachable() {
        val main = source("app/src/main/java/com/movietogether/app/MainActivity.kt")
        assertTrue(main.contains("BackHandler(enabled = page == Screen.UPDATES || page == Screen.LOGS)"))
        assertTrue(main.contains("selected = page in listOf(Screen.SETTINGS, Screen.LOGS, Screen.UPDATES)"))
        assertTrue(main.contains("label = { Text(\"Quit\") }"))
    }
}
