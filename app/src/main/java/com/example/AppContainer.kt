package com.example

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.data.auth.DevelopmentDriverProvisioner
import com.example.data.auth.LocalAuthRepository
import com.example.data.auth.PinHasher
import com.example.data.auth.remote.RemoteAuthRepository
import com.example.data.auth.remote.TmsAuthApi
import com.example.data.auth.token.EncryptedTokenRepository
import com.example.data.evidence.BitmapSignatureRenderer
import com.example.data.evidence.FileSystemEvidenceFileStore
import com.example.data.local.ALL_MIGRATIONS
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.remote.AndroidConnectivityRepository
import com.example.data.remote.ConnectivityRepository
import com.example.data.remote.RetrofitRemoteJobDataSource
import com.example.data.remote.RetrofitSyncTransport
import com.example.data.remote.TmsApi
import com.example.data.remote.TmsApiClient
import com.example.data.remote.TmsEnvironment
import com.example.data.repository.RoomDriverRepository
import com.example.data.repository.RoomEvidenceRepository
import com.example.data.repository.RoomFreightExceptionRepository
import com.example.data.repository.RoomInspectionRepository
import com.example.data.repository.RoomJobRepository
import com.example.data.repository.RoomLocationRepository
import com.example.data.repository.RoomShiftRepository
import com.example.data.repository.RoomSyncQueue
import com.example.data.repository.RoomSyncRepository
import com.example.data.seed.PrototypeSeedData
import com.example.data.session.DataStoreSessionRepository
import com.example.data.startup.AppBootstrapper
import com.example.data.sync.SyncOperationProcessor
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
import com.example.domain.repository.RemoteJobDataSource
import com.example.domain.repository.SessionRepository
import com.example.domain.repository.ShiftRepository
import com.example.domain.repository.SyncRepository
import com.example.domain.repository.TokenRepository
import com.example.domain.repository.UnconfiguredRemoteJobDataSource
import com.example.domain.sync.SyncEngine
import com.example.domain.sync.SyncQueue
import com.example.domain.sync.SyncTransport
import com.example.domain.sync.UnconfiguredSyncTransport
import com.example.location.LocationTrackingController
import com.example.location.LocationTrackingStateStore
import com.example.sync.SyncScheduler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    /**
     * Long-lived observers run here. The handler is deliberate: a database or scheduling fault in
     * a background observer must be logged and contained, never allowed to reach the default
     * uncaught handler and take the process down while a driver is mid-delivery.
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            Log.w(TAG, "Background observer failed", error)
        }
    )
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

    val evidenceRepository: EvidenceRepository = RoomEvidenceRepository(database)
    val freightExceptionRepository: FreightExceptionRepository =
        RoomFreightExceptionRepository(database)
    val syncRepository: SyncRepository = RoomSyncRepository(database.syncOperationDao())
    val syncQueue: SyncQueue = RoomSyncQueue(database)
    val prototypeSeedData: PrototypeSeedData = PrototypeSeedData(database, codec)

    val sessionRepository: SessionRepository = DataStoreSessionRepository(context)

    /** Keystore-backed. Tokens never touch Room and never appear in a log. */
    val tokenRepository: TokenRepository = EncryptedTokenRepository.create(context)

    /**
     * Whether this build may talk to a TMS at all. Resolved once at startup from the build-time
     * `TMS_BASE_URL`; an empty value keeps the whole app in local/offline mode.
     */
    val tmsEnvironment: TmsEnvironment =
        TmsEnvironment.from(BuildConfig.TMS_BASE_URL, isDebugBuild = BuildConfig.DEBUG)

    val connectivityRepository: ConnectivityRepository = AndroidConnectivityRepository(context)

    private val moshi = TmsApiClient.moshi()

    private val tmsApi: TmsApi? = (tmsEnvironment as? TmsEnvironment.Configured)?.let { env ->
        TmsApiClient
            .retrofit(
                baseUrl = env.baseUrl,
                client = TmsApiClient.okHttp(tokenRepository, BuildConfig.DEBUG),
                moshi = moshi
            )
            .create(TmsApi::class.java)
    }

    /**
     * With no endpoint configured this is [UnconfiguredSyncTransport], which refuses every call.
     * That is the whole point: queued work stays PENDING rather than being marked synced against
     * a server that does not exist.
     */
    val syncTransport: SyncTransport =
        tmsApi?.let { RetrofitSyncTransport(it, moshi) } ?: UnconfiguredSyncTransport()

    val remoteJobDataSource: RemoteJobDataSource =
        tmsApi?.let { RetrofitRemoteJobDataSource(it) } ?: UnconfiguredRemoteJobDataSource()

    private val syncOperationProcessor = SyncOperationProcessor(database, syncTransport, moshi)

    val syncEngine = SyncEngine(
        queue = syncQueue,
        process = syncOperationProcessor::process,
        environment = { tmsEnvironment },
        // A rejected session must not silently retry with a dead token, and must not take any
        // local data with it.
        onUnauthorized = { tokenRepository.clear() }
    )

    val syncScheduler = SyncScheduler(context)

    /**
     * Local auth is used until a TMS base URL is configured for the build. Both implementations
     * satisfy the same [AuthRepository] contract, so nothing above this line changes when the real
     * endpoint arrives.
     */
    val authRepository: AuthRepository =
        tmsApi?.let {
            RemoteAuthRepository(
                TmsApiClient
                    .retrofit(
                        baseUrl = (tmsEnvironment as TmsEnvironment.Configured).baseUrl,
                        client = TmsApiClient.okHttp(tokenRepository, BuildConfig.DEBUG),
                        moshi = moshi
                    )
                    .create(TmsAuthApi::class.java),
                tokenRepository
            )
        } ?: LocalAuthRepository(database.driverCredentialDao(), pinHasher)

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

    init {
        applicationScope.launch {
            locationRepository.observeLatest()
                .catch { Log.w(TAG, "Location observer stopped", it) }
                .collect(locationStateStore::restoreLastPoint)
        }
        // One place that asks for a drain: whenever the queue goes from empty to non-empty, and
        // once at startup if a backlog survived the last session. WorkManager's network
        // constraint handles "when the connection comes back" without us polling for it.
        applicationScope.launch {
            syncRepository.observePending()
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .catch { Log.w(TAG, "Sync queue observer stopped", it) }
                .collect { hasWork -> if (hasWork) syncScheduler.requestSync() }
        }
    }

    /** Stops the long-lived observers. Used by tests and by any future process teardown. */
    fun shutdown() {
        applicationScope.cancel()
    }

    private companion object {
        const val TAG = "AppContainer"
    }
}
