package com.spendingtracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.spendingtracker.app.data.local.entity.AppSettings
import com.spendingtracker.app.data.repository.FinanceRepository
import com.spendingtracker.app.ui.navigation.AppNavHost
import com.spendingtracker.app.ui.theme.SpendingTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var financeRepository: FinanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var isSetupComplete = false
        var themeMode = "SYSTEM"
        splash.setKeepOnScreenCondition { false }

        runBlocking {
            try {
                financeRepository.ensureDefaultCategories()
                val settings = financeRepository.getSettingsSync()
                isSetupComplete = settings?.isSetupComplete == true
                themeMode = settings?.theme ?: "SYSTEM"
            } catch (e: Exception) {
                // ignore
            }
        }

        setContent {
            val darkTheme = when (themeMode) {
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
                        startDestination = if (isSetupComplete) "home" else "setup"
                    )
                }
            }
        }
    }
}
