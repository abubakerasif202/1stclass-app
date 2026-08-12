package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.repository.RoomDriverRepository
import com.example.data.repository.RoomEvidenceRepository
import com.example.data.repository.RoomInspectionRepository
import com.example.data.repository.RoomJobRepository
import com.example.data.repository.RoomShiftRepository
import com.example.data.repository.RoomSyncRepository
import com.example.data.seed.PrototypeSeedData
import com.example.domain.repository.DriverRepository
import com.example.domain.repository.EvidenceRepository
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.JobRepository
import com.example.domain.repository.ShiftRepository
import com.example.domain.repository.SyncRepository

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "first_class_express.db"
    ).build()

    private val codec = JobPayloadCodec()

    val driverRepository: DriverRepository = RoomDriverRepository(database.referenceDataDao())
    val inspectionRepository: InspectionRepository = RoomInspectionRepository(database)
    val shiftRepository: ShiftRepository = RoomShiftRepository(database, inspectionRepository)
    val jobRepository: JobRepository = RoomJobRepository(database, codec)
    val evidenceRepository: EvidenceRepository = RoomEvidenceRepository(database)
    val syncRepository: SyncRepository = RoomSyncRepository(database.syncOperationDao())
    val prototypeSeedData: PrototypeSeedData = PrototypeSeedData(database, codec)
}
