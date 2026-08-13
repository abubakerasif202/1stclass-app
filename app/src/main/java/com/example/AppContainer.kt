package com.example

import android.content.Context
import androidx.room.Room
import com.example.data.auth.DevelopmentDriverProvisioner
import com.example.data.auth.LocalAuthRepository
import com.example.data.auth.PinHasher
import com.example.data.auth.remote.RemoteAuthRepository
import com.example.data.auth.remote.TmsAuthApi
import com.example.data.evidence.BitmapSignatureRenderer
import com.example.data.evidence.FileSystemEvidenceFileStore
import com.example.data.local.ALL_MIGRATIONS
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.repository.RoomDriverRepository
import com.example.data.repository.RoomEvidenceRepository
import com.example.data.repository.RoomFreightExceptionRepository
import com.example.data.repository.RoomInspectionRepository
import com.example.data.repository.RoomJobRepository
import com.example.data.repository.RoomLocationRepository
import com.example.data.repository.RoomShiftRepository
import com.example.data.repository.RoomSyncRepository
import com.example.data.seed.PrototypeSeedData
import com.example.data.session.DataStoreSessionRepository
import com.example.data.startup.AppBootstrapper
import com.example.domain.evidence.EvidenceCaptureService
import com.example.domain.evidence.EvidenceFileStore
import com.example.domain.evidence.SignatureRenderer
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.DriverRepository
import com.example.domain.repository.EvidenceRepository
import com.example.domain.repository.FreightExceptionRepository
import com.example.domain.repository.InspectionRepository
import com.example.domain.repository.JobRepository
import com.example.domain.repository.LocationRepository
import com.example.domain.repository.SessionRepository
import com.example.domain.repository.ShiftRepository
import com.example.domain.repository.SyncRepository
import com.example.location.LocationTrackingController
import com.example.location.LocationTrackingStateStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "first_class_express.db"
    ).addMigrations(*ALL_MIGRATIONS).build()

    private val codec = JobPayloadCodec()
    private val pinHasher = PinHasher()

    val driverRepository: DriverRepository = RoomDriverRepository(database.referenceDataDao())
    val inspectionRepository: InspectionRepository = RoomInspectionRepository(database)
    val shiftRepository: ShiftRepository = RoomShiftRepository(database, inspectionRepository)
    val jobRepository: JobRepository = RoomJobRepository(database, codec)
    val locationRepository: LocationRepository = RoomLocationRepository(database)
    val locationStateStore = LocationTrackingStateStore()
    val locationTrackingController = LocationTrackingController(context.applicationContext)

    init {
        applicationScope.launch {
            locationRepository.observeLatest().collect(locationStateStore::restoreLastPoint)
        }
    }
    val evidenceRepository: EvidenceRepository = RoomEvidenceRepository(database)
    val freightExceptionRepository: FreightExceptionRepository =
        RoomFreightExceptionRepository(database)
    val syncRepository: SyncRepository = RoomSyncRepository(database.syncOperationDao())
    val prototypeSeedData: PrototypeSeedData = PrototypeSeedData(database, codec)

    val sessionRepository: SessionRepository = DataStoreSessionRepository(context)

    /**
     * Local auth is used until a TMS base URL is configured for the build. Both implementations
     * satisfy the same [AuthRepository] contract, so nothing above this line changes when the real
     * endpoint arrives.
     */
    val authRepository: AuthRepository =
        if (BuildConfig.TMS_BASE_URL.isNotBlank()) {
            RemoteAuthRepository(
                Retrofit.Builder()
                    .baseUrl(BuildConfig.TMS_BASE_URL)
                    .addConverterFactory(
                        MoshiConverterFactory.create(
                            Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                        )
                    )
                    .build()
                    .create(TmsAuthApi::class.java)
            )
        } else {
            LocalAuthRepository(database.driverCredentialDao(), pinHasher)
        }

    val evidenceFileStore: EvidenceFileStore = FileSystemEvidenceFileStore(context)
    val signatureRenderer: SignatureRenderer = BitmapSignatureRenderer()
    val evidenceCaptureService = EvidenceCaptureService(
        evidenceRepository = evidenceRepository,
        fileStore = evidenceFileStore,
        signatureRenderer = signatureRenderer
    )

    val bootstrapper = AppBootstrapper(
        seedData = prototypeSeedData,
        provisioner = DevelopmentDriverProvisioner(database.driverCredentialDao(), pinHasher),
        developmentPin = BuildConfig.DEV_DRIVER_PIN
    )
}
