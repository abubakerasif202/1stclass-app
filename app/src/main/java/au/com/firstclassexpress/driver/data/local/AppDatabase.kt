package au.com.firstclassexpress.driver.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import au.com.firstclassexpress.driver.data.local.dao.DriverCredentialDao
import au.com.firstclassexpress.driver.data.local.dao.DriverIncidentDao
import au.com.firstclassexpress.driver.data.local.dao.DriverMessageDao
import au.com.firstclassexpress.driver.data.local.dao.EvidenceDao
import au.com.firstclassexpress.driver.data.local.dao.FreightExceptionDao
import au.com.firstclassexpress.driver.data.local.dao.InspectionDao
import au.com.firstclassexpress.driver.data.local.dao.JobDao
import au.com.firstclassexpress.driver.data.local.dao.JobTimelineEventDao
import au.com.firstclassexpress.driver.data.local.dao.LocationPointDao
import au.com.firstclassexpress.driver.data.local.dao.ReferenceDataDao
import au.com.firstclassexpress.driver.data.local.dao.ShiftDao
import au.com.firstclassexpress.driver.data.local.dao.SyncOperationDao
import au.com.firstclassexpress.driver.data.local.entity.DriverCredentialEntity
import au.com.firstclassexpress.driver.data.local.entity.DriverEntity
import au.com.firstclassexpress.driver.data.local.entity.DriverIncidentEntity
import au.com.firstclassexpress.driver.data.local.entity.DriverMessageEntity
import au.com.firstclassexpress.driver.data.local.entity.EvidenceEntity
import au.com.firstclassexpress.driver.data.local.entity.FreightExceptionEntity
import au.com.firstclassexpress.driver.data.local.entity.InspectionEntity
import au.com.firstclassexpress.driver.data.local.entity.InspectionItemEntity
import au.com.firstclassexpress.driver.data.local.entity.JobEntity
import au.com.firstclassexpress.driver.data.local.entity.JobTimelineEventEntity
import au.com.firstclassexpress.driver.data.local.entity.LocationPointEntity
import au.com.firstclassexpress.driver.data.local.entity.ShiftEntity
import au.com.firstclassexpress.driver.data.local.entity.SyncOperationEntity
import au.com.firstclassexpress.driver.data.local.entity.VehicleEntity

@Database(
    entities = [
        DriverEntity::class,
        DriverCredentialEntity::class,
        FreightExceptionEntity::class,
        VehicleEntity::class,
        ShiftEntity::class,
        InspectionEntity::class,
        InspectionItemEntity::class,
        JobEntity::class,
        EvidenceEntity::class,
        SyncOperationEntity::class,
        LocationPointEntity::class,
        JobTimelineEventEntity::class,
        DriverIncidentEntity::class,
        DriverMessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun referenceDataDao(): ReferenceDataDao
    abstract fun driverCredentialDao(): DriverCredentialDao
    abstract fun freightExceptionDao(): FreightExceptionDao
    abstract fun shiftDao(): ShiftDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun jobDao(): JobDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun locationPointDao(): LocationPointDao
    abstract fun jobTimelineEventDao(): JobTimelineEventDao
    abstract fun driverIncidentDao(): DriverIncidentDao
    abstract fun driverMessageDao(): DriverMessageDao
}
