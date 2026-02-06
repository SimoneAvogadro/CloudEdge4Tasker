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
│   └── Util.java                 # Metadata and date utilities
├── device/
│   ├── CamManager.java           # Core singleton for all camera ops
│   ├── DeviceListActivity.java   # Main screen with camera list
│   ├── DeviceListAdapter.java    # RecyclerView adapter with per-camera controls
│   ├── DeviceMonitorActivity.java # Live preview/playback
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
│   ├── TakePictureActionHelper.kt    # Take live snapshot
│   ├── TriggerCameraSirenActionHelper.kt  # Fire siren on camera(s)
│   ├── DownloadLastCameraImageInput.kt    # Input: cameraID, cameraName
│   ├── DownloadLastCameraImageOutput.kt   # Output: image file path
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
- `fireAllSirenAlarms(List<CameraInfo>)` - Fire sirens

**Selector-based operations** (login + resolve selector via `CameraResolver` + action):
- `enableCamerasPIR(selector, callback)` - Enable PIR detection
- `disableCamerasPIR(selector, callback)` - Disable PIR detection
- `fireSirenOnCameras(selector, callback)` - Fire siren (10s wake-up)
- `turnOnLightOnCameras(selector, callback)` - Turn on light (10s wake-up)

**List-based operations** (operate on a pre-resolved list of cameras):
- `fireSirenOnCameras(List<CameraInfo>, callback)` - Fire siren via `wakeAndDoSomethingOnCameras`
- `turnOnLightOnCameras(List<CameraInfo>, callback)` - Turn on light via `wakeAndDoSomethingOnCameras`

**No-arg bulk operations** (login first, then operate on all cached cameras):
- `enableAllCameraAlarms()`, `disableAllCameraAlarms()`

**Single-camera operations** (with `ISetDeviceParamsCallback`):
- `enableSingleCameraPIR(context, cameraID, callback)`
- `disableSingleCameraPIR(context, cameraID, callback)`
- `enableSingleCameraAlarm(context, cameraID, callback)`
- `disableSingleCameraAlarm(context, cameraID, callback)`
- `fireSirenAlarm(context, cameraID, callback)` - Includes 10s wake-up delay

**Image/Media:**
- `takeAPicture(context, cameraID, listener)` - Live snapshot (90s timeout)
- `getLastAlertImage(cameraID, callback)` - Latest alert image from today
- `getLastAlertImage(date, cameraID, callback)` - Alert image from specific date (searches up to 10 days back)
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
| Enable PIR | `BasicActionHelper` | `ActivityConfigBasicAction` | `DownloadLastCameraImageInput` → `Unit` | Enable motion detection (supports selectors: `*`, name, glob, ID) |
| Disable PIR | `DisableAlarmsHelper` | `ActivityConfigDisableAlarms` | `DownloadLastCameraImageInput` → `Unit` | Disable motion detection (supports selectors) |
| Enable Siren | `EnableSirenActionHelper` | `ActivityConfigEnableSirenAction` | `Unit` → `Unit` | Enable siren alarm on all cameras |
| Disable Siren | `DisableSirenActionHelper` | `ActivityConfigDisableSirenAction` | `Unit` → `Unit` | Disable siren alarm on all cameras |
| Download Alert Image | `DownloadLastCameraImageActionHelper` | `ActivityConfigDownloadLastCameraImageAction` | `DownloadLastCameraImageInput` → `DownloadLastCameraImageOutput` | Download latest alert image (30s timeout, single camera only) |
| Take Picture | `TakePictureActionHelper` | `ActivityConfigTakePictureAction` | `DownloadLastCameraImageInput` → `DownloadLastCameraImageOutput` | Capture live snapshot (90s timeout, single camera only) |
| Fire Siren | `TriggerCameraSirenActionHelper` | `ActivityConfigTriggerSirenAction` | `DownloadLastCameraImageInput` → `Unit` | Fire siren (supports selectors, 10s wake-up) |
| Turn On Light | `TurnOnLightActionHelper` | `ActivityConfigTurnOnLightAction` | `DownloadLastCameraImageInput` → `Unit` | Turn on camera light (supports selectors, 10s wake-up) |

**Input/Output classes:**
- `DownloadLastCameraImageInput` - Fields: `cameraID` (String), `cameraName` (String)
- `DownloadLastCameraImageOutput` - Fields: image file path

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
- Fire siren button (play_alarm) - fires siren on single camera with confirmation dialog
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

## Key Patterns

### Camera Operation Pattern
Selector-based Tasker actions follow:
1. Login with stored credentials
2. Fetch/cache device list (120s cache)
3. Resolve selector to camera list via `CameraResolver.resolve()`
4. Set each camera as current device
5. Execute operation via Meari SDK

For wake-up operations (siren, light), `wakeAndDoSomethingOnCameras()` wakes all matched cameras, waits 10s once, then executes the action on each.

### Async Operations
- Most camera operations use callbacks (`ISetDeviceParamsCallback`)
- Image downloads use `AsyncTask` pattern
- Device wake-up includes hardcoded 10-second delays (fireSirenOnCameras, turnOnLightOnCameras)
- Image download timeouts: 30s for alert images, 90s for live pictures

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

## Important Limitations

- Single login session: CloudEdge doesn't allow concurrent logins
- Wake-up delays: Battery cameras need time to come online (10s hardcoded)
- Image decryption: Uses proprietary MeariMediaUtil.decodePic()
- Device list cache: 120-second refresh interval
