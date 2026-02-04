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

## Architecture Overview

### Core Components

1. **CamManager** (`device/CamManager.java`) - Central singleton for camera operations
   - Handles authentication with CloudEdge cloud service
   - Manages device list and camera state
   - Provides methods for camera control (enable/disable PIR, sirens, lights)
   - Implements image download from camera alerts

2. **Tasker Integration** (`tasker/` package)
   - Uses TaskerPluginLibrary for Tasker/MacroDroid integration
   - Each action has its own Helper, Runner, and Configuration Activity
   - Pattern: `ActionHelper.kt` + `ActionRunner.kt` + `ActivityConfigAction.kt`

3. **Device List UI** (`device/DeviceListActivity.java` + `DeviceListAdapter.java`)
   - Main screen showing all cameras with bulk control buttons (enable/disable PIR, enable/disable siren, fire all sirens)
   - Bulk fire siren requires AlertDialog confirmation
   - Tab-based filtering by first word of camera name (configurable via settings)
   - Per-camera inline controls: toggle PIR detection, toggle siren, fire siren on single camera (with confirmation dialog)
   - Waits for MeariIotManager initialization before enabling controls

4. **Event System** (`tasker/events/`)
   - Firebase messaging for camera notifications
   - `CameraAlarmRaiser.kt` converts Firebase messages to Tasker events
   - `AnyNotificationReceiver.java` handles incoming push notifications

### Key Patterns

#### Tasker Action Pattern
Each Tasker action follows this structure:
- Configuration Activity: UI for action setup
- Action Helper: Bridges config and runner
- Action Runner: Executes the actual camera operation via CamManager

#### Camera Operation Pattern
All camera operations follow:
1. Login with stored credentials
2. Fetch/cache device list
3. Find target camera by ID
4. Set camera as current device
5. Execute operation via Meari SDK

#### Async Operations
- Most camera operations use callbacks (`ISetDeviceParamsCallback`)
- Image downloads use `AsyncTask` pattern
- Device wake-up includes hardcoded 10-second delays

## Security Considerations

- **Credential Storage**: App stores encrypted CloudEdge credentials locally
- **Secondary Account Requirement**: Must use dedicated CloudEdge account (not primary)
- **Shared Access**: Cameras must be shared from primary to secondary account
- **Network**: Uses CloudEdge cloud service and AWS IoT for device communication

## Development Notes

### SDK Dependencies
- Meari SDK (core-sdk-device/meari AAR files in libs/)
- TaskerPluginLibrary for automation integration
- Firebase for push notifications
- AWS SDK for IoT device communication

### Target Configuration
- Min SDK: 24 (Android 7.0)
- Target SDK: 33 (Android 13)
- Compile SDK: 34 (Android 14)
- Architecture: armeabi-v7a, arm64-v8a

### Important Limitations
- Single login session: CloudEdge doesn't allow concurrent logins
- Wake-up delays: Battery cameras need time to come online (10s hardcoded)
- Image decryption: Uses proprietary MeariMediaUtil.decodePic()