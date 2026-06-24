# v0.0.11 — Work Summary

## Goal
Implement a testability abstraction for disk file system operations (`FileSystem` interface) to decouple the `Merger` domain logic from direct file I/O, allowing complete unit test mockability without relying on `mockkConstructor` or faking path lookups. Additionally, unblock the instrumentation tests (`androidTest`) compilation that was broken since v0.0.10.

## Scope decisions
- **Untouched read/write paths**: Scoped-storage reads, DocumentFile, SAF, and ParcelFileDescriptor operations remain as-is.
- **Out of scope for FileSystem seam**: The Timber debug-log `concatFile.readText()` and `MergeListWriter.writeSafList(...)` operations were deliberately left off the `FileSystem` seam to keep the change focused.
- **Seam bounds**: The seam is restricted exactly to the 8 `File`-typed methods of the `FileSystem` interface (`cacheDir`, `exists`, `canRead`, `length`, `openInput`, `openOutput`, `createNewFile`, `delete`).

## What shipped
- **FileSystem seam**:
  - `FileSystem` interface containing exactly 8 `File`-typed methods.
  - `RealFileSystem` implementation delegating to `java.io.File`.
  - `FileSystemModule` binding `RealFileSystem` to `FileSystem` as a `@Singleton` in `SingletonComponent` using Hilt.
  - Plumbed `FileSystem` into the `Merger` constructor as the 7th parameter and migrated all in-scope file I/O operations to use it.
- **Unit test migrations**:
  - Migrated `MergerFastPathTest` (4 cases), `MergerArgvTest`, and `MergerCollisionTest` to a mocked `FileSystem` using `mockk` (backed by a real JUnit `TemporaryFolder.root` for cacheDir).
  - Added `RealFileSystemTest` to fully cover the real file system delegation.
- **Android test fixes**:
  - `SafLifecycleTest` updated to use `RealFileSystem(context)` and a relaxed mock of `SettingsRepository`.
  - `MergerSequentialCleanupTest` updated to use `@Inject` for both `FileSystem` and `SettingsRepository` via Hilt.

## What was tried and abandoned
- **Faking cacheDir path**: Initially faked `cacheDir()` to return a non-existent directory (`/test-cache`) in tests. Abandoned because Timber's debug-log and `MergeListWriter` require a real, writable directory structure even when the rest of the file operations are mocked. Reverted to backing the mock cacheDir with a real temporary directory on disk.
- **Expanding seam scope**: Considered adding `MergeListWriter` and `Timber` file reading under the seam. Abandoned to prevent scope creep, sticking strictly to the 8 `File`-typed methods.

## Issues caught in review
- **MockK evaluation order**: Mocked `every { exists(any()) }` could match too broadly if placed after specific mock setup. Ensured correct registration sequence and argument validation.
- **StoragePreflight space query**: Noticed that `usableSpace` queries on disk cannot be easily mocked if the parent directories do not exist on the file system. Addressed by ensuring test directories exist on disk (`mkdirs()`).
- **Android Test compile break**: Instrumentation tests were silently broken since v0.0.10 because they weren't updated when `SettingsRepository` was added to `Merger`'s constructor. Identified and fixed under K-024.

## Verification summary
All step-4 verification builds were executed successfully on the Windows host and are fully green:
- `:app:assembleDebug` — GREEN
- `:app:lintDebug` — GREEN
- `:app:testDebugUnitTest` — GREEN
- `:app:assembleRelease` — GREEN
- `compileDebugAndroidTestKotlin` — GREEN

## Caveats
- PENDING (v0.0.12 decision, NOT this release): with the FileSystem seam now landed, decide whether the disk fast-path stays as conditional code or is removed (it is effectively a no-op on targetSdk=35 SAF-only devices). Not part of the v0.0.11 changeset.

## SECTION F — TERMS & EXAMPLES

### 1. FileSystem Seam
* **What it is**: An architectural abstraction that wraps direct file system calls (like `exists()`, `delete()`, etc.) behind an interface, allowing unit tests to mock out disk behaviors without creating real files or mocking constructors.
* **Example from this run**: The `FileSystem` interface introduced in `platform/io/` containing 8 `File`-typed methods.
* **Why it mattered for v0.0.11**: It replaces JVM-wide `mockkConstructor(FileInputStream::class)` hacks and temporary folder side-effects, making `Merger` completely testable in isolation.
