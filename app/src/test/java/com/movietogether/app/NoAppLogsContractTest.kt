package com.movietogether.app
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class NoAppLogsContractTest {
    private fun src(p: String) = listOf(File(p), File("../$p")).first { it.isFile }.readText()
    @Test fun settingsNoLongerContainsLogsPage() {
        assertFalse(src("app/src/main/java/com/movietogether/app/MainActivity.kt").contains("Screen.LOGS"))
    }
    @Test fun noPrivateCrashJournal() {
        assertTrue(src("app/src/main/java/com/movietogether/app/AppLog.kt").contains("Log.d("))
        assertFalse(src("app/src/main/java/com/movietogether/app/AppLog.kt").contains("appendText("))
        assertFalse(src("app/src/main/AndroidManifest.xml").contains("CrashJournalProvider"))
    }
}
