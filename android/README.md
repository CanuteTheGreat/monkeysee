# MonkeySee Android App

Turn your Android phone into a webcam and IP camera using RTSP streaming.

## Features

- Camera preview with CameraX
- RTSP server for streaming video
- Automatic IP address detection
- Simple UI with start/stop controls

## Requirements

- Android 7.0 (API 24) or higher
- Camera permission
- Network access

## Building

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK with API 34

### Build Steps

1. Open the project in Android Studio:
   ```bash
   cd android
   # Then open this directory in Android Studio
   ```

2. Wait for Gradle sync to complete

3. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```

4. Install on device:
   ```bash
   ./gradlew installDebug
   ```

   Or use Android Studio's run button.

### Command Line Build (without Android Studio)

If you don't have Android Studio, you can build from the command line:

1. Install Android SDK command-line tools from:
   https://developer.android.com/studio#command-line-tools-only

2. Set environment variables:
   ```bash
   export ANDROID_HOME=/path/to/android-sdk
   export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
   ```

3. Accept licenses:
   ```bash
   sdkmanager --licenses
   ```

4. Build:
   ```bash
   cd android
   chmod +x gradlew
   ./gradlew assembleDebug
   ```

## Usage

1. Launch the MonkeySee app on your Android phone
2. Grant camera permission when prompted
3. The app will display:
   - Camera preview
   - RTSP URL (e.g., `rtsp://192.168.1.100:8554/camera`)
   - Status (Stopped/Streaming)

4. Tap "Start Streaming" to begin broadcasting

5. Use the displayed RTSP URL in:
   - MonkeySee desktop client
   - VLC Media Player
   - ffplay
   - Any RTSP-compatible client

## Testing the Stream

### With VLC Media Player

1. Open VLC
2. Media → Open Network Stream
3. Enter the RTSP URL from the app
4. Click Play

### With ffplay

```bash
ffplay rtsp://192.168.1.100:8554/camera
```

### With MonkeySee Desktop Client

See the desktop client README for instructions.

## Network Requirements

- Phone and client must be on the same network (WiFi recommended)
- Default port: 8554 (RTSP)
- Firewall may need to allow incoming connections on port 8554

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/monkeysee/app/
│   │   │   ├── MainActivity.kt           # Main UI and camera setup
│   │   │   └── rtsp/
│   │   │       └── RtspServer.kt         # RTSP protocol implementation
│   │   ├── res/                          # Resources (layouts, strings, etc.)
│   │   └── AndroidManifest.xml           # App configuration
│   └── build.gradle.kts                  # App dependencies
├── build.gradle.kts                      # Project build config
└── settings.gradle.kts                   # Project settings
```

## Implementation Details

### Camera Capture

Uses Android CameraX API for camera access:
- `Preview` use case for on-screen preview
- `ImageAnalysis` use case for frame access
- Back camera as default

### RTSP Server

The RTSP server implements the following methods:
- `OPTIONS` - Capability negotiation
- `DESCRIBE` - SDP (Session Description Protocol) response
- `SETUP` - Session setup
- `PLAY` - Start streaming
- `TEARDOWN` - Stop streaming

### Video Format

- Container: RTSP/RTP
- Codec: H.264 (placeholder, encoding not yet implemented)
- Resolution: Matches camera output (typically 1280x720 or 1920x1080)
- Format: YUV420

## Current Limitations

This is a functional prototype with the following limitations:

1. **Video Encoding**: H.264 encoding via MediaCodec is not yet implemented
   - Currently only handles RTSP protocol negotiation
   - Frame encoding to H.264 needs to be added

2. **RTP Packetization**: RTP packet creation is not implemented
   - Need to packetize H.264 NAL units into RTP packets
   - Implement proper RTP headers

3. **UDP Streaming**: Currently uses TCP for RTSP control
   - Should add UDP transport for RTP data

4. **Audio**: No audio capture or streaming

5. **Resolution Control**: No UI to select resolution/fps

## Next Steps

To complete the implementation:

1. **Add H.264 Encoding**:
   ```kotlin
   // Use MediaCodec to encode frames
   val encoder = MediaCodec.createEncoderByType("video/avc")
   // Configure and start encoder
   // Feed YUV frames from ImageAnalysis
   // Get H.264 NAL units
   ```

2. **Implement RTP Packetization**:
   - Create RTP packets from H.264 NAL units
   - Add RTP headers (sequence number, timestamp, SSRC)
   - Send via UDP socket

3. **Add UDP Transport**:
   ```kotlin
   val udpSocket = DatagramSocket()
   // Send RTP packets to client
   ```

4. **Add Audio Support**:
   - Capture audio with AudioRecord
   - Encode to AAC with MediaCodec
   - Stream alongside video

5. **UI Improvements**:
   - Resolution selector
   - FPS selector
   - Front/back camera toggle
   - Connection indicator

## Troubleshooting

### Camera Not Starting

- Check camera permission is granted
- Try restarting the app
- Check logcat for errors: `adb logcat -s MonkeySee`

### Cannot Connect to Stream

- Verify phone and client are on same network
- Check firewall settings
- Verify IP address matches what's shown in app
- Check port 8554 is not blocked

### Build Errors

**Gradle sync failed**:
- Check internet connection
- Invalidate caches: File → Invalidate Caches / Restart

**SDK not found**:
- Install required SDK versions in SDK Manager
- Set `ANDROID_HOME` environment variable

**Kotlin version mismatch**:
- Update Kotlin plugin version in `build.gradle.kts`

## Debugging

Enable verbose logging:

```bash
adb logcat -s MonkeySee:D
```

View all app logs:

```bash
adb logcat | grep com.monkeysee.app
```

## Contributing

Contributions welcome! Priority areas:

- [ ] MediaCodec H.264 encoding
- [ ] RTP packetization
- [ ] UDP transport
- [ ] Audio capture and encoding
- [ ] UI improvements (resolution selector, etc.)
- [ ] Battery optimization
- [ ] Background service for streaming

## License

MIT License
