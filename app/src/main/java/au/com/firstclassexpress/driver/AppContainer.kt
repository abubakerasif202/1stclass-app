package au.com.firstclassexpress.driver

import android.content.Context
import android.util.Log
import androidx.room.Room
import au.com.firstclassexpress.driver.data.auth.DevelopmentDriverProvisioner
import au.com.firstclassexpress.driver.data.auth.LocalAuthRepository
import au.com.firstclassexpress.driver.data.auth.PinHasher
import au.com.firstclassexpress.driver.data.auth.remote.RemoteAuthRepository
import au.com.firstclassexpress.driver.data.auth.remote.TmsAuthApi
import au.com.firstclassexpress.driver.data.auth.token.EncryptedTokenRepository
import au.com.firstclassexpress.driver.data.evidence.BitmapSignatureRenderer
import au.com.firstclassexpress.driver.data.evidence.FileSystemEvidenceFileStore
import au.com.firstclassexpress.driver.data.local.ALL_MIGRATIONS
import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.JobPayloadCodec
import au.com.firstclassexpress.driver.data.remote.AndroidConnectivityRepository
import au.com.firstclassexpress.driver.data.remote.ConnectivityRepository
import au.com.firstclassexpress.driver.data.remote.RetrofitRemoteJobDataSource
import au.com.firstclassexpress.driver.data.remote.RetrofitSyncTransport
import au.com.firstclassexpress.driver.data.remote.TmsApi
import au.com.firstclassexpress.driver.data.remote.TmsApiClient
import au.com.firstclassexpress.driver.data.remote.TmsEnvironment
import au.com.firstclassexpress.driver.data.repository.RoomDriverRepository
import au.com.firstclassexpress.driver.data.repository.RoomEvidenceRepository
import au.com.firstclassexpress.driver.data.repository.RoomFreightExceptionRepository
import au.com.firstclassexpress.driver.data.repository.RoomInspectionRepository
import au.com.firstclassexpress.driver.data.repository.RoomJobRepository
import au.com.firstclassexpress.driver.data.repository.RoomLocationRepository
import au.com.firstclassexpress.driver.data.repository.RoomShiftRepository
import au.com.firstclassexpress.driver.data.repository.RoomSyncQueue
import au.com.firstclassexpress.driver.data.repository.RoomSyncRepository
import au.com.firstclassexpress.driver.data.seed.PrototypeSeedData
import au.com.firstclassexpress.driver.data.session.DataStoreSessionRepository
import au.com.firstclassexpress.driver.data.startup.AppBootstrapper
import au.com.firstclassexpress.driver.data.sync.SyncOperationProcessor
import au.com.firstclassexpress.driver.domain.evidence.EvidenceCaptureService
import au.com.firstclassexpress.driver.domain.evidence.EvidenceFileStore
import au.com.firstclassexpress.driver.domain.evidence.SignatureRenderer
import au.com.firstclassexpress.driver.domain.repository.AuthRepository
import au.com.firstclassexpress.driver.domain.repository.DriverRepository
import au.com.firstclassexpress.driver.domain.repository.EvidenceRepository
import au.com.firstclassexpress.driver.domain.repository.FreightExceptionRepository
import au.com.firstclassexpress.driver.domain.repository.InspectionRepository
import au.com.firstclassexpress.driver.domain.repository.JobRepository
import au.com.firstclassexpress.driver.domain.repository.LocationRepository
import au.com.firstclassexpress.driver.domain.repository.RemoteJobDataSource
import au.com.firstclassexpress.driver.domain.repository.SessionRepository
import au.com.firstclassexpress.driver.domain.repository.ShiftRepository
import au.com.firstclassexpress.driver.domain.repository.SyncRepository
import au.com.firstclassexpress.driver.domain.repository.TokenRepository
import au.com.firstclassexpress.driver.domain.repository.UnconfiguredRemoteJobDataSource
import au.com.firstclassexpress.driver.domain.sync.SyncEngine
import au.com.firstclassexpress.driver.domain.sync.SyncQueue
import au.com.firstclassexpress.driver.domain.sync.SyncTransport
import au.com.firstclassexpress.driver.domain.sync.UnconfiguredSyncTransport
import au.com.firstclassexpress.driver.location.LocationTrackingController
import au.com.firstclassexpress.driver.location.LocationTrackingStateStore
import au.com.firstclassexpress.driver.push.FirebasePushMessagingClient
import au.com.firstclassexpress.driver.push.PushRegistrationCoordinator
import au.com.firstclassexpress.driver.push.PushTokenStore
import au.com.firstclassexpress.driver.sync.SyncScheduler
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
    val jobTimelineRepository: au.com.firstclassexpress.driver.domain.repository.JobTimelineRepository =
        au.com.firstclassexpress.driver.data.repository.RoomJobTimelineRepository(database)
    val incidentRepository: au.com.firstclassexpress.driver.domain.repository.IncidentRepository =
        au.com.firstclassexpress.driver.data.repository.RoomIncidentRepository(database)
    val messageRepository: au.com.firstclassexpress.driver.domain.repository.MessageRepository =
        au.com.firstclassexpress.driver.data.repository.RoomMessageRepository(database)
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

    val deviceRegistrationRepository: au.com.firstclassexpress.driver.domain.repository.DeviceRegistrationRepository =
        au.com.firstclassexpress.driver.data.repository.DefaultDeviceRegistrationRepository(
            context = context,
            apiProvider = {
                (tmsEnvironment as? TmsEnvironment.Configured)?.let { env ->
                    TmsApiClient.retrofit(
                        baseUrl = env.baseUrl,
                        client = TmsApiClient.okHttp(tokenRepository, BuildConfig.DEBUG),
                        moshi = moshi
                    ).create(au.com.firstclassexpress.driver.data.remote.api.DeviceRegistrationApi::class.java)
                }
            }
        )

    val pushTokenStore = PushTokenStore(context)
    val pushRegistrationCoordinator = PushRegistrationCoordinator(
        sessionRepository = sessionRepository,
        deviceRegistrationRepository = deviceRegistrationRepository,
        tokenStore = pushTokenStore,
        tokenProvider = FirebasePushMessagingClient(context)::currentToken,
        appVersionName = BuildConfig.VERSION_NAME
    )

    val remoteAppConfigRepository: au.com.firstclassexpress.driver.domain.config.RemoteAppConfigRepository =
        au.com.firstclassexpress.driver.data.repository.DefaultRemoteAppConfigRepository(
            currentAppVersion = BuildConfig.VERSION_NAME,
            apiProvider = {
                (tmsEnvironment as? TmsEnvironment.Configured)?.let { env ->
                    TmsApiClient.retrofit(
                        baseUrl = env.baseUrl,
                        client = TmsApiClient.okHttp(tokenRepository, BuildConfig.DEBUG),
                        moshi = moshi
                    ).create(au.com.firstclassexpress.driver.data.remote.api.AppConfigApi::class.java)
                }
            }
        )

    val bootstrapper = AppBootstrapper(
        seedData = prototypeSeedData,
        provisioner = DevelopmentDriverProvisioner(database.driverCredentialDao(), pinHasher),
        developmentPin = BuildConfig.DEV_DRIVER_PIN,
        developmentFixturesEnabled = BuildConfig.SHOW_DEV_CREDENTIALS
    )

    init {
        applicationScope.launch {
            locationRepository.observeLatest()
                .catch { Log.w(TAG, "Location observer stopped", it) }
                .collect(locationStateStore::restoreLastPoint)
        }
        if (BuildConfig.SHOW_DEV_CREDENTIALS) applicationScope.launch {
            messageRepository.seedInitialMessagesIfEmpty()
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
        applicationScope.launch {
            sessionRepository.observeSession()
                .distinctUntilChanged()
                .catch { Log.w(TAG, "Push registration observer stopped", it) }
                .collect { session ->
                    if (session != null) pushRegistrationCoordinator.registerIfAuthenticated()
                }
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
