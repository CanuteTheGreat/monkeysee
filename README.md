# MonkeySee

A cross-platform solution to turn your Android phone into a webcam and IP network camera.

## Features

### Core Streaming
- **Virtual Webcam**: Use your Android phone as a webcam on Linux (Windows/macOS via OBS)
- **Audio + Video**: Full A/V streaming with AAC audio and H.264 video
- **Auto-Reconnection**: Stream continues even if WiFi drops briefly
- **Low Latency**: Optimized streaming with minimal delay (~50-100ms)
- **IP Camera**: Stream camera feed over network using RTSP/RTP protocol

### Quality & Performance
- **Quality Presets**: CLI support for easy preset selection ⭐ NEW v0.3!
- **Dynamic Quality Control**: Server-side quality changes via RTSP ⭐ NEW v0.3!
- **Adaptive Bitrate**: Automatic quality adjustment based on network
- **Network Stats**: Real-time latency, jitter, packet loss tracking

### Recording & Monitoring
- **GUI Recording Controls**: Record streams directly from GUI ⭐ NEW v0.3!
- **Recording Status Display**: Real-time frames, file size, duration ⭐ NEW v0.3!
- **Stream Recording**: Save to MP4/MKV while viewing
- **Enhanced GUI**: Stats dashboard with FPS graphs

### Platform Support
- **Cross-Platform**: Desktop client written in Rust, works on all major platforms
- **Native Performance**: Android app built with Kotlin for optimal performance
- **Dual Interface**: CLI and beautiful GUI clients available

## Architecture

- **Android App** (`android/`): Captures camera feed and streams via RTSP server
- **Desktop Client** (`desktop/`): Receives RTSP stream and creates virtual webcam device

## Streaming Protocol

Uses RTSP/RTP for video streaming:
- Standard IP camera protocol
- Wide compatibility with media players and applications
- Reliable UDP or TCP transport

## Requirements

### Android App
- Android 6.0+ (API level 23+)
- Camera permission
- Network permission

### Desktop Client

**Linux:**
- v4l2loopback kernel module
- Install: `sudo apt install v4l2loopback-dkms` (Debian/Ubuntu)

**Windows:**
- Virtual camera driver (will be provided)

**macOS:**
- CoreMediaIO virtual camera plugin

## Quick Start

**For detailed setup instructions, see [SETUP.md](SETUP.md)**

### 1. Install dependencies (Linux)
```bash
# Quick setup (recommended)
./setup_dependencies.sh

# Or manual installation (Ubuntu/Debian)
sudo apt install libavcodec-dev libavformat-dev libavutil-dev libavdevice-dev \
                 libswscale-dev v4l2loopback-dkms libclang-dev pkg-config \
                 libasound2-dev ffmpeg

# Load v4l2loopback
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

### 2. Build Android App
```bash
cd android
./gradlew installDebug
```

### 3. Build Desktop Client
```bash
cd desktop
cargo build --release
```

### 4. Run
1. Start the Android app and tap "Start Streaming"
2. Note the RTSP URL displayed (e.g., `rtsp://192.168.1.100:8554/camera`)
3. On desktop:
   ```bash
   # Basic usage
   ./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10

   # With quality preset (NEW in v0.3!)
   ./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10 --quality high

   # With audio and recording
   ./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10 --audio --record stream.mp4

   # All features together
   ./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10 \
     --quality high --audio --adaptive-bitrate --record meeting.mp4
   ```
4. Use `/dev/video10` in any webcam application!
5. Check the stats logged every 10 seconds for streaming quality

**Quality Presets** (v0.3):
- `--quality low`: 480p @ 15fps (poor WiFi)
- `--quality medium`: 720p @ 24fps (normal WiFi)
- `--quality high`: 720p @ 30fps (good WiFi, default)
- `--quality ultra`: 1080p @ 30fps (5GHz WiFi)

## Documentation

### User Guides
- **[Complete Setup Guide](SETUP.md)** - Installation and configuration
- **[v0.3.0 Progress](V0.3_PROGRESS_SUMMARY.md)** - Latest features & status ⭐ NEW!
- **[v0.2.0 Features](FEATURES_V0.2_SUMMARY.md)** - v0.2 feature guide
- **[Testing Guide](TESTING_V0.2.md)** - Comprehensive testing checklist
- [Enhancements Summary](ENHANCEMENTS_SUMMARY.md) - v0.1 improvements

### Component Guides
- [Android App Details](android/README.md) - Android development
- [Desktop Client Details](desktop/README.md) - Rust/desktop development

### Platform Guides
- [Windows Virtual Camera](desktop/WINDOWS_VIRTUAL_CAMERA.md) - Implementation guide
- [macOS Virtual Camera](desktop/MACOS_VIRTUAL_CAMERA.md) - Implementation guide

## Project Status

This is a **working implementation** with full video streaming pipeline! The following components are implemented:

### Completed ✅
**Core Streaming**
- Project structure (Android + Rust workspace)
- Android app with CameraX integration
- **H.264 video encoding via MediaCodec (Android)**
- **AAC audio encoding via MediaCodec (Android)**
- **RTP packetization and UDP transport (Android)**
- RTSP protocol server and client with dual-track support
- **H.264 video decoding via FFmpeg (Desktop)**
- **AAC audio decoding via FFmpeg (Desktop)**
- **Audio playback via cpal (Desktop)**
- **Frame format conversion to YUV420 (Desktop)**

**Reliability & Performance**
- **Auto-reconnection with exponential backoff** ⭐ NEW!
- **Streaming statistics (FPS, bitrate)** ⭐ NEW!
- **Quality presets (LOW/MEDIUM/HIGH/ULTRA)** ⭐ NEW!
- **Configurable bitrate and resolution** ⭐ NEW!

**Platform Support**
- Linux v4l2loopback virtual camera (fully working)
- Windows/macOS virtual camera guides (implementation docs)
- Cross-platform virtual camera abstraction layer

**Interfaces & Testing**
- CLI interface with full argument parsing
- GUI interface with egui
- Unit tests for RTP handling
- Comprehensive documentation

### Optional Enhancements
- Windows DirectShow virtual camera (guide available)
- macOS CoreMediaIO virtual camera (guide available)
- Adaptive bitrate based on network conditions
- Recording to file (MP4)

## How It Works

1. **Android App**:
   - Captures camera frames using CameraX (30 fps)
   - Encodes frames to H.264 using MediaCodec
   - Runs RTSP server on port 8554 (TCP)
   - Handles RTSP protocol (OPTIONS, DESCRIBE, SETUP, PLAY)
   - Packetizes H.264 NAL units into RTP packets
   - Sends RTP packets via UDP to connected clients
   - Automatic SPS/PPS parameter set generation

2. **Desktop Client**:
   - Connects to RTSP server via TCP
   - Performs RTSP handshake to negotiate session
   - Receives RTP packets via UDP
   - Reassembles fragmented packets (FU-A)
   - Decodes H.264 frames using FFmpeg
   - Converts to YUV420 format
   - Writes frames to v4l2loopback virtual camera device

3. **Applications** (Zoom, Chrome, OBS, etc.):
   - Read from virtual camera device (e.g., /dev/video10)
   - See your phone's camera as a standard webcam
   - Works with any application that supports V4L2

## Testing the Implementation

### 1. Test with VLC
```bash
vlc rtsp://PHONE_IP:8554/camera
```

### 2. Test with ffplay
```bash
ffplay rtsp://PHONE_IP:8554/camera
```

### 3. Test the full pipeline
```bash
# On desktop (assuming v4l2loopback is loaded at /dev/video10)
./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10 --verbose

# Then test with any webcam app
ffplay /dev/video10
```

## Contributing

This project is perfect for learning about:
- RTSP/RTP protocols
- Video encoding/decoding
- Android MediaCodec API
- Virtual camera implementations
- Rust async programming

Pull requests welcome! See component READMEs for specific areas needing work.

## Similar Projects

- **DroidCam**: Commercial solution with similar functionality
- **IP Webcam**: Android app for IP camera streaming
- **scrcpy**: Android screen mirroring (different approach)

MonkeySee aims to be an open-source alternative with cross-platform support.

## License

MIT License
