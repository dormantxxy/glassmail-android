package com.dormantxxy.glassmail.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dormantxxy.glassmail.ui.screens.*
import com.dormantxxy.glassmail.ui.theme.GlassMailTheme


data class MailtoRequest(val id: Long, val to: String, val subject: String, val body: String)

private object Routes {
    const val MAILBOX = "mailbox"
    const val COMPOSE = "compose"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ADD_ACCOUNT = "add-account"
    const val MESSAGE = "message/{id}"
    const val ACCOUNT = "account/{id}"
    fun message(id: String) = "message/$id"
    fun account(id: String) = "account/$id"
}

@Composable
fun GlassMailRoot(viewModel: AppViewModel, mailtoRequest: MailtoRequest? = null) {
    val accounts by viewModel.accounts.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    GlassMailTheme(preferences.themeMode) {
        if (accounts.isEmpty()) {
            OnboardingScreen(viewModel)
        } else {
            val navController = rememberNavController()
            val snackbar = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(mailtoRequest?.id) {
                val request = mailtoRequest ?: return@LaunchedEffect
                viewModel.startExternalMessage(request.to, request.subject, request.body)
                navController.navigate(Routes.COMPOSE) { launchSingleTop = true }
            }
            LaunchedEffect(Unit) {
                viewModel.events.collect { event ->
                    val result = snackbar.showSnackbar(
                        message = event.message,
                        actionLabel = event.action,
                        withDismissAction = event.action == null,
                        duration = if (event.action != null) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.handleEventAction(event)
                }
            }

            Box(Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = Routes.MAILBOX) {
                    composable(Routes.MAILBOX) {
                        MailboxScreen(
                            viewModel = viewModel,
                            onOpenMessage = { navController.navigate(Routes.message(it)) },
                            onCompose = { navController.navigate(Routes.COMPOSE) },
                            onSearch = { navController.navigate(Routes.SEARCH) },
                            onSettings = { navController.navigate(Routes.SETTINGS) },
                            onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) }
                        )
                    }
                    composable(Routes.COMPOSE) { ComposeScreen(viewModel) { navController.popBackStack() } }
                    composable(Routes.SEARCH) {
                        SearchScreen(viewModel, onBack = { navController.popBackStack() }, onOpen = { navController.navigate(Routes.message(it)) })
                    }
                    composable(
                        Routes.MESSAGE,
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { entry ->
                        MessageScreen(
                            messageId = entry.arguments?.getString("id").orEmpty(),
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onCompose = { navController.navigate(Routes.COMPOSE) }
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            viewModel,
                            onBack = { navController.popBackStack() },
                            onAddAccount = { navController.navigate(Routes.ADD_ACCOUNT) },
                            onAccount = { navController.navigate(Routes.account(it)) }
                        )
                    }
                    composable(Routes.ADD_ACCOUNT) {
                        AccountSetupScreen(viewModel, onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
                    }
                    composable(
                        Routes.ACCOUNT,
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { entry ->
                        AccountScreen(entry.arguments?.getString("id").orEmpty(), viewModel) { navController.popBackStack() }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            }
        }
    }
}
