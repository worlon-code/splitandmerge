# Phase 4: Engine — Probe & Keyframe Find

Status: AWAITING APPROVAL

## Problem
In Phase 4, we must establish the core FFmpeg/FFprobe engines to read media file metadata and identify keyframe timestamp lists from video files. These are critical prerequisites for the split algorithm. We will also implement the progress parsing logic and verify the native binaries via a smoke test.

## Proposed change
1. **Engine Interfaces**: Create `FfmpegEngine.kt`, `FfprobeEngine.kt`, and `EngineEvent` classes in a new `engine/` package.
2. **Library Integration**: Integrate `ffmpeg-kit-video` library using Jitpack's maintained community fork `com.github.tanersener.ffmpeg-kit:ffmpeg-kit-video:2.1.0`.
3. **Ffprobe Implementation**: Create `ProcessFfprobeEngine.kt` wrapping `FFprobeKit` command executions to retrieve JSON metadata and frame packet timestamps.
4. **Ffmpeg Implementation**: Create `ProcessFfmpegEngine.kt` wrapping `FFmpegKit` command executions with progress callbacks and cancellation tokens.
5. **Progress Parser**: Create `ProgressParser.kt` to extract time and speed from FFmpeg console logs.
6. **Domain planning helper**: Create `CutPlanner.kt` to plan the splits based on target caps and keyframe indices (JVM-only).
7. **Testing**:
   - JVM Unit tests for `ProgressParser` and `CutPlanner` to ensure correctness without running native processes.
   - Instrumented Android test (`EngineSmokeTest`) running a simple `-version` check and a short lossless stream-copy on a real test fixture file.

## Files touched (paths + intent)
- [NEW] [FfmpegEngine.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/engine/FfmpegEngine.kt): Interface for executing FFmpeg tasks.
- [NEW] [FfprobeEngine.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/engine/FfprobeEngine.kt): Interface for probing media files and extracting keyframe timestamps.
- [NEW] [ProgressParser.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/engine/ProgressParser.kt): Pure Kotlin parser for log strings.
- [NEW] [ProcessFfmpegEngine.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfmpegEngine.kt): FFmpeg execution wrapper using FFmpegKit.
- [NEW] [ProcessFfprobeEngine.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfprobeEngine.kt): FFprobe execution wrapper using FFmpegKit.
- [NEW] [CutPlanner.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/splitter/CutPlanner.kt): Pure Kotlin split scheduler logic.
- [NEW] [ProgressParserTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/engine/ProgressParserTest.kt): Unit tests for parser.
- [NEW] [CutPlannerTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/domain/splitter/CutPlannerTest.kt): Unit tests for split planning logic.
- [NEW] [EngineSmokeTest.kt](file:///d:/Repos/splitandmerge/app/src/androidTest/kotlin/com/splitandmerge/mkvslice/engine/EngineSmokeTest.kt): Instrumented integration test for native binaries on device.

## Tests added/updated
- JVM unit tests verifying log parsing under `app/src/test`.
- JVM unit tests verifying split scheduler boundary logic (EXACT_PARTS, SIZE_CAP_ONLY, BOTH).
- Instrumented test verifying FFmpeg version check and lossless copy of a mock/fixture asset file.

## Migration notes
None.

## Rollback plan
Revert new files under `engine/` package and `domain/splitter/CutPlanner.kt`.

## Open questions
None.
