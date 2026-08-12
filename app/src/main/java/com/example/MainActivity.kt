package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FirstClassExpressApplication).container

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val appViewModel: AppViewModel = viewModel(
                        factory = viewModelFactory {
                            AppViewModel(
                                driverRepository = container.driverRepository,
                                jobRepository = container.jobRepository,
                                shiftRepository = container.shiftRepository,
                                prototypeSeedData = container.prototypeSeedData
                            )
                        }
                    )
                    AppNavigation(
                        navController = navController,
                        viewModel = appViewModel,
                        container = container
                    )
                }
            }
        }
    }
}
