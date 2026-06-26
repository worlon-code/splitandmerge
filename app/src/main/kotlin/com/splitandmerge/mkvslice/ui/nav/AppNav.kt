package com.splitandmerge.mkvslice.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.splitandmerge.mkvslice.ui.result.MergeResultViewModel
import com.splitandmerge.mkvslice.ui.result.SplitResultScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsViewModel
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreen
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreenTablet
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigViewModel
import com.splitandmerge.mkvslice.ui.splitconfirm.SplitConfirmScreen
import com.splitandmerge.mkvslice.ui.oss.OssNoticesScreen
import com.splitandmerge.mkvslice.ui.logs.LogViewerScreen
import com.splitandmerge.mkvslice.ui.logs.LogViewerViewModel
import com.splitandmerge.mkvslice.domain.model.JobType

@Composable
fun AppNav(
    startDestination: String = Routes.ONBOARDING,
    onOnboardingFinished: () -> Unit = {}
) {
    val navController = rememberNavController()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    onOnboardingFinished()
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            if (isTablet) {
                LibraryScreenTablet(
                    viewModel = libraryViewModel,
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onStartSplitFlow = { uri, filename -> navController.navigate(Routes.fileDetails(uri, filename)) },
                    onStartMergeFlow = { navController.navigate(Routes.MERGE_ORDER) },
                    onStartDefaultTracksFlow = { navController.navigate(Routes.DEFAULT_TRACKS_FLOW) },
                    onNavigateToJobDetail = { jobId ->
                        navController.navigate(Routes.jobProgress(jobId))
                    },
                    onNavigateToSplitResult = { jobId ->
                        navController.navigate(Routes.splitResult(jobId))
                    },
                    onNavigateToMergeResult = { jobId ->
                        navController.navigate(Routes.mergeResult(jobId))
                    }
                )
            } else {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onStartSplitFlow = { uri, filename -> navController.navigate(Routes.fileDetails(uri, filename)) },
                    onStartMergeFlow = { navController.navigate(Routes.MERGE_ORDER) },
                    onStartDefaultTracksFlow = { navController.navigate(Routes.DEFAULT_TRACKS_FLOW) },
                    onNavigateToJobDetail = { jobId ->
                        navController.navigate(Routes.jobProgress(jobId))
                    },
                    onNavigateToSplitResult = { jobId ->
                        navController.navigate(Routes.splitResult(jobId))
                    },
                    onNavigateToMergeResult = { jobId ->
                        navController.navigate(Routes.mergeResult(jobId))
                    }
                )
            }
        }

        composable(
            route = Routes.FILE_DETAILS,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("filename") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: ""
            val filename = backStackEntry.arguments?.getString("filename") ?: ""
            val fileDetailsViewModel: FileDetailsViewModel = hiltViewModel()
            
            androidx.compose.runtime.LaunchedEffect(uri) {
                if (uri.isNotEmpty()) {
                    fileDetailsViewModel.probeFile(uri, filename)
                }
            }

            FileDetailsScreen(
                viewModel = fileDetailsViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { 
                    val size = fileDetailsViewModel.fileDetails.value?.sizeBytes ?: 0L
                    val dur = fileDetailsViewModel.fileDetails.value?.durationSec ?: 0.0
                    navController.navigate(Routes.splitConfig(uri, filename, size, dur)) 
                }
            )
        }

        composable(
            route = Routes.SPLIT_CONFIG,
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("filename") { type = NavType.StringType },
                navArgument("sizeBytes") { type = NavType.StringType },
                navArgument("durationSec") { type = NavType.StringType }
            )
        ) {
            val splitConfigViewModel: SplitConfigViewModel = hiltViewModel()
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
            val parentEntry = navController.previousBackStackEntry
            val splitConfigViewModel: SplitConfigViewModel = if (parentEntry != null) {
                hiltViewModel(parentEntry)
            } else {
                hiltViewModel()
            }
            
            SplitConfirmScreen(
                viewModel = splitConfigViewModel,
                onBack = { navController.popBackStack() },
                onConfirm = { generatedJobId ->
                    navController.navigate(Routes.jobProgress(generatedJobId)) {
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
            val progressViewModel: JobProgressViewModel = hiltViewModel()
            JobProgressScreen(
                viewModel = progressViewModel,
                jobId = jobId,
                onNavigateToResult = { id ->
                    val jobType = progressViewModel.state.value.jobType
                    if (jobType == JobType.MERGE) {
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
            val mergeOrderViewModel: com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderViewModel = hiltViewModel()
            com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderScreen(
                viewModel = mergeOrderViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.mergeConfig(mergeOrderViewModel.getPartsUris())) }
            )
        }

        composable(
            route = Routes.MERGE_CONFIG,
            arguments = listOf(navArgument("uris") { type = NavType.StringType })
        ) { backStackEntry ->
            val uris = backStackEntry.arguments?.getString("uris") ?: ""
            val mergeConfigViewModel: com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigViewModel = hiltViewModel()
            
            androidx.compose.runtime.LaunchedEffect(uris) {
                mergeConfigViewModel.initMock(uris)
            }
            
            com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigScreen(
                viewModel = mergeConfigViewModel,
                onBack = { navController.popBackStack() },
                onConfirm = { jobId ->
                    navController.navigate(Routes.jobProgress(jobId)) {
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
            val mergeResultViewModel: MergeResultViewModel = hiltViewModel()
            MergeResultScreen(
                viewModel = mergeResultViewModel,
                onNavigateHome = { navController.popBackStack(Routes.LIBRARY, false) }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToCleanupPatterns = { navController.navigate(Routes.CLEANUP_PATTERNS) },
                onNavigateToOssNotices = { navController.navigate(Routes.OSS_NOTICES) },
                onNavigateToLogs = { navController.navigate(Routes.LOGS) }
            )
        }

        composable(Routes.LOGS) {
            val logsViewModel: LogViewerViewModel = hiltViewModel()
            LogViewerScreen(
                viewModel = logsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CLEANUP_PATTERNS) {
            val cleanupViewModel: CleanupPatternsViewModel = hiltViewModel()
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

        composable(Routes.DEFAULT_TRACKS_FLOW) {
            val defaultTracksViewModel: com.splitandmerge.mkvslice.ui.defaulttracks.DefaultTracksViewModel = hiltViewModel()
            com.splitandmerge.mkvslice.ui.defaulttracks.DefaultTracksFlowScreen(
                viewModel = defaultTracksViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
