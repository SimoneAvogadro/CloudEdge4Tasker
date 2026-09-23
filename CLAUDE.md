# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CloudEdge4Tasker is an Android plugin app that integrates CloudEdge/Meari battery cameras with automation platforms like Tasker and MacroDroid. The app enables camera control and alarm management through Tasker actions and events.

## Build System & Commands

### Building the Project
```bash
./gradlew build                    # Build the entire project
./gradlew assembleDebug           # Build debug APK
./gradlew assembleRelease         # Build release APK
./gradlew clean                   # Clean build artifacts
```

### Testing
```bash
./gradlew test                    # Run unit tests
./gradlew connectedAndroidTest    # Run instrumented tests
```

### Installation
```bash
./gradlew installDebug            # Install debug APK to connected device
./gradlew installRelease          # Install release APK to connected device
```

### Build Configuration
- Gradle Plugin: 8.2.2
- Kotlin: 1.9.22
- compileSdkVersion: 34 (Android 14)
- minSdkVersion: 24 (Android 7.0)
- targetSdkVersion: 33 (Android 13)
- Architecture: armeabi-v7a, arm64-v8a
- viewBinding: enabled

### Line endings (LF everywhere)
The working tree lives on a Windows drive shared between Windows git (`core.autocrlf=true`) and
WSL git (`core.autocrlf=false`). `.gitattributes` enforces LF in the repo and on disk for every
text file (`* text=auto eol=lf`; only `*.bat` are CRLF) so both clients agree regardless of their
`core.autocrlf`. Rules:
- Never commit CRLF text files and never add `core.autocrlf`/`core.eol` overrides: the attributes file is the single source of truth
- If `git status` ever lists dozens of files with whitespace-only diffs (`git diff --ignore-all-space --stat` shows 0 real changes), it is a line-ending drift: fix with `git add --renormalize .`, not by committing the noise
- New file types that are binary (e.g. new archive/media extensions) must be added to `.gitattributes` as `binary`

## Architecture Overview

### Package Structure

```
online.avogadro.mearitaskerplugin/
├── MainActivity.java              # Minimal placeholder
├── CommonUtils.java               # Video stream ID helper
├── SplashActivity.java            # Entry point, permissions, auto-login
├── app/
│   ├── MeariApplication.java     # SDK init (partnerId=8)
│   ├── MyFirebaseMessagingService.java  # FCM push notifications
│   ├── MyMessageHandler.java     # MQTT message handling
│   ├── SharedPreferencesHelper.java    # AES-encrypted credential storage
│   ├── LogDumper.kt              # Dump app logcat to Downloads for bug reports
│   └── Util.java                 # Metadata and date utilities
├── device/
│   ├── CamManager.java           # Core singleton for all camera ops
│   ├── DeviceListActivity.java   # Main screen with camera list
│   ├── DeviceListAdapter.java    # RecyclerView adapter with per-camera controls
│   ├── DeviceMonitorActivity.java # Live preview/playback
│   ├── LiveSnapshotTaker.java    # Headless live snapshot (Take Picture action)
│   ├── P2PSession.java           # One P2P connection with retries (also wakes battery cams)
│   ├── AwakeCameraAction.java    # Wake via P2P, then send a command (siren, light)
│   ├── DeviceSettingActivity.java # Per-camera settings
│   ├── DeviceCloudPlayActivity.java # Cloud storage playback
│   ├── AddDeviceActivity.java    # QR code onboarding
│   ├── SettingsActivity.java     # App preferences
│   └── TrafficManagerActivity.java # Traffic/data management
├── tasker/
│   ├── CameraResolver.kt             # Selector→camera list resolution (glob, ID, name)
│   ├── TriggerCameraLightActionHelper.kt  # Turn on light + AbstractCameraActionConfig base class + HelperHolder interface
│   ├── BasicActionHelper.kt      # Enable PIR detection
│   ├── ActivityConfigDisableAlarms.kt # Disable PIR detection
│   ├── EnableSirenActionHelper.kt    # Enable siren on all cameras
│   ├── DisableSirenActionHelper.kt   # Disable siren on all cameras
│   ├── DownloadLastCameraImageActionHelper.kt # Download alert image
│   ├── DownloadLastCameraVideoActionHelper.kt # Download alert video
│   ├── TakePictureActionHelper.kt    # Take live snapshot
│   ├── TriggerCameraSirenActionHelper.kt  # Fire siren on camera(s)
│   ├── BlockingCameraAction.kt       # runBlockingCameraAction: sync bridge (CountDownLatch, 45s)
│   ├── CameraActionOutput.kt         # Shared @TaskerOutputObject output (%result)
│   ├── DownloadLastCameraImageInput.kt    # Input: cameraID, cameraName
│   ├── DownloadLastCameraImageOutput.kt   # Output: image file path
│   ├── DownloadLastCameraVideoOutput.kt   # Output: video file path
│   └── events/
│       ├── ActivityConfigCameraAlarmEvent.kt  # Event config UI
│       ├── CameraAlarmRaiser.kt       # Triggers Tasker events
│       ├── CameraAlarmInfo.java       # Event data: deviceName, deviceID
│       └── AnyNotificationReceiver.java # Legacy C2DM receiver
├── user/
│   ├── LoginActivity.java        # Login screen
│   ├── RegisterActivity.java     # Account registration
│   ├── CloudStatusActivity.java  # Cloud subscription status
│   └── BuyCloudServiceActivity.java # Cloud purchase
├── bean/                          # Data models for traffic management
│   ├── TrafficNumberBean.java
│   ├── TrafficOrderBean.java
│   └── TrafficPacketBean.java
└── alipay/                        # Unused payment utilities
    ├── Base64.java
    ├── SignUtils.java
    └── PayResult.java
```

### Core Components

#### 1. CamManager (`device/CamManager.java`)
Central singleton (`CamManager.get(context)`) for all camera operations.

**Initialization & Auth:**
- `loginAndInitList(IDoSomething)` - Login with stored credentials, fetch device list
- Device list is cached with 120-second refresh interval

**Bulk operations** (operate on a list of cameras, with optional `ICameraOperationCallback`):
- `enableAllCameras(List<CameraInfo>)` - Enable PIR detection
- `disableAllCameras(List<CameraInfo>)` - Disable PIR detection
- `enableAllCameraAlarms(List<CameraInfo>)` - Enable siren alarm
- `disableAllCameraAlarms(List<CameraInfo>)` - Disable siren alarm

**Selector-based operations** (login + resolve selector via `CameraResolver` + action):
- `enableCamerasPIR(selector, callback)` - Enable PIR detection
- `disableCamerasPIR(selector, callback)` - Disable PIR detection
- `fireSirenOnCameras(selector, callback)` - Fire siren (P2P wake-up)
- `turnOnLightOnCameras(selector, callback)` - Turn on light (P2P wake-up)

**List-based operations** (operate on a pre-resolved list of cameras):
- `fireSirenOnCameras(List<CameraInfo>, callback)` - Fire siren via `wakeAndRunOnCameras` (callback nullable: null = per-camera toasts only)
- `turnOnLightOnCameras(List<CameraInfo>, callback)` - Turn on light via `wakeAndRunOnCameras`

**No-arg bulk operations** (login first, then operate on all cached cameras):
- `enableAllCameraAlarms()`, `disableAllCameraAlarms()`

**Single-camera operations** (with `ISetDeviceParamsCallback`):
- `enableSingleCameraPIR(context, cameraID, callback)`
- `disableSingleCameraPIR(context, cameraID, callback)`
- `enableSingleCameraAlarm(context, cameraID, callback)`
- `disableSingleCameraAlarm(context, cameraID, callback)`

**Image/Media:**
- `takeAPicture(context, cameraID, listener)` - Live snapshot (50s budget), delegated to `LiveSnapshotTaker` (device/): P2P connect (the native connect wakes battery cameras by itself, no REST wake), soft-decoded off-screen preview on the highest-resolution bps2 stream (`CommonUtils.getMaxResolutionStreamId`), 2s settle, native JPEG snapshot to cache, then copied to Pictures via MediaStore; returns the file path
- `getLastAlertImage(cameraID, callback)` - Latest alert image from today
- `getLastAlertImage(date, cameraID, callback)` - Alert image from specific date (searches up to 10 days back)
- `getLastAlertVideo(cameraID, callback)` / `getLastAlertVideo(date, cameraID, callback)` - Latest alert video: picks the most recent alarm message with a non-empty `videoUrl` segment list (searches up to 10 days back), downloads it to Movies/*.mp4 via `downloadAlertVideo`, returns the local path in `CameraInfo.firmID` (same hack as images)
- `downloadAlertVideo(segments, cameraSN)` - Static, blocking: builds a local .m3u8 from the HLS segments and remuxes to mp4 via the SDK's bundled ffmpeg (`SdkUtils.downloadMp4FromM3U8`, decKey = `formatLicenceId(SN)` with keyless retry); serialized by `FFMPEG_LOCK`
- `getImageBytes(imageUrl)` - Static method to download image bytes from URL

#### 2. Tasker Actions (`tasker/` package)

Each action follows the pattern: `ActionHelper.kt` (+ embedded `ActionRunner`) + config Activity extending `AbstractCameraActionConfig`.

All config Activities with camera selector extend `AbstractCameraActionConfig` (in `TriggerCameraLightActionHelper.kt`), which provides:
- Camera dropdown spinner (populated via `CamManager.loginAndInitList`)
- EditText for camera ID/selector
- Configurable hint and help text via `open val editHint` / `open val helpText`
- Constants: `HINT_GROUP`/`HELP_GROUP` (for group-capable actions), `HINT_SINGLE`/`HELP_SINGLE` (for single-camera actions)

Helpers must implement `HelperHolder` interface (defines `finishForTasker()` and `onCreate()`).

| Action | Helper | Config Activity | Input/Output | Description |
|--------|--------|-----------------|--------------|-------------|
| Enable PIR | `BasicActionHelper` | `ActivityConfigBasicAction` | `DownloadLastCameraImageInput` → `CameraActionOutput` | Enable motion detection (supports selectors: `*`, name, glob, ID) |
| Disable PIR | `DisableAlarmsHelper` | `ActivityConfigDisableAlarms` | `DownloadLastCameraImageInput` → `CameraActionOutput` | Disable motion detection (supports selectors) |
| Enable Siren | `EnableSirenActionHelper` | `ActivityConfigEnableSirenAction` | `Unit` → `CameraActionOutput` | Enable siren alarm on all cameras |
| Disable Siren | `DisableSirenActionHelper` | `ActivityConfigDisableSirenAction` | `Unit` → `CameraActionOutput` | Disable siren alarm on all cameras |
| Download Alert Image | `DownloadLastCameraImageActionHelper` | `ActivityConfigDownloadLastCameraImageAction` | `DownloadLastCameraImageInput` → `DownloadLastCameraImageOutput` | Download latest alert image (30s timeout, single camera only) |
| Download Alert Video | `DownloadLastCameraVideoActionHelper` | `ActivityConfigDownloadLastCameraVideoAction` | `DownloadLastCameraImageInput` → `DownloadLastCameraVideoOutput` | Download latest alert video, cloud-hosted (55s timeout, single camera only) |
| Take Picture | `TakePictureActionHelper` | `ActivityConfigTakePictureAction` | `DownloadLastCameraImageInput` → `DownloadLastCameraImageOutput` | Capture live snapshot at full resolution (50s budget / 55s runner wait, single camera only) |
| Fire Siren | `TriggerCameraSirenActionHelper` | `ActivityConfigTriggerSirenAction` | `DownloadLastCameraImageInput` → `CameraActionOutput` | Fire siren (supports selectors, P2P wake-up) |
| Turn On Light | `TurnOnLightActionHelper` | `ActivityConfigTurnOnLightAction` | `DownloadLastCameraImageInput` → `CameraActionOutput` | Turn on camera light (supports selectors, P2P wake-up) |

**Input/Output classes:**
- `DownloadLastCameraImageInput` - Fields: `cameraID` (String), `cameraName` (String)
- `DownloadLastCameraImageOutput` - Fields: image file path
- `CameraActionOutput` - Fields: `result` (String, `%result`) — shared output for actions with no natural output (see Async Operations: MacroDroid requires a real output class)

**CameraResolver** (`tasker/CameraResolver.kt`):
Resolves a selector string to a list of matching `CameraInfo` objects:
- `null` / `""` / `"*"` → all cameras
- Purely numeric string → exact match on `deviceID`
- String containing `"*"` (but not just `"*"`) → glob match on `deviceName` (case-insensitive)
- Non-numeric string without `"*"` → exact match on `deviceName` (case-insensitive)

#### 3. Tasker Event

| Event | Config Activity | Data Class | Tasker Variables |
|-------|-----------------|------------|------------------|
| Camera Alarm | `ActivityConfigCameraAlarmEvent` | `CameraAlarmInfo` | `%deviceName`, `%deviceID` |

Triggers when a camera detects motion/person and sends an alert via Firebase/MQTT.

#### 4. Device List UI (`device/DeviceListActivity.java` + `DeviceListAdapter.java`)

Main screen showing all cameras in a RecyclerView.

**5 Toolbar buttons** (operate on currently filtered cameras):
1. `imageEnableDetection` (`@mipmap/camera_play`) - Enable PIR on all filtered cameras
2. `imageDisableDetection` (`@mipmap/camera_pause`) - Disable PIR on all filtered cameras
3. `imageEnableSiren` (`@mipmap/enable_siren`) - Enable siren on all filtered cameras
4. `imageDisableSiren` (`@mipmap/disable_siren`) - Disable siren on all filtered cameras
5. `imageFireAlarm` (`@mipmap/play_alarm`) - Fire siren on all filtered cameras (requires confirmation dialog)

**Per-camera inline controls** (in DeviceListAdapter):
- PIR toggle icon (camera_play / camera_pause) - clickable, toggles motion detection
- Siren toggle icon (enable_siren / disable_siren) - clickable, toggles siren alarm
- Fire siren button (play_alarm) - fires siren on single camera with confirmation dialog (same `fireSirenOnCameras(selector)` path as the Tasker action)
- Visual feedback: icons update per-camera as operations complete via `ICameraOperationCallback`

**TabLayout filtering:**
- Dynamically creates tabs from the first word of each camera name
- "ALL" tab shows all cameras
- Controlled by `PREF_GROUP_BY_FIRST_WORD` setting (default: false)

**Other behaviors:**
- Waits for MeariIotManager initialization before enabling controls
- Auto-accepts shared camera invitations (via MyMessageHandler)

#### 5. Notification & Event Flow

**Firebase path:**
1. `MyFirebaseMessagingService.onMessageReceived()` extracts `deviceName` and `deviceID` from message data
2. Creates `CameraAlarmInfo` and calls `CameraAlarmRaiser.raiseAlarmEvent()`
3. `CameraAlarmRaiser` triggers `triggerTaskerEventCameraAlarm()` which notifies Tasker

**Legacy C2DM path:**
1. `AnyNotificationReceiver.onReceive()` extracts same fields from Intent extras
2. Raises event via `CameraAlarmRaiser`

**MQTT path (MyMessageHandler):**
- Handles: login on other devices, device sharing, doorbell calls, family messages, cloud service disconnection, permission changes
- Auto-accepts device share requests: `dealShareMessage(msgID, 1, callback)`

**Token management:**
- `MyFirebaseMessagingService.startListening(context)` - Initializes Firebase, gets FCM token, posts to cloud via `MeariUser.getInstance().postPushToken(1, token)`
- `onNewToken()` - Re-logs in and re-posts token

### Login & Authentication Flow

**SplashActivity (entry point):**
1. Requests battery optimization exemption, storage, and wake-lock permissions
2. Attempts login with stored credentials
3. On success → DeviceListActivity; on failure → LoginActivity

**LoginActivity:**
1. User enters: country, phone code, username, password
2. Calls `MeariUser.getInstance().loginWithAccount(country, code, username, password, callback)`
3. On success: stores credentials encrypted, starts Firebase listening, navigates to DeviceListActivity
4. On error: shows toast, clears stored credentials

**Credential storage (SharedPreferencesHelper):**
- Encrypts/decrypts using AES with hardcoded key
- Stored keys: `username`, `password`, `country`, `code`

### App Settings (`device/SettingsActivity.java`)

| Preference Key | Type | Default | Description |
|----------------|------|---------|-------------|
| `PREF_SHOW_CAMERA_ID` | Boolean | true | Show camera ID in device list |
| `PREF_GROUP_BY_FIRST_WORD` | Boolean | false | Enable tab filtering by camera name prefix |

Also hosts a "save log" button that calls `LogDumper.dumpToDownloads()` (app/LogDumper.kt): dumps the app's own logcat (including TaskerPluginLibrary and Meari SDK lines) to a timestamped text file in Downloads, for bug reports.

## Key Patterns

### Camera Operation Pattern
Selector-based Tasker actions follow:
1. Login with stored credentials
2. Fetch/cache device list (120s cache)
3. Resolve selector to camera list via `CameraResolver.resolve()`
4. Set each camera as current device
5. Execute operation via Meari SDK

For wake-up operations (siren, light), `wakeAndRunOnCameras()` runs an `AwakeCameraAction` per camera in parallel: it opens a `P2PSession` (the native connect wakes the camera and succeeds only once it answers, ~3s), sends the command as soon as that camera is awake, then closes the session. If P2P fails or exceeds 20s the command is sent anyway; if this app already holds a session to the camera it is sent immediately. The Tasker actions and both in-app fire-siren buttons (toolbar and per-camera) share this path. No fixed sleeps, no REST wake/status polling (rejected by the server since 2026-09).

### Async Operations
- EVERY Tasker action MUST declare a real `@TaskerOutputObject` output class (see `CameraActionOutput`): MacroDroid never completes plugin actions whose output type is `Unit` (no output variables) — the macro hangs forever on the action. Actions with real outputs (e.g. Download Alert Image) always worked. Do NOT use the `NoOutput` runner/config-helper variants.
- Never remove/replace the library's `BroadcastReceiverAction` via manifest `tools:node="remove"`: hosts (MacroDroid) cache the receiver component at action-config time and send the fire broadcast explicitly to it — replacing it breaks all existing configured actions.
- Tasker action runners are synchronous: they block on `runBlockingCameraAction` (tasker/BlockingCameraAction.kt, CountDownLatch, 45s timeout) until the CamManager callback fires, then return `CameraActionOutput` (%result).
- Bulk operations report aggregate completion via `doSomethingOnCamerasAndReport` (event fires once, after ALL cameras answered; onFailed carries "N/M cameras failed")
- CamManager error paths (login failed, no credentials, device list failure) must always invoke the caller's callback, not just Toast — otherwise the blocking runners time out
- Toasts in CamManager go through `toast()` (main-looper Handler): SDK callbacks can arrive on native non-Looper threads where `Toast.makeText` throws
- Most camera operations use callbacks (`ISetDeviceParamsCallback`)
- Image downloads use `AsyncTask` pattern
- Device wake-up (siren, light, live snapshot) goes through `P2PSession`: one session per camera at a time (`P2PSession.open` returns null if busy), main looper only
- Image download timeouts: 30s for alert images, 55s for alert videos, 55s for live pictures

## Security Considerations

- **Credential Storage**: AES-encrypted credentials in SharedPreferences
- **Secondary Account Requirement**: Must use dedicated CloudEdge account (not primary)
- **Shared Access**: Cameras must be shared from primary to secondary account
- **Network**: Uses CloudEdge cloud service and AWS IoT for device communication

## SDK Dependencies

- Meari SDK (core-sdk-device, core-sdk-meari AAR files in libs/)
- TaskerPluginLibrary v0.4.10
- Firebase Messaging v23.4.1 + DirectBoot v23.4.1
- AWS IoT Android SDK v2.16.13
- Eclipse Paho MQTT v3
- Glide v4.11.0 (image loading)
- RxJava2 + RxAndroid
- Google Material Components

## SDK Notes (from decompiled core-sdk-meari-500-20230801.aar)

### Stream IDs
Two separate stream families exist for live preview:
- **Native streams (0, 1)**: Direct P2P main/sub stream, used only when `vst==1` or the camera has no `bps2`. The official app never requests them on bps2 (battery) cameras.
- **bps2 streams (100–104)**: key k of `bps2` = stream 100+k; official labels 100=SD, 101=HD, 102=FHD (renamed QHD/5MP when ≥2000px wide), 103=UHD. `getDefaultStreamId()` picks the LOWEST key (SD, as the official default) — used by DeviceMonitorActivity; `getMaxResolutionStreamId()` picks the largest w×h — used by `takeAPicture()`.
- **Adaptive stream (105)**: Available if `cameraInfo.getAdb()==1` and `ver>=81`.

`bps2` field is a JSON like `{"0":"640x360@15","2":"2304x1296@15"}` — width×height@fps per stream key (verified on a real battery cam: 100=640x360, 102=2304x1296, plus 105=auto). The SDK passes it to native as `{"100":{"w":..,"h":..},...}` in the P2P connect string.

### Live snapshot (headless)
- The native P2P connect sends an `awaken` itself (hirsdk UDP to the Meari server), so battery cameras need no REST wake-up. `MeariIotManager.wakeDevice`/`getDeviceStatusGet` (`/openapi/device/awaken`, `/status`) use the old signature and fail since the 2026-09 server change; the official app now signs awaken with token/t/clientid and no longer calls `/status` at all
- Off-screen capture requires software decoding (`controller.enableHardDecode(false)`, per controller): the native decoder memcpy's YUV into the `PPSGLSurfaceView` renderer buffers (no GL needed) and `snapshot()` → native `FFmpegPlayer::take_snapshot` encodes the last frame to JPEG at stream resolution. In HARD mode `snapshot()` waits for an OpenGL draw that never happens off-screen
`MeariDeviceUtil.getVideoStreamId(cameraInfo)` returns supported native stream IDs (0,1) via `bps` bitmask.

### Alert videos (cloud event clips)
- `getAlertMsgWithVideo` (endpoint `/v3/app/event/list`) already returns, per `DeviceAlarmMessage`, a `List<VideoInfo>` (`getVideoUrl()`): ordered HLS `.ts` segment URLs `{url, duration}`; empty when the event has no cloud recording
- Download recipe (used by `CamManager.downloadAlertVideo`): `SdkUtils.getM3U8Path(segments, m3u8Path)` writes a local playlist, then `SdkUtils.downloadMp4FromM3U8(m3u8, mp4, decKey)` runs the bundled native ffmpeg (`MeariFFmpeg.ffmpegCmd`, libmrplayer.so: `x [-dec KEY] -i m3u8 -c copy -y mp4`) producing a plain mp4; returns 0 on success, deletes the m3u8 on success and the mp4 on failure
- Decryption key = `SdkUtils.formatLicenceId(cameraInfo.getSnNum())` (9-char SNs get zero-padded to 20) — same key `DeviceCloudPlayActivity` feeds the cloud player via `setDecKey`; unencrypted setups need no `-dec`
- `ffmpegCmd` is blocking, has no timeout/cancel and is not known to be reentrant: call from a worker thread, serialize invocations

### Alert image / recording resolution (IoT commands, to investigate)
- `MeariUser.setShotResolution(int resolution, int connectType, callback)` — IoT cmd "247": sets resolution of camera-side alert snapshots
- `MeariUser.setRecordResolution(int resolution, int connectType, callback)` — IoT cmd "249": sets resolution of camera-side video recordings
- `connectType` distinguishes IoT hub vs direct connection
- These affect what `getLastAlertImage()` returns, not the live P2P stream

## Important Limitations

- Single login session: CloudEdge doesn't allow concurrent logins
- Wake-up delays: Battery cameras need a few seconds to come online; the P2P connect waits for them (up to 3 attempts)
- Image decryption: Uses proprietary MeariMediaUtil.decodePic()
- Device list cache: 120-second refresh interval
