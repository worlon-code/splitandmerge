package com.splitandmerge.mkvslice.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreen
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreenTablet
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsViewModel
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsScreen
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsViewModel
import com.splitandmerge.mkvslice.ui.jobs.JobsScreen
import com.splitandmerge.mkvslice.ui.jobs.JobsScreenTablet
import com.splitandmerge.mkvslice.ui.jobs.JobsViewModel
import com.splitandmerge.mkvslice.ui.library.LibraryScreen
import com.splitandmerge.mkvslice.ui.library.LibraryScreenTablet
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigScreen
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderScreen
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderViewModel
import com.splitandmerge.mkvslice.ui.onboarding.OnboardingScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressViewModel
import com.splitandmerge.mkvslice.ui.result.MergeResultScreen
import com.splitandmerge.mkvslice.ui.result.SplitResultScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsViewModel
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreen
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreenTablet
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigViewModel
import com.splitandmerge.mkvslice.ui.splitconfirm.SplitConfirmScreen
import com.splitandmerge.mkvslice.ui.oss.OssNoticesScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    NavHost(
        navController = navController,
        startDestination = Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = viewModel()
            if (isTablet) {
                LibraryScreenTablet(
                    viewModel = libraryViewModel,
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onStartSplitFlow = { navController.navigate(Routes.FILE_DETAILS) },
                    onStartMergeFlow = { navController.navigate(Routes.MERGE_ORDER) },
                    onNavigateToJobDetail = { jobId ->
                        navController.navigate(Routes.jobProgress(jobId))
                    }
                )
            } else {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onStartSplitFlow = { navController.navigate(Routes.FILE_DETAILS) },
                    onStartMergeFlow = { navController.navigate(Routes.MERGE_ORDER) },
                    onNavigateToJobDetail = { jobId ->
                        navController.navigate(Routes.jobProgress(jobId))
                    }
                )
            }
        }

        composable(Routes.FILE_DETAILS) {
            val fileDetailsViewModel: FileDetailsViewModel = viewModel()
            FileDetailsScreen(
                viewModel = fileDetailsViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.SPLIT_CONFIG) }
            )
        }

        composable(Routes.SPLIT_CONFIG) {
            val splitConfigViewModel: SplitConfigViewModel = viewModel()
            if (isTablet) {
                SplitConfigScreenTablet(
                    viewModel = splitConfigViewModel,
                    onBack = { navController.popBackStack() },
                    onConfirm = { navController.navigate(Routes.SPLIT_CONFIRM) }
                )
            } else {
                SplitConfigScreen(
                    viewModel = splitConfigViewModel,
                    onBack = { navController.popBackStack() },
                    onConfirm = { navController.navigate(Routes.SPLIT_CONFIRM) }
                )
            }
        }

        composable(Routes.SPLIT_CONFIRM) {
            // Sharing ViewModel to show preview details in confirmation
            val splitConfigViewModel: SplitConfigViewModel = viewModel()
            SplitConfirmScreen(
                viewModel = splitConfigViewModel,
                onBack = { navController.popBackStack() },
                onConfirm = {
                    navController.navigate(Routes.jobProgress("job-1")) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.JOB_PROGRESS,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: "1"
            val progressViewModel: JobProgressViewModel = viewModel()
            JobProgressScreen(
                viewModel = progressViewModel,
                jobId = jobId,
                onNavigateToResult = { id ->
                    if (id == "job-2") {
                        navController.navigate(Routes.mergeResult(id)) {
                            popUpTo(Routes.LIBRARY) { inclusive = false }
                        }
                    } else {
                        navController.navigate(Routes.splitResult(id)) {
                            popUpTo(Routes.LIBRARY) { inclusive = false }
                        }
                    }
                },
                onCancelOrBack = { navController.popBackStack(Routes.LIBRARY, false) }
            )
        }

        composable(
            route = Routes.SPLIT_RESULT,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: "1"
            SplitResultScreen(
                jobId = jobId,
                onMergePartsShortcut = { navController.navigate(Routes.MERGE_ORDER) },
                onNavigateHome = { navController.popBackStack(Routes.LIBRARY, false) }
            )
        }

        composable(Routes.MERGE_ORDER) {
            val mergeOrderViewModel: MergeOrderViewModel = viewModel()
            MergeOrderScreen(
                viewModel = mergeOrderViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.MERGE_CONFIG) }
            )
        }

        composable(Routes.MERGE_CONFIG) {
            MergeConfigScreen(
                onBack = { navController.popBackStack() },
                onConfirm = {
                    navController.navigate(Routes.jobProgress("job-2")) {
                        popUpTo(Routes.LIBRARY) { inclusive = false }
                    }
                }
            )
        }

        composable(
            route = Routes.MERGE_RESULT,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: "1"
            MergeResultScreen(
                jobId = jobId,
                onNavigateHome = { navController.popBackStack(Routes.LIBRARY, false) }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToCleanupPatterns = { navController.navigate(Routes.CLEANUP_PATTERNS) },
                onNavigateToOssNotices = { navController.navigate(Routes.OSS_NOTICES) }
            )
        }

        composable(Routes.CLEANUP_PATTERNS) {
            val cleanupViewModel: CleanupPatternsViewModel = viewModel()
            if (isTablet) {
                CleanupPatternsScreenTablet(
                    viewModel = cleanupViewModel,
                    onBack = { navController.popBackStack() }
                )
            } else {
                CleanupPatternsScreen(
                    viewModel = cleanupViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        composable(Routes.OSS_NOTICES) {
            OssNoticesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
