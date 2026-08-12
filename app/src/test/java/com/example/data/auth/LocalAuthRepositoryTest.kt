package com.example.data.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.model.AuthFailure
import com.example.model.Driver
import com.example.model.ShiftStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalAuthRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalAuthRepository

    private val testDriver = Driver(
        id = "DRV-8492",
        name = "James Miller",
        email = "james.miller@firstclassexpress.com.au",
        shiftStatus = ShiftStatus.OFF_DUTY
    )

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Fewer iterations keeps the suite fast; the algorithm under test is unchanged.
        val hasher = PinHasher(iterations = 1_000)
        DevelopmentDriverProvisioner(database.driverCredentialDao(), hasher)
            .provision(testDriver, "1234")
            .getOrThrow()
        repository = LocalAuthRepository(database.driverCredentialDao(), hasher)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun correctDevelopmentCredentialsSucceed() = runTest {
        val result = repository.authenticate("DRV-8492", "1234")

        assertTrue(result.isSuccess)
        assertEquals("DRV-8492", result.getOrThrow().driverId)
        assertEquals("James Miller", result.getOrThrow().name)
    }

    @Test
    fun driverIdIsCaseInsensitiveAndTrimmed() = runTest {
        assertTrue(repository.authenticate("  drv-8492  ", "1234").isSuccess)
        assertTrue(repository.authenticate("DrV-8492", "1234").isSuccess)
    }

    @Test
    fun emailAlsoWorksAsALogin() = runTest {
        assertTrue(
            repository.authenticate("JAMES.MILLER@firstclassexpress.com.au", "1234").isSuccess
        )
    }

    @Test
    fun incorrectPinFails() = runTest {
        val result = repository.authenticate("DRV-8492", "9999")

        assertTrue(result.isFailure)
        assertEquals(
            AuthFailure.InvalidCredentials.message,
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun unknownDriverFailsWithTheSameMessageAsAWrongPin() = runTest {
        val unknown = repository.authenticate("DRV-0000", "1234")
        val wrongPin = repository.authenticate("DRV-8492", "0000")

        assertTrue(unknown.isFailure)
        assertEquals(unknown.exceptionOrNull()?.message, wrongPin.exceptionOrNull()?.message)
    }

    @Test
    fun blankFieldsFail() = runTest {
        assertTrue(repository.authenticate("", "1234").isFailure)
        assertTrue(repository.authenticate("DRV-8492", "").isFailure)
        assertTrue(repository.authenticate("   ", "   ").isFailure)
    }

    @Test
    fun pinIsNeverStoredInPlainText() = runTest {
        val credential = database.driverCredentialDao().findByDriverId("DRV-8492")!!

        assertFalse(credential.pinHash.contains("1234"))
        assertFalse(credential.pinSalt.contains("1234"))
        assertTrue(credential.pinHash.length >= 32)
    }

    @Test
    fun reprovisioningDoesNotRotateAWorkingCredential() = runTest {
        val before = database.driverCredentialDao().findByDriverId("DRV-8492")!!

        DevelopmentDriverProvisioner(database.driverCredentialDao(), PinHasher(iterations = 1_000))
            .provision(testDriver, "1234")
            .getOrThrow()

        val after = database.driverCredentialDao().findByDriverId("DRV-8492")!!
        assertEquals(before.pinHash, after.pinHash)
    }

    @Test
    fun releaseBuildsProvisionNoTestAccount() = runTest {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        val provisioned = DevelopmentDriverProvisioner(emptyDb.driverCredentialDao())
            .provision(testDriver, pin = "")
            .getOrThrow()

        assertFalse(provisioned)
        assertEquals(0, emptyDb.driverCredentialDao().count())
        emptyDb.close()
    }
}
