package com.splitandmerge.mkvslice.ui.nav

object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val FILE_DETAILS = "file_details"
    const val SPLIT_CONFIG = "split_config"
    const val SPLIT_CONFIRM = "split_confirm"
    const val JOB_PROGRESS = "job_progress/{jobId}"
    const val SPLIT_RESULT = "split_result/{jobId}"
    const val MERGE_ORDER = "merge_order"
    const val MERGE_CONFIG = "merge_config"
    const val MERGE_RESULT = "merge_result/{jobId}"
    const val JOBS = "jobs"
    const val SETTINGS = "settings"
    const val CLEANUP_PATTERNS = "cleanup_patterns"
    const val OSS_NOTICES = "oss_notices"

    fun jobProgress(jobId: String) = "job_progress/$jobId"
    fun splitResult(jobId: String) = "split_result/$jobId"
    fun mergeResult(jobId: String) = "merge_result/$jobId"
}
