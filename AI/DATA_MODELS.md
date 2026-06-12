# Data Models

> Domain models (pure Kotlin) and persistence schema (Room).

## 1. Domain models (no Android imports)

### 1.1 Source file metadata (after probe)

```kotlin
data class ProbeResult(
    val sourceUri: String,
    val originalFilename: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val containerLabel: String,         // e.g. "Matroska (.mkv)"
    val containerExtension: String,     // "mkv" | "mp4" | "avi" | …
    val avgBitrate: Long,               // bits / sec
    val video: VideoStream,
    val audio: List<AudioStream>,
    val subtitles: List<SubtitleStream>,
    val chapters: Int,
    val attachments: Int,               // count of font files
)

data class VideoStream(
    val codec: String,                  // "hevc", "h264", "av1", …
    val codecLong: String,              // "HEVC (H.265 Main 10)"
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val pixFmt: String,                 // "yuv420p10le", …
    val bitDepth: Int,                  // 8 | 10 | 12
    val hdr: HdrType,                   // SDR, HDR10, HDR10Plus, DolbyVision
    val bitrate: Long,
)

data class AudioStream(
    val index: Int,                     // 0-based across audio streams
    val codec: String,                  // "dts", "eac3", "aac", …
    val codecLong: String,
    val language: String?,              // BCP-47 tag if present
    val channels: Int,                  // 2 | 6 | 8
    val sampleRate: Int,
    val bitrate: Long,
    val isDefault: Boolean,
)

data class SubtitleStream(
    val index: Int,                     // 0-based across subtitle streams
    val codec: String,                  // "ass", "subrip", "hdmv_pgs_subtitle", "dvd_subtitle", "webvtt"
    val language: String?,
    val isDefault: Boolean,
    val isBitmap: Boolean,              // PGS / VobSub
)

enum class HdrType { SDR, HDR10, HDR10Plus, DolbyVision }
```

### 1.2 Cleaned title

```kotlin
data class CleanedTitle(
    val raw: String,
    val title: String,                  // "Baahubali The Epic (2025)"
    val folder: String,                 // "Baahubali The Epic (2025)"  (no trailing slash)
    val partTemplate: String,           // "Baahubali The Epic (2025).part%03d.%s"
    val year: Int?,                     // 2025 if detected
    val tracedRules: List<TraceStep>,   // for live preview UI
)

data class TraceStep(
    val ruleId: String,
    val ruleLabel: String,
    val before: String,
    val after: String,
    val matched: Boolean,
)
```

### 1.3 Cut plan

```kotlin
data class CutPlan(
    val mode: SplitMode,                // EXACT_PARTS | SIZE_CAP_ONLY | BOTH
    val requestedParts: Int?,           // null when SIZE_CAP_ONLY
    val targetCapBytes: Long,           // 9 GB (target)
    val ceilingCapBytes: Long,          // 9.5 GB (never exceed)
    val cuts: List<Double>,             // seconds, ascending; size = parts - 1
    val expectedPartCount: Int,
    val expectedPartSizes: List<Long>,
    val expectedTotalDuration: Double,
)

enum class SplitMode { EXACT_PARTS, SIZE_CAP_ONLY, BOTH }
```

### 1.4 Job + part

```kotlin
data class SplitJob(
    val id: String,                     // UUID
    val title: String,                  // cleaned title
    val sourceUri: String,
    val outputDirUri: String,
    val plan: CutPlan,
    val container: String,              // ".mkv" | ".mp4" | …
    val streams: ProbeResult,
)

data class MergeJob(
    val id: String,
    val title: String,
    val partsInOrder: List<PartRef>,
    val outputUri: String,
    val outputContainer: String,
)

data class PartRef(
    val index: Int,
    val uri: String,
    val sizeBytes: Long,
    val codecSignature: String,         // for pre-merge validation
)
```

### 1.5 Manifest (`<base>.split.json`)

Schema version 1. Serialised with `kotlinx.serialization`.

```kotlin
@Serializable
data class Manifest(
    val schema: Int = 1,
    val source: ManifestSource,
    val parts: List<ManifestPart>,
    val ffmpegVersion: String,
    val appVersion: String,
)

@Serializable
data class ManifestSource(
    val name: String,
    val size: Long,
    val durationSeconds: Double,
    val sha256First64MB: String,
    val video: ManifestVideo,
    val audio: List<ManifestAudio>,
    val subs: List<ManifestSub>,
)

@Serializable
data class ManifestVideo(val codec: String, val width: Int, val height: Int, val hdr: String)

@Serializable
data class ManifestAudio(val codec: String, val lang: String? = null)

@Serializable
data class ManifestSub(val codec: String, val lang: String? = null)

@Serializable
data class ManifestPart(
    val index: Int,
    val name: String,
    val start: Double,
    val end: Double,
    val size: Long,
)
```

## 2. Persistence (Room)

Single `@Database`. Type converters for `Uri → String`, `Long → Long`, enums
as strings.

### 2.1 jobs table

```kotlin
@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val type: JobType,                  // SPLIT | MERGE
    val createdAt: Long,                // epoch millis
    val updatedAt: Long,
    val status: JobStatus,              // QUEUED, RUNNING, DONE, FAILED, CANCELLED
    val progressPct: Int,               // 0..100
    val errorMessage: String? = null,
    val sourceUri: String,              // SAF URI
    val outputDirUri: String,           // tree URI
    val outputBaseName: String,
    val outputContainer: String,        // ".mkv" | ".mp4" | …
    val mode: SplitMode? = null,        // null for MERGE
    val requestedParts: Int? = null,
    val targetCapBytes: Long? = null,
    val ceilingCapBytes: Long? = null,
    val manifestPath: String? = null,
)

enum class JobType { SPLIT, MERGE }
enum class JobStatus { QUEUED, RUNNING, DONE, FAILED, CANCELLED }
```

### 2.2 parts table

```kotlin
@Entity(
    tableName = "parts",
    foreignKeys = [ForeignKey(entity = JobEntity::class, parentColumns = ["id"],
                              childColumns = ["jobId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("jobId")]
)
data class PartEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val index: Int,                     // 1-based for display
    val name: String,
    val startSec: Double,
    val endSec: Double,
    val sizeBytes: Long? = null,        // null until written
    val sha256: String? = null,         // null in v1 (computed on demand)
    val status: PartStatus,
)

enum class PartStatus { PENDING, RUNNING, DONE, FAILED }
```

### 2.3 cleanup_patterns table

```kotlin
@Entity(tableName = "cleanup_patterns")
data class CleanupPatternEntity(
    @PrimaryKey val id: String,         // UUID
    val regex: String,                  // Java regex
    val replacement: String,            // usually ""
    val enabled: Boolean,
    val isBuiltIn: Boolean,             // built-ins can't be deleted
    val orderIndex: Int,                // applied in ascending order
    val label: String,                  // e.g. "Strip resolution tokens"
    val createdAt: Long,
)
```

The 12 built-ins are seeded by `AppDatabase.Callback.onCreate()` from a JSON
asset: `app/src/main/assets/cleanup-builtins.json`.

### 2.4 settings (DataStore Preferences, NOT Room)

Stored under `com.splitandmerge.mkvslice.preferences_pb`:

| Key | Type | Default | Notes |
|---|---|---|---|
| `theme` | string | `system` | `light` / `dark` / `amoled` / `system` |
| `dynamic_color` | bool | `true` | Android 12+ only |
| `default_size_cap_bytes` | long | `9_663_676_416` | 9 GB |
| `default_ceiling_bytes` | long | `10_200_547_328` | 9.5 GB |
| `default_output_dir_uri` | string | `""` | tree URI |
| `match_input_container` | bool | `true` | auto-promote MKV when subs need it |
| `show_cleanup_preview` | bool | `true` | D1 dialog enabled |
| `improve_reliability_battery` | bool | `false` | Settings → Reliability opt-in |
| `keep_screen_on` | bool | `false` | progress screen flag |
| `last_update_check_at` | long | `0` | epoch millis |
| `last_known_version` | string | `""` | from update.json |

### 2.5 Migrations

- Migrations live in `data/db/migrations/`. Always provided; never destroy
  and recreate.
- Migration tests: one test per `Migration_<from>_<to>` in
  `app/src/androidTest/.../MigrationTest.kt`.

## 3. JSON shapes

### 3.1 `videosplitter-version.json` (in releases repo)

```json
{
  "latest": "0.0.1",
  "versionCode": 1,
  "url": "https://github.com/splitandmerge/mkvslice-releases/raw/main/v0.0.1/app-release.apk",
  "size": 38_765_432,
  "sha256": "ab12cd34…",
  "minSdk": 26,
  "publishedAt": "2026-06-12T10:00:00Z",
  "changelog": "## [0.0.1] — 2026-06-12\n🚀 NEW FEATURES\n- Lossless split…\n"
}
```

Parsed with `kotlinx.serialization` into `VersionInfo`. See [API_USAGE.md](API_USAGE.md).

### 3.2 `<base>.split.json` (manifest beside parts)

See `Manifest` in §1.5.

## 4. Type converters

```kotlin
class Converters {
    @TypeConverter fun fromUri(uri: Uri?): String? = uri?.toString()
    @TypeConverter fun toUri(s: String?): Uri? = s?.let(Uri::parse)
    @TypeConverter fun fromStatus(v: JobStatus): String = v.name
    @TypeConverter fun toStatus(v: String): JobStatus = JobStatus.valueOf(v)
    // … one pair per enum
}
```

## 5. DAOs (excerpt)

```kotlin
@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(status: JobStatus = JobStatus.QUEUED): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Query("UPDATE jobs SET status = :status, progressPct = :pct, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, status: JobStatus, pct: Int, now: Long)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

## 6. Validation rules

- `JobEntity.outputContainer` must start with `.` (`.mkv`, `.mp4`).
- `PartEntity.startSec < endSec` always.
- `targetCapBytes ≤ ceilingCapBytes` enforced on insert.
- `requestedParts ≥ 2` when `mode == EXACT_PARTS` or `BOTH`.
- `CleanupPatternEntity.orderIndex` is contiguous within `isBuiltIn=true`
  set; user-added rules append after the highest built-in index.

## 7. Backups

Room DB is **not** backed up via Auto Backup (`android:allowBackup="false"`)
because:

1. Persistent SAF URI permissions don't survive a restore — would create
   ghost jobs.
2. We rely on the source file still existing locally to reproduce a job.

User can export jobs as JSON (manual; v1.x feature, not v1).
