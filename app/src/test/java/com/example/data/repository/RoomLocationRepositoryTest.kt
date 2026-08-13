package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.domain.model.LocationPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomLocationRepositoryTest {
    private lateinit var db: AppDatabase
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() = db.close()

    @Test fun locationAndSyncOperationArePersistedAtomically() = runTest {
        val repository = RoomLocationRepository(db, clock = { 200L }, idGenerator = { "sync1" })
        val point = LocationPoint("point1", "driver1", "shift1", null, -33.8, 151.2, 7.5f, null, null, null, 100L, 110L)
        repository.save(point).getOrThrow()
        val stored = db.locationPointDao().getById("point1")
        assertNotNull(stored)
        assertEquals("driver1", stored!!.driverId)
        assertEquals("shift1", stored.shiftId)
        assertEquals(100L, stored.recordedAt)
        assertEquals(7.5f, stored.accuracyMeters)
        val operation = db.syncOperationDao().getById("sync1")!!
        assertEquals("LOCATION_POINT_CREATED", operation.operationType)
        assertEquals("PENDING", operation.status)
        assertEquals("point1", repository.observeLatest().first()!!.id)
    }
}
