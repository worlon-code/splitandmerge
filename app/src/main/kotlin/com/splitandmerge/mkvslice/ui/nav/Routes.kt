package com.splitandmerge.mkvslice.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val FILE_DETAILS = "file_details?uri={uri}&filename={filename}"
    const val SPLIT_CONFIG = "split_config?uri={uri}&filename={filename}&sizeBytes={sizeBytes}&durationSec={durationSec}"
    const val SPLIT_CONFIRM = "split_confirm"
    const val JOB_PROGRESS = "job_progress/{jobId}"
    const val SPLIT_RESULT = "split_result/{jobId}"
    const val MERGE_ORDER = "merge_order"
    const val MERGE_CONFIG = "merge_config?uris={uris}"
    const val MERGE_RESULT = "merge_result/{jobId}"
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    const val CLEANUP_PATTERNS = "cleanup_patterns"
    const val OSS_NOTICES = "oss_notices"

    fun jobProgress(jobId: String) = "job_progress/$jobId"
    fun splitResult(jobId: String) = "split_result/$jobId"
    fun mergeResult(jobId: String) = "merge_result/$jobId"
    
    fun mergeConfig(uris: String): String {
        return "merge_config?uris=${android.net.Uri.encode(uris)}"
    }
    
    fun fileDetails(uri: String, filename: String): String {
        return "file_details?uri=${android.net.Uri.encode(uri)}&filename=${android.net.Uri.encode(filename)}"
    }
    
    fun splitConfig(uri: String, filename: String, sizeBytes: Long, durationSec: Double): String {
        return "split_config?uri=${android.net.Uri.encode(uri)}&filename=${android.net.Uri.encode(filename)}&sizeBytes=$sizeBytes&durationSec=$durationSec"
    }
}
