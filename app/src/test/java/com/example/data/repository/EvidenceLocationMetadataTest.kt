package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.evidence.StoredEvidenceFile
import com.example.domain.model.EvidenceCaptureRequest
import com.example.domain.model.EvidenceType
import com.example.domain.model.LocationPoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EvidenceLocationMetadataTest {
    private lateinit var db: AppDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun recentRealLocationAttachesToEvidence() = runTest {
        RoomLocationRepository(db).save(LocationPoint("p", "d", "s", null, -33.8, 151.2, 6f, null, null, null, 900L, 900L)).getOrThrow()
        val repository = RoomEvidenceRepository(db, clock = { 1_000L }, idGenerator = { "id-${System.nanoTime()}" })
        val id = repository.createPending(EvidenceCaptureRequest("j", EvidenceType.PICKUP_PHOTO, "d", "s")).getOrThrow()
        val saved = repository.markSavedLocal(id, StoredEvidenceFile("file:///photo.jpg", 10L, 1_000L)).getOrThrow()
        assertEquals(-33.8, saved.latitude!!, 0.0)
        assertEquals(6f, saved.locationAccuracyMeters)
    }

    @Test fun evidenceStillCompletesWithoutGpsAndCoordinatesStayNull() = runTest {
        val repository = RoomEvidenceRepository(db, clock = { 1_000L }, idGenerator = { "id-${System.nanoTime()}" })
        val id = repository.createPending(EvidenceCaptureRequest("j", EvidenceType.DELIVERY_PHOTO, "d", "s")).getOrThrow()
        val saved = repository.markSavedLocal(id, StoredEvidenceFile("file:///photo.jpg", 10L, 1_000L)).getOrThrow()
        assertNull(saved.latitude)
        assertNull(saved.longitude)
    }
}
