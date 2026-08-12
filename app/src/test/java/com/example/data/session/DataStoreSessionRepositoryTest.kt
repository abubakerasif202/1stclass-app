package com.example.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.domain.model.AuthenticatedDriver
import com.example.domain.model.DriverSession
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSessionRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val driver = AuthenticatedDriver(
        driverId = "DRV-8492",
        name = "James Miller",
        email = "james.miller@firstclassexpress.com.au",
        phone = null
    )

    /**
     * Runs [block] against a repository whose DataStore owns [file] exclusively, then tears it
     * down. DataStore allows only one live instance per file, so each "restart" needs its own
     * scope.
     */
    private suspend fun <T> withRepository(
        file: File,
        clock: () -> Long = { 1_000L },
        block: suspend (DataStoreSessionRepository) -> T
    ): T {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        return try {
            block(
                DataStoreSessionRepository(
                    PreferenceDataStoreFactory.create(scope = scope) { file },
                    clock
                )
            )
        } finally {
            scope.cancel()
        }
    }

    /** A path inside a fresh folder: the file must not exist, or DataStore's rename fails. */
    private fun sessionFile(name: String): File =
        File(temporaryFolder.newFolder(name), "session.preferences_pb")

    @Test
    fun startedSessionIsReadableImmediately() = runTest {
        withRepository(sessionFile("one"), clock = { 4_242L }) { repository ->
            val session = repository.startSession(driver).getOrThrow()

            assertEquals("DRV-8492", session.driverId)
            assertEquals(4_242L, session.authenticatedAt)
            assertEquals("James Miller", repository.currentSession()?.name)
        }
    }

    @Test
    fun sessionSurvivesARestartOfTheRepository() = runTest {
        val file = sessionFile("two")
        withRepository(file) { it.startSession(driver).getOrThrow() }

        val restored: DriverSession? = withRepository(file) { it.observeSession().first() }

        assertEquals("DRV-8492", restored?.driverId)
        assertEquals("james.miller@firstclassexpress.com.au", restored?.email)
    }

    /**
     * DataStore commits by renaming a temp file over the target. Windows' `File.renameTo` refuses
     * to replace an existing file, so on a Windows host only the very first write to a given file
     * can succeed. Android (and Linux CI) rename atomically over the existing file, so this runs
     * there and is skipped locally rather than being weakened to fit the weaker platform.
     */
    @Test
    fun clearingTheSessionSignsTheDriverOutAndSurvivesARestart() = runTest {
        assumeTrue(
            "Host filesystem cannot rename over an existing file",
            supportsRenameOverExistingFile()
        )

        val file = sessionFile("three")
        withRepository(file) { repository ->
            repository.startSession(driver).getOrThrow()
            repository.clearSession().getOrThrow()
            assertNull(repository.currentSession())
        }

        assertNull(withRepository(file) { it.currentSession() })
    }

    private fun supportsRenameOverExistingFile(): Boolean {
        val probeDir = temporaryFolder.newFolder("rename-probe")
        val source = File(probeDir, "source").apply { writeText("a") }
        val target = File(probeDir, "target").apply { writeText("b") }
        return source.renameTo(target)
    }

    @Test
    fun aDriverWithoutAPhoneNumberDoesNotGainOne() = runTest {
        withRepository(sessionFile("four")) { repository ->
            repository.startSession(driver).getOrThrow()

            assertNull(repository.currentSession()?.phone)
        }
    }

    @Test
    fun blankDriverIdIsRejected() = runTest {
        withRepository(sessionFile("five")) { repository ->
            val result = repository.startSession(driver.copy(driverId = "  "))

            assertTrue(result.isFailure)
            assertNull(repository.currentSession())
        }
    }
}
