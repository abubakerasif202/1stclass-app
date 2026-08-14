package au.com.firstclassexpress.driver.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The upgrade must not cost a driver queued work.
 *
 * An operation sitting in the queue when the app updates keeps its id — which is its idempotency
 * key — so the TMS can still deduplicate the replay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncQueueMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var databaseDirectory: File

    @Before
    fun setUp() {
        databaseDirectory = File(context.cacheDir, "sync-migration").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        databaseDirectory.deleteRecursively()
    }

    @Test
    fun `queued operations survive the version three to four upgrade`() {
        val database = openVersionThree()
        database.execSQL(
            "INSERT INTO sync_operations VALUES " +
                "('op-1','EVIDENCE','ev-1','UPSERT','{\"jobId\":\"job-1\"}',1700,2,'timeout','PENDING')"
        )

        MIGRATION_3_4.migrate(database)

        assertEquals(1, count(database, "SELECT COUNT(*) FROM sync_operations"))
        assertEquals(
            "The idempotency key must be untouched",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE id = 'op-1'")
        )
        assertEquals(
            "Existing work stays queued rather than being failed or dropped",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE status = 'PENDING'")
        )
        assertEquals(
            "Attempts already made are preserved",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE retryCount = 2")
        )
        assertEquals(
            "Rows written by an older app version read as payload version 1",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE payloadVersion = 1")
        )
        assertEquals(
            "A never-claimed row is immediately eligible for the stale sweep",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE updatedAt = 0")
        )
        assertEquals(
            "The original event timestamp is preserved",
            1,
            count(database, "SELECT COUNT(*) FROM sync_operations WHERE createdAt = 1700")
        )
        database.close()
    }

    private fun openVersionThree(): SupportSQLiteDatabase {
        val file = File(databaseDirectory, "legacy-v3.db")
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE `sync_operations` (
                                `id` TEXT NOT NULL,
                                `entityType` TEXT NOT NULL,
                                `entityId` TEXT NOT NULL,
                                `operationType` TEXT NOT NULL,
                                `payloadJson` TEXT NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `retryCount` INTEGER NOT NULL,
                                `lastError` TEXT,
                                `status` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        ).writableDatabase
    }

    private fun count(db: SupportSQLiteDatabase, query: String): Int =
        db.query(query).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
