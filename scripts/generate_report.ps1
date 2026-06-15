$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$reportFile = "logs/merge-failure-2-$timestamp.md"

$planContent = Get-Content AI/plans/2026-06-13-fix-merge-saf-paths.md -Raw
$gitLog = Get-Content logs/git_log.txt -Raw
$gitStat = Get-Content logs/git_show_stat.txt -Raw
$gitShow = Get-Content logs/git_show_full.txt -Raw
$concatTxt = Get-Content logs/adb_concat.txt -Raw

Add-Content $reportFile "# Section 1 — Plan + commit reality`n"
Add-Content $reportFile "Plan MD Path: AI/plans/2026-06-13-fix-merge-saf-paths.md`n"
Add-Content $reportFile "```markdown`n"
Add-Content $reportFile $planContent
Add-Content $reportFile "````n"

Add-Content $reportFile "git log --oneline -20:`n```text`n"
Add-Content $reportFile $gitLog
Add-Content $reportFile "````n"

Add-Content $reportFile "git show --stat 5ef74cd:`n```text`n"
Add-Content $reportFile $gitStat
Add-Content $reportFile "````n"

Add-Content $reportFile "git show 5ef74cd:`n```diff`n"
Add-Content $reportFile $gitShow
Add-Content $reportFile "````n"

Add-Content $reportFile "Confirming sub-fixes:`n"
Add-Content $reportFile "1. Real-path resolution: PARTIAL. PathResolver.kt was added, but Merger.kt fails to resolve and falls back to content:// URI.`n"
Add-Content $reportFile "2. Canonical argv: ABSENT. The argv is missing -map 0 -map 0:t? -avoid_negative_ts make_zero -f matroska.`n"
Add-Content $reportFile "3. MergeValidator added + wired: PRESENT. mergeValidator.validate(partUris) is called in Merger.kt.`n"
Add-Content $reportFile "4. Schema hygiene — manifestPath no longer CSV: PRESENT. JobDao uses getPartsForJob(jobId).`n"
Add-Content $reportFile "5. Pre-flight storage check on cache volume: PRESENT. context.cacheDir.usableSpace < totalSizeRequired is checked.`n"
Add-Content $reportFile "6. (Out of scope)`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 2 — Source files (paste in full)`n────────────────────────────────────────────`n"

function Append-SourceFile {
    param([string]$Path, [string]$Lang)
    $lines = Get-Content $Path -ErrorAction SilentlyContinue
    if ($null -eq $lines) {
        Add-Content $reportFile "`n$Path`n(file not present)`n"
    } else {
        Add-Content $reportFile "`n$Path`n```$Lang"
        Add-Content $reportFile $lines
        Add-Content $reportFile "```"
    }
}

Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeListWriter.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/engine/FfmpegEngine.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfmpegEngine.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/StoragePreflight.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolver.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/SafFile.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeValidator.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/service/JobService.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/data/repository/JobsRepository.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/entity/JobEntity.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/entity/PartEntity.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/JobDao.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModel.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeconfig/MergeConfigViewModel.kt" "kotlin"
Append-SourceFile "app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressViewModel.kt" "kotlin"
Append-SourceFile "app/src/test/kotlin/com/splitandmerge/mkvslice/engine/MergerArgvTest.kt" "kotlin"
Append-SourceFile "app/src/test/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeValidatorTest.kt" "kotlin"
Append-SourceFile "app/src/test/kotlin/com/splitandmerge/mkvslice/domain/merger/StoragePreflightTest.kt" "kotlin"
Append-SourceFile "app/src/test/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolverTest.kt" "kotlin"
Append-SourceFile "app/src/main/AndroidManifest.xml" "xml"
Append-SourceFile "app/build.gradle.kts" "kotlin"
Append-SourceFile "gradle/libs.versions.toml" "toml"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 3 — Reproduce the failure end-to-end`n────────────────────────────────────────────`n"
Add-Content $reportFile "Source job: 3-part dridam split.`n"
Add-Content $reportFile "| Part | Path | Size |`n|---|---|---|`n"
Add-Content $reportFile "| 1 | /document/primary:Movies/dridam.part001.mkv | Unknown |`n"
Add-Content $reportFile "| 2 | /document/primary:Movies/dridam.part002.mkv | Unknown |`n"
Add-Content $reportFile "| 3 | /document/primary:Movies/dridam.part003.mkv | Unknown |`n"
Add-Content $reportFile "`nLogcat block:`n```text`n"
Add-Content $reportFile "01 06-13 03:20:46.497 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv (resolved: saf:16.mkv)`n"
Add-Content $reportFile "02 06-13 03:20:46.675 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part002.mkv (resolved: saf:17.mkv)`n"
Add-Content $reportFile "03 06-13 03:20:46.850 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part003.mkv (resolved: saf:18.mkv)`n"
Add-Content $reportFile "04 06-13 03:20:47.417 11234 11976 D ENGINE  : start token=923a64ca-faea-49cf-aebc-65f597c5098b args=-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv`n"
Add-Content $reportFile "05 06-13 03:20:47.418 11234 11976 D ENGINE  : start sessionId=20 token=923a64ca-faea-49cf-aebc-65f597c5098b`n"
Add-Content $reportFile "06 06-13 03:20:47.422 11234 11979 V ENGINE  : stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!`n"
Add-Content $reportFile "07 06-13 03:20:47.422 11234 12628 D ENGINE  : done token=923a64ca-faea-49cf-aebc-65f597c5098b exit=1`n"
Add-Content $reportFile "08 06-13 03:20:47.422 11234 11979 V ENGINE  : stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'`n"
Add-Content $reportFile "09 06-13 03:20:47.423 11234 11979 V ENGINE  : stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument`n"
Add-Content $reportFile "````n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 4 — The exact argv as constructed today`n────────────────────────────────────────────`n"
Add-Content $reportFile "start token=923a64ca-faea-49cf-aebc-65f597c5098b args=-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv`n"
Add-Content $reportFile "-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv`n"
Add-Content $reportFile "`nTokens:`n-hide_banner`n-y`n-f`nconcat`n-safe`n0`n-i`n/data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt`n-c`ncopy`n/data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv`n"
Add-Content $reportFile "`nCompared against canonical list:`n"
Add-Content $reportFile "-hide_banner (PRESENT)`n-y (PRESENT)`n-f concat (PRESENT)`n-safe 0 (PRESENT)`n-i <list path> (PRESENT)`n-map 0 (MISSING)`n-map 0:t? (MISSING)`n-c copy (PRESENT)`n-avoid_negative_ts make_zero (MISSING)`n-f matroska (MISSING)`n<output path> (PRESENT)`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 5 — concat list as written this run`n────────────────────────────────────────────`n"
Add-Content $reportFile "List file path: /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt`n"
Add-Content $reportFile "```text`n"
Add-Content $reportFile $concatTxt
Add-Content $reportFile "````n"
Add-Content $reportFile "For each line:`n* Pattern: file '<path>'`n* Path is content://... (URI)`n* Path exists from app-private context: No (content URIs are not file paths)`n* Path exists from shell context: No`n* Is parent readable: N/A`n* Non-ASCII/whitespaces: Encoded URI entities present (e.g., %3A).`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 6 — FFmpeg stderr (raw)`n────────────────────────────────────────────`n"
Add-Content $reportFile "```text`n"
Add-Content $reportFile "01 ENGINE stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!`n"
Add-Content $reportFile "02 ENGINE stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'`n"
Add-Content $reportFile "03 ENGINE stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument`n"
Add-Content $reportFile "````n"
Add-Content $reportFile "Matches for fingerprints:`nProtocol '<x>' not on whitelist`n> ENGINE stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!`n"
Add-Content $reportFile "Impossible to open`n> ENGINE stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'`n"
Add-Content $reportFile "Invalid argument`n> ENGINE stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 7 — Per-part ffprobe fresh signatures`n────────────────────────────────────────────`n"
Add-Content $reportFile "(Omitted: On-device ffprobe output via FFmpegKit unavailable without intent hook. Probes were successful according to logcat but output details are not printed to logcat.)`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 8 — MergeValidator behaviour at runtime`n────────────────────────────────────────────`n"
Add-Content $reportFile "- Was MergeValidator invoked before the engine start? Yes.`n"
Add-Content $reportFile "- Quote the call site: mergeValidator.validate(partUris) in Merger.kt.`n"
Add-Content $reportFile "- What did it return for the failing parts? It returned successfully (no exception thrown), allowing the engine to proceed to the concat step.`n"
Add-Content $reportFile "- Was the result surfaced to S10 / S11 / S12 UI? The successful validation allowed the job to transition to RUNNING.`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 9 — Storage pre-flight at runtime`n────────────────────────────────────────────`n"
Add-Content $reportFile "- Which volume did the pre-flight measure? cacheDir (/data/user/95/com.splitandmerge.mkvslice.debug/cache)`n"
Add-Content $reportFile "- Available bytes reported: Unknown (not logged, but checked via context.cacheDir.usableSpace < totalSizeRequired).`n"
Add-Content $reportFile "- Required bytes computed: Unknown (from DocumentFile).`n"
Add-Content $reportFile "- Did it pass? Yes, otherwise the engine start log would not have occurred.`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 10 — Output staging path`n────────────────────────────────────────────`n"
Add-Content $reportFile "- Exact merge_tmp output path used: /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv`n"
Add-Content $reportFile "- .tmp residue: The code calls if (tempOutputFile.exists()) tempOutputFile.delete() before running, so there shouldn't be residue.`n"
Add-Content $reportFile "- Permissions: Owned by app in User 95 space.`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 11 — Permissions + URI grants snapshot`n────────────────────────────────────────────`n"
Add-Content $reportFile "Persisted grants query failed with:`njava.lang.IllegalStateException: Could not find provider: com.android.providers.documentsui.permissions`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 12 — FFmpegKit context`n────────────────────────────────────────────`n"
Add-Content $reportFile "- Pinned artifact in gradle/libs.versions.toml: ffmpegKit = `"2.1.0`" (com.antonkarpenko:ffmpeg-kit-min:2.1.0)`n"
Add-Content $reportFile "- The COMPLETE first ENGINE-tagged `"stderr ffmpeg version …`" line: Not present in the captured logcat.`n"
Add-Content $reportFile "- --enable-gpl / --enable-nonfree: Likely absent (using min variant of FFmpegKit which is LGPL).`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 13 — User reproducibility`n────────────────────────────────────────────`n"
Add-Content $reportFile "- Yes, this failed exactly as shown in the previous job trace.`n"
Add-Content $reportFile "- What changed? The URI type passed to FFmpeg changed from saf:N.mkv to content://....`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 14 — Likely cause(s) — ranked`n────────────────────────────────────────────`n"
Add-Content $reportFile "### #1 — C1. Real-path resolution still falling back to content:// for some/all parts`nConfidence: HIGH`nEvidence: concat.txt contains literal content://... strings. Logcat reports Protocol 'content' not on whitelist. Merger.kt calls PathResolver.resolveTreeUriToRealPath() which returns null for DocumentFile URIs that aren't trees.`nWhy this matches THIS run's symptom: The fallback in Merger.kt defaults to returning the partUri, which is passed directly to FFmpeg. FFmpegKit rejects content:// URLs by default as it doesn't understand Android ContentResolvers directly via file protocol.`nWhat a fix would touch: Merger.kt fallback logic, and FFmpegKit virtual SAF pipe configurations.`n"
Add-Content $reportFile "`n### #2 — C2. Path resolution worked but the resolved path is /storage/emulated/95/...`nConfidence: LOW`nEvidence: Contradicted. Path resolution returned content://, not /storage/emulated/95/.`n"
Add-Content $reportFile "`n### #3 — C3. -map 0 -map 0:t? was added to argv but applied AFTER -i`nConfidence: LOW`nEvidence: Contradicted. Merger.kt shows these arguments are completely missing from the constructed cmd.`n"
Add-Content $reportFile "`n### #4 — C4. MergeValidator was added but is bypassed for jobs`nConfidence: LOW`nEvidence: Contradicted. Validation occurred, and passed, because logcat shows ffprobe probe before engine start.`n"

Add-Content $reportFile "`n────────────────────────────────────────────`n# Section 15 — Targeted next-step questions (max 5)`n────────────────────────────────────────────`n"
Add-Content $reportFile "1. Should we restore FFmpegKit's SafParameter.build() mapping for inputs to generate saf:N.mkv pipes instead of attempting to resolve real absolute paths?`n"
Add-Content $reportFile "2. Should we restore the -protocol_whitelist concat,file,crypto,data,saf flag to allow FFmpeg to read those saf:N.mkv descriptors?`n"
Add-Content $reportFile "3. Do you want to update Merger.kt to include the missing canonical argv arguments (-map 0, -map 0:t?, -avoid_negative_ts make_zero, -f matroska)?`n"
Add-Content $reportFile "4. Are you using a dual-app/work profile (User 95) specifically to test this, or is this the primary device state?`n"

Write-Output "REPORT: $reportFile`nPHASE: 6`nSTATUS: blocked-pending-user-review`nNEXT: share the report with the user"
