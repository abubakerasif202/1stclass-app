package au.com.firstclassexpress.driver.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.com.firstclassexpress.driver.data.local.entity.EvidenceEntity
import au.com.firstclassexpress.driver.data.local.entity.InspectionItemEntity
import au.com.firstclassexpress.driver.data.local.entity.ShiftEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun shiftDraftPersists() = runTest {
        val dao = database.shiftDao()
        dao.insert(ShiftEntity("s1", "d1", "TRK-01", null, 1000L, null, "PRESTART_REQUIRED", 10L, null, null))
        assertEquals("PRESTART_REQUIRED", dao.getById("s1")!!.phase)
    }

    @Test
    fun inspectionAnswerRemainsUnansweredUntilExplicitlyChanged() = runTest {
        val dao = database.inspectionDao()
        dao.insertItem(InspectionItemEntity("i1", "insp1", "s1", "tyres", "Tyres", "Exterior", true, "UNANSWERED", null, null))
        assertEquals("UNANSWERED", dao.getItem("i1")!!.status)
    }

    @Test
    fun evidenceSavedLocalStatePersists() = runTest {
        val dao = database.evidenceDao()
        dao.insert(EvidenceEntity("e1", "j1", "PICKUP_PHOTO", "prototype://e1", "SAVED_LOCAL", 20L))
        assertEquals("SAVED_LOCAL", dao.getById("e1")!!.status)
    }

    @Test
    fun syncOperationKeepsOriginalTimestamp() = runTest {
        val dao = database.syncOperationDao()
        dao.insert(SyncOperationEntity("o1", "JOB", "j1", "STATUS_CHANGE", "{}", 1234L, 0, null, "PENDING"))
        assertEquals(1234L, dao.getById("o1")!!.createdAt)
    }
}
