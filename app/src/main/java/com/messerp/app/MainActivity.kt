package com.messerp.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messerp.app.data.Notifier
import com.messerp.app.ui.AppViewModel
import com.messerp.app.ui.NotificationsScreen
import com.messerp.app.ui.BillsScreen
import com.messerp.app.ui.ExpensesScreen
import com.messerp.app.ui.FinanceScreen
import com.messerp.app.ui.HomeScreen
import com.messerp.app.ui.LoginScreen
import com.messerp.app.ui.MealScreen
import com.messerp.app.ui.MessTheme
import com.messerp.app.ui.ProfileScreen
import com.messerp.app.ui.RegisterScreen
import com.messerp.app.ui.Screen
import com.messerp.app.ui.SplashScreen

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        setContent {
            val vm: AppViewModel = viewModel()
            LaunchedEffect(vm.profile?.id) {
                if (vm.profile != null && Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            MessTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (vm.screen) {
                        Screen.Splash -> SplashScreen(vm)
                        Screen.Login -> LoginScreen(vm, adminMode = false)
                        Screen.AdminLogin -> {
                            BackHandler { vm.go(Screen.Login) }
                            LoginScreen(vm, adminMode = true)
                        }
                        Screen.Register -> {
                            BackHandler { vm.go(Screen.Login) }
                            RegisterScreen(vm)
                        }
                        Screen.Home -> HomeScreen(vm)
                        Screen.Meal -> {
                            BackHandler { vm.go(Screen.Home) }
                            MealScreen(vm)
                        }
                        Screen.Notifications -> {
                            BackHandler { vm.go(Screen.Home) }
                            NotificationsScreen(vm)
                        }
                        Screen.Bills -> {
                            BackHandler { vm.go(Screen.Home) }
                            BillsScreen(vm)
                        }
                        Screen.Finance -> {
                            BackHandler { vm.go(Screen.Home) }
                            FinanceScreen(vm)
                        }
                        Screen.Expenses -> {
                            BackHandler { vm.go(Screen.Home) }
                            ExpensesScreen(vm)
                        }
                        Screen.ProfileScreen -> {
                            BackHandler { vm.go(Screen.Home) }
                            ProfileScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
