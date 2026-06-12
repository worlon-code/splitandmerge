# Permissions

> Manifest permissions, when each is requested, and why.

## 1. Declared in `AndroidManifest.xml`

```xml
<!-- Required for any foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Android 14+ specific FGS type for our `dataSync` service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- Android 13+ runtime permission to post notifications -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Keep CPU on during long jobs -->
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- Update check + APK download (HTTPS only) -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Install in-app updates (release variant only) -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- Optional reliability boost on aggressive OEMs (user-triggered from Settings) -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

The `<service>` element:

```xml
<service
    android:name=".service.JobService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

## 2. NOT declared (and why)

| Permission | Reason for omission |
|---|---|
| `MANAGE_EXTERNAL_STORAGE` | Play Store policy red flag. We use SAF, which doesn't need it. |
| `READ_MEDIA_VIDEO` | We don't enumerate the media store; SAF is the only path. |
| `READ_EXTERNAL_STORAGE` | Same — SAF supersedes it on Android 11+. |
| `WRITE_EXTERNAL_STORAGE` | Same. |
| `RECORD_AUDIO`, `CAMERA` | We don't capture media. |
| `BLUETOOTH*` | No bluetooth flow. |
| `LOCATION` | Never. |
| `CONTACTS`, `CALENDAR` | Never. |
| `RECEIVE_BOOT_COMPLETED` | We don't auto-start. Jobs require explicit user kick-off. |
| `SCHEDULE_EXACT_ALARM` | We don't schedule. |
| `SYSTEM_ALERT_WINDOW` | We don't draw over other apps. |
| `INSTALL_PACKAGES` (the privileged one) | We use `REQUEST_INSTALL_PACKAGES` + `PackageInstaller` which is the user-allowed flow. |

## 3. Runtime request flow

| Permission | When prompted | Required for | UX |
|---|---|---|---|
| `POST_NOTIFICATIONS` | First time we start a job (S6 → S7) | Job progress notification | Standard system dialog. If denied, jobs still run; no notification. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | User taps "Improve reliability" in Settings | Better survival on Xiaomi/OnePlus/Huawei/Realme | Brief explainer sheet → system intent. Off by default. |
| `REQUEST_INSTALL_PACKAGES` | User taps "Install update" after download | Install in-app update | System intent; user grants once. |

SAF folder grant is **not** a runtime permission in the manifest sense —
it's a per-URI grant from `ACTION_OPEN_DOCUMENT_TREE`. We persist it via
`contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`.

## 4. Permission policy matrix

| Layer | May request runtime perms? | Notes |
|---|---|---|
| UI / Compose | ✅ | Via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`. |
| ViewModel | ❌ | No Context, no permission API. |
| Domain | ❌ | Pure Kotlin. |
| Engine | ❌ | Internal; assumes everything granted. |
| Service | ❌ | Reads granted permissions only. Throws clear error if missing. |
| Update | ✅ | `PackageInstaller` flow. |

## 5. Where each permission is actually consumed

```
FOREGROUND_SERVICE         → service/JobService (always)
FOREGROUND_SERVICE_DATA_SYNC → service/JobService (A14+)
POST_NOTIFICATIONS         → service/JobNotificationFactory + ui/MainActivity (request)
WAKE_LOCK                  → service/JobService (acquire / release)
INTERNET, ACCESS_NETWORK_STATE → update/UpdateService (Retrofit)
REQUEST_INSTALL_PACKAGES   → update/ApkInstaller (PackageInstaller)
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS → ui/settings/ReliabilityRow
```

## 6. Manifest configuration extras

```xml
<application
    android:name=".App"
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="false"
    android:label="@string/app_name"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:enableOnBackInvokedCallback="true"
    android:theme="@style/Theme.VideoSplitter">
```

| Attribute | Why |
|---|---|
| `allowBackup="false"` | SAF URI permissions don't survive Auto Backup restore — would create ghost jobs. |
| `dataExtractionRules` | Empty; no data is backed up. |
| `enableOnBackInvokedCallback` | Predictive back gestures on Android 14+. |

`<intent-filter>` set:

```xml
<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Optional: open a video file from another app (S4 entry point) -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="video/x-matroska" />
        <data android:mimeType="video/mp4" />
        <data android:mimeType="video/webm" />
        <data android:mimeType="video/x-msvideo" />
        <data android:mimeType="video/quicktime" />
        <data android:mimeType="video/mp2t" />
    </intent-filter>
</activity>
```

## 7. Test coverage

In `app/src/androidTest/.../PermissionsTest.kt`:

- `manifestDoesNotDeclareForbiddenPermissions()` — fails if any of the
  forbidden list in §2 appears.
- `foregroundServiceTypeIsDataSync()` — assert the `<service>` element.
- `notificationPermissionRequestedOnFirstJob()` — UI test, runs on A13+ only.

## 8. What an agent must NOT do here

- ❌ Add `MANAGE_EXTERNAL_STORAGE`. Period.
- ❌ Add `READ_MEDIA_VIDEO` "for convenience" — SAF is the contract.
- ❌ Auto-request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` on launch. User-
  triggered only.
- ❌ Catch `SecurityException` to swallow a missing permission. Surface it.
- ❌ Use `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` without also calling
  `takePersistableUriPermission`.
- ❌ Set `allowBackup="true"`.
