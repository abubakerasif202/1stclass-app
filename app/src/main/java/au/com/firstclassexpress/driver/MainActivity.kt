package au.com.firstclassexpress.driver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import au.com.firstclassexpress.driver.navigation.AppNavigation
import au.com.firstclassexpress.driver.ui.theme.MyApplicationTheme
import au.com.firstclassexpress.driver.viewmodel.AppViewModel
import au.com.firstclassexpress.driver.viewmodel.viewModelFactory
import au.com.firstclassexpress.driver.push.PushDestination
import au.com.firstclassexpress.driver.push.PushMessageRouter
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notificationDestination = MutableStateFlow<PushDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        notificationDestination.value = PushMessageRouter.fromIntent(intent)
        enableEdgeToEdge()
        val container = (application as FirstClassExpressApplication).container

        setContent {
            val destination by notificationDestination.collectAsState()
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val appViewModel: AppViewModel = viewModel(
                        factory = viewModelFactory {
                            AppViewModel(
                                authRepository = container.authRepository,
                                sessionRepository = container.sessionRepository,
                                driverRepository = container.driverRepository,
                                jobRepository = container.jobRepository,
                                shiftRepository = container.shiftRepository,
                                syncRepository = container.syncRepository,
                                bootstrap = container.bootstrapper::ensureReady,
                                appVersionName = BuildConfig.VERSION_NAME
                            )
                        }
                    )
                    AppNavigation(
                        navController = navController,
                        viewModel = appViewModel,
                        container = container,
                        notificationDestination = destination,
                        onNotificationHandled = { notificationDestination.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = PushMessageRouter.fromIntent(intent)
    }
}
