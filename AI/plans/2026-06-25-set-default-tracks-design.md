# Set Default Tracks — Phase 0 Engineering Design

**Date:** 2026-06-25 | **Author:** Antigravity | **Status:** READY FOR REVIEWER APPROVAL (EVIDENCE COMPLETED)

---

## Problem

MKV files in a season folder often have the wrong default audio or subtitle flag set —
either because the rip tool didn't honour the user's preference, or because there is no
default flag at all. The user must manually change the default track in their player for
every episode.

MKV Slice already owns the SAF URI to these files and understands their structure.
Adding "Set Default Tracks" gives the user a one-tap batch fix with a safety model stronger
than any desktop tool — journaled, verified, fail-closed, no re-encode, no re-mux.

---

## User Review Required

> [!IMPORTANT]
> This document resolves all Phase 0 open questions empirically with on-device test evidence, hex dumps, and library details. No production code changes have been checked in yet.

> [!WARNING]
> **Git Status & v0.0.12 Work:** Git is READ-ONLY for Phase 0. The Room schema and database changes are designed against the current local v0.0.12 workspace tree.

---

## Phase 0 Investigation Findings & Evidence

### 1.1 FFprobeKit Dependency & Class Presence
* **Coordinates:** `com.antonkarpenko:ffmpeg-kit-min:2.1.0` (LGPL 3.0 license)
* **FFprobeKit Bundled:** **YES**.
* **Exposed Classes & Properties:**
  Inspecting the compiled `classes.jar` inside the AAR confirms that the following classes are fully exposed:
  - `com.antonkarpenko.ffmpegkit.FFprobeKit`
  - `com.antonkarpenko.ffmpegkit.MediaInformation`
  - `com.antonkarpenko.ffmpegkit.StreamInformation`
  - `com.antonkarpenko.ffmpegkit.Chapter`
  
  `StreamInformation` exposes the following API:
  - `getIndex()`: Long
  - `getType()`: String (e.g., `"audio"`, `"video"`, `"subtitle"`)
  - `getCodec()`: String
  - `getTags()`: `org.json.JSONObject` (contains BCP-47 track language under key `"language"` and track title under key `"title"`)
  - `getProperty(String)`: `org.json.JSONObject` / `getNumberProperty` / `getStringProperty`. Calling `getProperty("disposition")` retrieves the track's disposition properties (e.g., `default`, `forced`) as a nested JSON object.

---

### 1.2 SAF "rw" Arbitrary-Offset Write Feasibility
On-device instrumented tests were executed on a Samsung Galaxy M51 (Android 12) under two URI schemes:
1. **Local File Scheme (`file://`):**
   - **Result:** **WORKS**. The byte at offset 16 was successfully modified and synchronized via `Os.fdatasync()`. File size remained exactly unchanged.
2. **System SAF Tree Provider (`content://com.android.externalstorage.documents/...`):**
   - **Result:** **WORKS**. Using a persisted tree URI to create a scratch file, copying content into it, and opening it via `context.contentResolver.openFileDescriptor(uri, "rw")` allows seeking to offset 16, writing the flipped byte, executing `Os.fdatasync()`, and closing. Upon re-opening and reading, the byte matches the modified value and the file size is verified to be identical down to the byte.
   - **Risk Assessment:** No truncation or write rejections were encountered on primary emulated storage. The surgically targeted in-place byte editing is fully supported.

---

### 1.3 Annotated Hex-Dump Evidence from `dridam.mkv`

Below is the verified layout of elements parsed directly from the sample file `dridam.mkv` (size `4,831,251,051` bytes):

#### (a) TrackEntry with Explicit FlagDefault
* **TrackEntry 2 Offset:** `0x11E8` (4584)
* **FlagDefault Element:** Located at absolute offset `0x11FB` (4603).
* **Bytes (3B):** `88 81 00`
* **Meaning:**
  - `88`: FlagDefault ID (1-byte EBML ID)
  - `81`: VINT size width 1, value 1 (payload length = 1 byte)
  - `00`: Payload value `0` (FlagDefault is explicitly set to false/0)

#### (b) TrackEntry with FlagDefault Absent
* **TrackEntry 0 Offset:** `0x10DD` (4317)
* **Children present:** `TrackNumber` (`D7`), `TrackType` (`83`), `Language` (`22B59C`), and codec headers.
* **FlagDefault:** **ABSENT** (means the default flag defaults to the Matroska spec implied value of `1`).

#### (c) EbmlVoid Element
* **Void Offset:** `0x7B` (123)
* **Total element size:** 4028 bytes.
* **Bytes (ID + Size VINT + Payload excerpt):** `EC 4F B9 00 00 00 00 00 00 00 00 00 00...`
* **Meaning:**
  - `EC`: Void ID (1-byte EBML ID)
  - `4F B9`: VINT size width 2, value `0x0FB9` (4025 bytes payload length)
  - `4025` bytes of zero padding follow the header.

#### (d) SeekHead Element
* **SeekHead Offset:** `0x34` (52)
* **Size:** 66 bytes (`0x42` payload size)
* **Bytes:** `11 4D 9B 74 C2 4D BB 8C 53 AB 84 15 49 A9 66 53 AC 82 10 03...`
* **Meaning:**
  - `11 4D 9B 74`: SeekHead ID (4-byte EBML ID)
  - `C2`: VINT size width 1, value `0x42` (66 bytes payload)
  - `4D BB`: Seek element
  - `53 AB`: SeekID (`15 49 A9 66` = Info ID)
  - `53 AC`: SeekPosition (`82 10 03` = offset value `1003` bytes)

#### (e) Cues Element with CueClusterPosition
* **Cues Offset:** `0x11FF56EFE` (4831145726)
* **First CueClusterPosition Element:** Located at absolute offset `0x11FF56F0F` (4831145743)
* **Bytes:** `F1 82 1B 4E`
* **Meaning:**
  - `F1`: CueClusterPosition ID (1-byte EBML ID)
  - `82`: VINT size width 1, value 2 (payload length = 2 bytes)
  - `1B 4E`: Payload value `0x1B4E` (offset `7000` bytes)
  - **Stored value width:** The value itself is stored in exactly 2 bytes.

---

### 1.4 Matroska Structural Distances & VINT Widths
* **Tracks Offset:** `0x10D7` (4311)
* **First Cluster Offset:** `0x1B82` (7042)
* **Relative Position:** Tracks (`4311`) is located before the first Cluster (`7042`).
* **Byte Distance:** `2,731` bytes.
* **Segment Size VINT Width:** 8 bytes.
* **SeekPosition VINT Width (in SeekHead):** 1 byte for size VINT, value payload size is 2 bytes (for offsets < 65KB) or 5 bytes (for offsets > 4GB).
* **CueClusterPosition VINT Width (in Cues):** 1 byte for size VINT, value payload size is 2 bytes (for offsets < 65KB) or 5 bytes (for offsets > 4GB).

---

## Design Refinements & Product Decisions

### 2.1 FlagVerifier Autonomy
* The primary post-write verification will be performed by the engine's own `EbmlReader` to parse and assert the updated track layout.
* `FFprobeKit` is an optional cross-check and will only run if it successfully initializes. The engine's parser remains the standalone source of truth for validation success.

### 2.2 Job Persistence Consolidation
To align with the existing queue and job pipeline, we will **NOT** create a parallel `default_track_jobs` table. Instead, a batch operation is persisted as a row in the existing `jobs` table (`JobEntity`), tagged with `JobType.SET_DEFAULT_TRACKS`.
* Per-file results inside the batch are persisted in a new child table `default_track_file_results` referencing `JobEntity(id)` via a Cascade Delete Foreign Key.
* The Room database version will be bumped to `6` to introduce this single child table and clean up versioning.

### 2.3 Journal & TOCTOU Verification
* **Content Signature:** The engine records a signature covering the exact target bytes of the region to be modified, not the first/last 64MB of the file.
* **Atomic Read-Write Gate:** The engine opens the file descriptor in `"rw"` mode only once. Right before performing the seek-write operation, it reads the target region bytes and aborts/rolls back the transaction if the read bytes mismatch the recorded signature. This eliminates time-of-check to time-of-use (TOCTOU) file modification issues.

### 2.4 Unbounded Tracks Location
* We do not enforce a hard 4MB limit for locating the Tracks element. The `EbmlReader` will continuously parse child elements of the Segment until it reaches the first Cluster. If the Tracks element is not found before the first Cluster, the file is skipped with `SKIPPED(reason=tracks-not-before-cluster)`.

### 2.5 Room Default Value Upgrades
* The Room `defaultValue` upgrade hazard refers to the pattern of adding `NOT NULL` columns to database tables in migrations without specifying a default value, leading to SQLite exceptions. All columns in the new `default_track_file_results` table will be declared with explicit `@ColumnInfo(defaultValue = ...)` configurations.

### 3.1 Path C Deferral
* Path C (tail-rewrites for files lacking an inline `EbmlVoid` to insert elements) is deferred indefinitely. If a file cannot be patched via Path A or Path B, it is skipped with `SKIPPED(reason=no-void-for-insert)`. The engine logs these events to evaluate the skip frequency.

### 3.2 Orphan Journal Recovery
* Rollbacks are not performed silently on app launch. Any leftover `.journal` files in the app cache folder are surfaced via a dialog/notification on launch, prompting the user to approve the rollback.

### 3.3 Safety-Bounded Folder Recursion
* When the user selects a folder, the scanner will recursively inspect subfolders. However, to prevent ANRs or memory issues, the scan is capped at a depth of 5 levels and a maximum total file count of 1,000. The UI shows a live count of files found during "Analyzing...".

### 3.4 Explicit Non-MKV Reporting
* Files that do not have the Matroska magic header are not silently omitted. They are reported on the results screen under the SKIPPED group with `reason=not-mkv`, ensuring that the total file count in the selected folder reconciles.

---

## Proposed Changes

### Domain & Engine

New package: `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/`

#### [NEW] [EbmlReader.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/EbmlReader.kt)
Forward-only EBML parser using `Os.pread` on seekable `FileDescriptor`.

#### [NEW] [TrackAnalyser.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/TrackAnalyser.kt)
Walks the segment tree to construct track structures and list voids.

#### [NEW] [FlagEditor.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/FlagEditor.kt)
Prepares editing decisions using Path A and Path B.

#### [NEW] [FlagJournal.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/FlagJournal.kt)
Pre-image journal writing/restoring.

#### [NEW] [FlagVerifier.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/FlagVerifier.kt)
Runs byte-diff and optional FFprobeKit verification.

#### [NEW] [DefaultTracksEngine.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/DefaultTracksEngine.kt)
Orchestrates file operations.

#### [NEW] [LanguageNormaliser.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/LanguageNormaliser.kt)
ISO 639 standard mappings.

---

### Database Schema (Room v5 → v6)

#### [NEW] [DefaultTrackFileResultEntity.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/entity/DefaultTrackFileResultEntity.kt)
```kotlin
@Entity(
    tableName = "default_track_file_results",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class DefaultTrackFileResultEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val uri: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "UNKNOWN") val status: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "") val reason: String = "",
    @ColumnInfo(defaultValue = "SKIPPED") val writeStrategy: String = "SKIPPED",
    @ColumnInfo(defaultValue = "") val appliedSpecJson: String = "",
    val createdAt: Long
)
```

#### [NEW] [Migration_5_6.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/migrations/Migration_5_6.kt)
```sql
CREATE TABLE IF NOT EXISTS `default_track_file_results` (
    `id` TEXT NOT NULL, 
    `jobId` TEXT NOT NULL, 
    `uri` TEXT NOT NULL, 
    `displayName` TEXT NOT NULL, 
    `status` TEXT NOT NULL DEFAULT 'UNKNOWN', 
    `reason` TEXT NOT NULL DEFAULT '', 
    `writeStrategy` TEXT NOT NULL DEFAULT 'SKIPPED', 
    `appliedSpecJson` TEXT NOT NULL DEFAULT '', 
    `createdAt` INTEGER NOT NULL, 
    PRIMARY KEY(`id`), 
    FOREIGN KEY(`jobId`) REFERENCES `jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_default_track_file_results_jobId` ON `default_track_file_results` (`jobId`);
```

#### [MODIFY] [AppDatabase.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/AppDatabase.kt)
Increment version to `6`, add `DefaultTrackFileResultEntity::class` to entities, and define its corresponding DAO.

#### [MODIFY] [JobType.kt](file:///D:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/model/JobType.kt)
Add `SET_DEFAULT_TRACKS` to the job type enum.

---

## Verification Plan

### Automated
1. `./gradlew lintDebug`
2. `./gradlew testDebugUnitTest`
3. `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.splitandmerge.mkvslice.data.db.migrations.Migration_5_6Test`

### Manual
* Deploy debug build, navigate to "Set default tracks", select a folder containing `.mkv` and non-`.mkv` files, verify that scanning lists correct counts and successfully patches selected files.
