const fs = require('fs');

const date = new Date();
const timestamp = date.getFullYear() +
    String(date.getMonth() + 1).padStart(2, '0') +
    String(date.getDate()).padStart(2, '0') + '_' +
    String(date.getHours()).padStart(2, '0') +
    String(date.getMinutes()).padStart(2, '0') +
    String(date.getSeconds()).padStart(2, '0');

const reportFile = `logs/merge-failure-2-${timestamp}.md`;

const planContent = fs.readFileSync('AI/plans/2026-06-13-fix-merge-saf-paths.md', 'utf8').trim();
const gitLog = fs.readFileSync('logs/git_log.txt', 'utf8').trim();
const gitStat = fs.readFileSync('logs/git_show_stat.txt', 'utf8').trim();
const gitShow = fs.readFileSync('logs/git_show_full.txt', 'utf8').trim();
const concatTxt = fs.readFileSync('logs/adb_concat.txt', 'utf8').trim();

function getFileContent(path, lang) {
    try {
        const content = fs.readFileSync(path, 'utf8');
        return `\n${path}\n\`\`\`${lang}\n${content}\n\`\`\``;
    } catch (e) {
        return `\n${path}\n(file not present)\n`;
    }
}

const filesToInclude = [
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeListWriter.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/engine/FfmpegEngine.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfmpegEngine.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/StoragePreflight.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolver.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/SafFile.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeValidator.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/service/JobService.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/data/repository/JobsRepository.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/entity/JobEntity.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/entity/PartEntity.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/JobDao.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModel.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeconfig/MergeConfigViewModel.kt", "kotlin"],
    ["app/src/main/kotlin/com/splitandmerge/mkvslice/ui/progress/JobProgressViewModel.kt", "kotlin"],
    ["app/src/test/kotlin/com/splitandmerge/mkvslice/engine/MergerArgvTest.kt", "kotlin"],
    ["app/src/test/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeValidatorTest.kt", "kotlin"],
    ["app/src/test/kotlin/com/splitandmerge/mkvslice/domain/merger/StoragePreflightTest.kt", "kotlin"],
    ["app/src/test/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolverTest.kt", "kotlin"],
    ["app/src/main/AndroidManifest.xml", "xml"],
    ["app/build.gradle.kts", "kotlin"],
    ["gradle/libs.versions.toml", "toml"]
];

let report = `# Section 1 — Plan + commit reality
Plan MD Path: AI/plans/2026-06-13-fix-merge-saf-paths.md

\`\`\`markdown
${planContent}
\`\`\`

git log --oneline -20:
\`\`\`text
${gitLog}
\`\`\`

git show --stat 5ef74cd:
\`\`\`text
${gitStat}
\`\`\`

git show 5ef74cd:
\`\`\`diff
${gitShow}
\`\`\`

Confirming sub-fixes:
1. Real-path resolution: PARTIAL. \`PathResolver.kt\` was added, but \`Merger.kt\` fails to resolve and falls back to \`content://\` URI. (Code: \`com.splitandmerge.mkvslice.platform.saf.PathResolver.resolveTreeUriToRealPath(context, Uri.parse(partUri)) ?: partUri\`)
2. Canonical argv: ABSENT. The argv is missing \`-map 0 -map 0:t? -avoid_negative_ts make_zero -f matroska\`. (Code: \`val cmd = listOf("-hide_banner", "-y", "-f", "concat", "-safe", "0", "-i", concatFile.absolutePath, "-c", "copy", tempOutputFile.absolutePath)\`)
3. MergeValidator added + wired: PRESENT. \`mergeValidator.validate(partUris)\` is called in \`Merger.kt\`.
4. Schema hygiene — manifestPath no longer CSV: PRESENT. \`JobDao\` uses \`getPartsForJob(jobId)\`.
5. Pre-flight storage check on cache volume: PRESENT. \`context.cacheDir.usableSpace < totalSizeRequired\` is checked.
6. (Out of scope)

────────────────────────────────────────────
# Section 2 — Source files (paste in full)
────────────────────────────────────────────
`;

filesToInclude.forEach(file => {
    report += getFileContent(file[0], file[1]);
});

report += `
────────────────────────────────────────────
# Section 3 — Reproduce the failure end-to-end
────────────────────────────────────────────
Source job: 3-part dridam split.
| Part | Path | Size |
|---|---|---|
| 1 | /document/primary:Movies/dridam.part001.mkv | Unknown |
| 2 | /document/primary:Movies/dridam.part002.mkv | Unknown |
| 3 | /document/primary:Movies/dridam.part003.mkv | Unknown |

Logcat block:
\`\`\`text
01 06-13 03:20:46.497 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv (resolved: saf:16.mkv)
02 06-13 03:20:46.675 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part002.mkv (resolved: saf:17.mkv)
03 06-13 03:20:46.850 11234 11976 D ENGINE  : ffprobe probe: content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part003.mkv (resolved: saf:18.mkv)
04 06-13 03:20:47.417 11234 11976 D ENGINE  : start token=923a64ca-faea-49cf-aebc-65f597c5098b args=-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv
05 06-13 03:20:47.418 11234 11976 D ENGINE  : start sessionId=20 token=923a64ca-faea-49cf-aebc-65f597c5098b
06 06-13 03:20:47.422 11234 11979 V ENGINE  : stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!
07 06-13 03:20:47.422 11234 12628 D ENGINE  : done token=923a64ca-faea-49cf-aebc-65f597c5098b exit=1
08 06-13 03:20:47.422 11234 11979 V ENGINE  : stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'
09 06-13 03:20:47.423 11234 11979 V ENGINE  : stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument
\`\`\`

────────────────────────────────────────────
# Section 4 — The exact argv as constructed today
────────────────────────────────────────────
\`start token=923a64ca-faea-49cf-aebc-65f597c5098b args=-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv\`
\`-hide_banner -y -f concat -safe 0 -i /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt -c copy /data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv\`

Tokens:
-hide_banner
-y
-f
concat
-safe
0
-i
/data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt
-c
copy
/data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv

Compared against canonical list:
-hide_banner (PRESENT)
-y (PRESENT)
-f concat (PRESENT)
-safe 0 (PRESENT)
-i <list path> (PRESENT)
-map 0 (MISSING)
-map 0:t? (MISSING)
-c copy (PRESENT)
-avoid_negative_ts make_zero (MISSING)
-f matroska (MISSING)
<output path> (PRESENT)

────────────────────────────────────────────
# Section 5 — concat list as written this run
────────────────────────────────────────────
List file path: \`/data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt\`

\`\`\`text
${concatTxt}
\`\`\`

For each line:
* Pattern: \`file '<path>'\`
* Path is \`content://...\` (URI)
* Path exists from app-private context: No (content URIs are not file paths)
* Path exists from shell context: No
* Is parent readable: N/A
* Non-ASCII/whitespaces: Encoded URI entities present (e.g., %3A).

────────────────────────────────────────────
# Section 6 — FFmpeg stderr (raw)
────────────────────────────────────────────
\`\`\`text
01 ENGINE stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!
02 ENGINE stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'
03 ENGINE stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument
\`\`\`

Matches for fingerprints:
Protocol '<x>' not on whitelist
> \`ENGINE stderr [content @ 0xb4000074539a23d0] Protocol 'content' not on whitelist 'file,crypto,data'!\`

Impossible to open
> \`ENGINE stderr [concat @ 0xb4000074339329f0] Impossible to open 'content://0@com.android.externalstorage.documents/document/primary%3AMovies%2Fdridam.part001.mkv'\`

Invalid argument
> \`ENGINE stderr /data/user/95/com.splitandmerge.mkvslice.debug/cache/concat.txt: Invalid argument\`

────────────────────────────────────────────
# Section 7 — Per-part ffprobe fresh signatures
────────────────────────────────────────────
(Omitted: On-device ffprobe output via FFmpegKit unavailable without intent hook. Probes were successful according to logcat but output details are not printed to logcat.)

────────────────────────────────────────────
# Section 8 — MergeValidator behaviour at runtime
────────────────────────────────────────────
- Was MergeValidator invoked before the engine start? Yes.
- Quote the call site: \`mergeValidator.validate(partUris)\` in \`Merger.kt\`.
- What did it return for the failing parts? It returned successfully (no exception thrown), allowing the engine to proceed to the concat step.
- Was the result surfaced to S10 / S11 / S12 UI? The successful validation allowed the job to transition to RUNNING.

────────────────────────────────────────────
# Section 9 — Storage pre-flight at runtime
────────────────────────────────────────────
- Which volume did the pre-flight measure? cacheDir (\`/data/user/95/com.splitandmerge.mkvslice.debug/cache\`)
- Available bytes reported: Unknown (not logged, but checked via \`context.cacheDir.usableSpace < totalSizeRequired\`).
- Required bytes computed: Unknown (from DocumentFile).
- Did it pass? Yes, otherwise the engine start log would not have occurred.

────────────────────────────────────────────
# Section 10 — Output staging path
────────────────────────────────────────────
- Exact merge_tmp output path used: \`/data/user/95/com.splitandmerge.mkvslice.debug/cache/merge_tmp.mkv\`
- .tmp residue: The code calls \`if (tempOutputFile.exists()) tempOutputFile.delete()\` before running, so there shouldn't be residue.
- Permissions: Owned by app in User 95 space.

────────────────────────────────────────────
# Section 11 — Permissions + URI grants snapshot
────────────────────────────────────────────
Persisted grants query failed with:
\`java.lang.IllegalStateException: Could not find provider: com.android.providers.documentsui.permissions\`

────────────────────────────────────────────
# Section 12 — FFmpegKit context
────────────────────────────────────────────
- Pinned artifact in \`gradle/libs.versions.toml\`: \`ffmpegKit = "2.1.0"\` (\`com.antonkarpenko:ffmpeg-kit-min:2.1.0\`)
- The COMPLETE first ENGINE-tagged "stderr ffmpeg version …" line: Not present in the captured logcat.
- \`--enable-gpl\` / \`--enable-nonfree\`: Likely absent (using \`min\` variant of FFmpegKit which is LGPL).

────────────────────────────────────────────
# Section 13 — User reproducibility
────────────────────────────────────────────
- Yes, this failed exactly as shown in the previous job trace.
- What changed? The URI type passed to FFmpeg changed from \`saf:N.mkv\` to \`content://...\`.

────────────────────────────────────────────
# Section 14 — Likely cause(s) — ranked
────────────────────────────────────────────
### #1 — C1. Real-path resolution still falling back to content:// for some/all parts
Confidence: HIGH
Evidence: \`concat.txt\` contains literal \`content://...\` strings. Logcat reports \`Protocol 'content' not on whitelist\`. \`Merger.kt\` calls \`PathResolver.resolveTreeUriToRealPath()\` which returns null for DocumentFile URIs that aren't trees.
Why this matches THIS run's symptom: The fallback in \`Merger.kt\` defaults to returning the \`partUri\`, which is passed directly to FFmpeg. FFmpegKit rejects \`content://\` URLs by default as it doesn't understand Android ContentResolvers directly via \`file\` protocol.
What a fix would touch: \`Merger.kt\` fallback logic, and FFmpegKit virtual SAF pipe configurations.

### #2 — C2. Path resolution worked but the resolved path is /storage/emulated/95/...
Confidence: LOW
Evidence: Contradicted. Path resolution returned \`content://\`, not \`/storage/emulated/95/\`.

### #3 — C3. -map 0 -map 0:t? was added to argv but applied AFTER -i
Confidence: LOW
Evidence: Contradicted. \`Merger.kt\` shows these arguments are completely missing from the constructed \`cmd\`.

### #4 — C4. MergeValidator was added but is bypassed for jobs
Confidence: LOW
Evidence: Contradicted. Validation occurred, and passed, because logcat shows \`ffprobe probe\` before engine start.

────────────────────────────────────────────
# Section 15 — Targeted next-step questions (max 5)
────────────────────────────────────────────
1. Should we restore FFmpegKit's \`SafParameter.build()\` mapping for inputs to generate \`saf:N.mkv\` pipes instead of attempting to resolve real absolute paths?
2. Should we restore the \`-protocol_whitelist concat,file,crypto,data,saf\` flag to allow FFmpeg to read those \`saf:N.mkv\` descriptors?
3. Do you want to update \`Merger.kt\` to include the missing canonical argv arguments (\`-map 0\`, \`-map 0:t?\`, \`-avoid_negative_ts make_zero\`, \`-f matroska\`)?
4. Are you using a dual-app/work profile (User 95) specifically to test this, or is this the primary device state?

`;

fs.writeFileSync(reportFile, report, 'utf8');
console.log(`REPORT: ${reportFile}\nPHASE: 6\nSTATUS: blocked-pending-user-review\nNEXT: share the report with the user`);
