package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.RoomEvidenceRepository
import com.example.domain.evidence.StoredEvidenceFile
import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceStatus
import com.example.domain.model.EvidenceType
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EvidencePersistenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun savedEvidenceSurvivesDatabaseRecreation() = runTest {
        val databaseFile = File(temporaryFolder.newFolder("db"), "evidence-test.db")

        val first = openDatabase(databaseFile)
        val evidenceId = RoomEvidenceRepository(first, clock = { 500L }).let { repository ->
            val id = repository.createPending(
                EvidenceCaptureRequest(
                    jobId = "job-1",
                    type = EvidenceType.DELIVERY_SIGNATURE,
                    driverId = "DRV-8492",
                    shiftId = "shift-1"
                )
            ).getOrThrow()
            repository.markSavedLocal(
                id = id,
                file = StoredEvidenceFile("file:///evidence/sig.png", 512L, 600L),
                signerName = "Jane Receiver"
            ).getOrThrow()
            id
        }
        first.close()

        val reopened = openDatabase(databaseFile)
        val record = RoomEvidenceRepository(reopened).getById(evidenceId)

        assertNotNull(record)
        assertEquals(EvidenceStatus.SAVED_LOCAL, record!!.status)
        assertEquals("file:///evidence/sig.png", record.localUri)
        assertEquals("Jane Receiver", record.signerName)
        assertEquals("DRV-8492", record.driverId)
        assertEquals("shift-1", record.shiftId)
        assertEquals(512L, record.fileSizeBytes)
        assertEquals(600L, record.savedAt)
        reopened.close()
    }

    @Test
    fun pendingEvidenceCannotBeSavedWithoutAFile() = runTest {
        val databaseFile = File(temporaryFolder.newFolder("db-pending"), "pending.db")
        val database = openDatabase(databaseFile)
        val repository = RoomEvidenceRepository(database)

        val id = repository.createPending(
            EvidenceCaptureRequest("job-1", EvidenceType.PICKUP_PHOTO, "DRV-8492")
        ).getOrThrow()

        val emptyFile = repository.markSavedLocal(
            id = id,
            file = StoredEvidenceFile("file:///evidence/a.jpg", sizeBytes = 0L, savedAt = 1L)
        )

        assertTrue(emptyFile.isFailure)
        assertEquals(EvidenceStatus.PENDING_CAPTURE, repository.getById(id)!!.status)
        database.close()
    }

    @Test
    fun savedEvidenceCannotBeOverwrittenByASecondSave() = runTest {
        val databaseFile = File(temporaryFolder.newFolder("db-once"), "once.db")
        val database = openDatabase(databaseFile)
        val repository = RoomEvidenceRepository(database)

        val id = repository.createPending(
            EvidenceCaptureRequest("job-1", EvidenceType.PICKUP_PHOTO, "DRV-8492")
        ).getOrThrow()
        repository.markSavedLocal(id, StoredEvidenceFile("file:///a.jpg", 10L, 1L)).getOrThrow()

        val second = repository.markSavedLocal(id, StoredEvidenceFile("file:///b.jpg", 10L, 2L))

        assertTrue(second.isFailure)
        assertEquals("file:///a.jpg", repository.getById(id)!!.localUri)
        database.close()
    }

    /**
     * Verifies the 1 → 3 migration statements against a hand-built v1 schema: the new tables and
     * columns appear, and the rows that were already there are still there afterwards.
     */
    @Test
    fun migrationOneToThreeIsAdditive() {
        val file = File(temporaryFolder.newFolder("db-migration"), "legacy.db")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = createVersionOneSchema(db)
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        val database = helper.writableDatabase
        database.execSQL(
            "INSERT INTO evidence VALUES ('e1','job-1','PICKUP_PHOTO','file:///a.jpg'," +
                "'SAVED_LOCAL',10)"
        )
        database.execSQL("INSERT INTO drivers VALUES ('DRV-8492','James Miller','j@example.com')")

        MIGRATION_1_2.migrate(database)
        MIGRATION_2_3.migrate(database)

        assertEquals(1, countOf(database, "SELECT COUNT(*) FROM evidence"))
        assertEquals(0, countOf(database, "SELECT COUNT(*) FROM driver_credentials"))
        assertEquals(0, countOf(database, "SELECT COUNT(*) FROM freight_exceptions"))
        assertEquals(0, countOf(database, "SELECT COUNT(*) FROM location_points"))
        assertEquals(
            1,
            countOf(database, "SELECT COUNT(*) FROM evidence WHERE savedAt IS NULL")
        )
        assertEquals(1, countOf(database, "SELECT COUNT(*) FROM evidence WHERE latitude IS NULL"))
        assertEquals(
            1,
            countOf(database, "SELECT COUNT(*) FROM drivers WHERE phone IS NULL")
        )
        database.close()
    }

    private fun openDatabase(file: File) = Room
        .databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
        .addMigrations(*ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    private fun countOf(database: SupportSQLiteDatabase, query: String): Int =
        database.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun createVersionOneSchema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `drivers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`email` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE `evidence` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `localUri` TEXT, `status` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
