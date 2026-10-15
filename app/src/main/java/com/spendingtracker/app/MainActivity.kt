package com.spendingtracker.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.ui.navigation.AppNavHost
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var financeRepository: FinanceRepository

    private data class StartupInfo(val isSetupComplete: Boolean, val themeMode: String, val databaseError: Boolean = false)

    // Read on the main thread by Compose; written once DB load finishes off the main thread.
    private val startupState: MutableState<StartupInfo?> = mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the splash screen up only until startup data is ready (never block the main thread).
        splash.setKeepOnScreenCondition { startupState.value == null }

        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                var isSetupComplete = false
                var themeMode = "SYSTEM"
                var databaseError = false
                try {
                    financeRepository.ensureDefaultCategories()
                    val settings = financeRepository.getSettingsSync()
                    isSetupComplete = settings?.isSetupComplete == true
                    themeMode = settings?.theme ?: "SYSTEM"
                } catch (e: Exception) {
                    // Fail closed: an unreadable existing database must not look like first launch.
                    databaseError = true
                }
                StartupInfo(isSetupComplete, themeMode, databaseError)
            }
            startupState.value = info
        }

        setContent {
            val startup = startupState.value
            if (startup == null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                return@setContent
            }

            if (startup.databaseError) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Text("Saved data could not be opened. No data was erased. Keep the app installed and resolve the database upgrade before continuing.")
                    }
                }
                return@setContent
            }

            // Ask for notification permission on Android 13+ so balance/loan alerts can be shown.
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* result handled by system settings */ }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val darkTheme = when (startup.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            SpendingTrackerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(
                        navController = navController,
                        startDestination = if (startup.isSetupComplete) {
                            com.spendingtracker.app.ui.navigation.Screen.Main.route
                        } else {
                            "setup"
                        }
                    )
                }
            }
        }
    }
}
