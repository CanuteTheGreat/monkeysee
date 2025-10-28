# MonkeySee Quick Reference

One-page reference for common tasks and commands.

## Installation (Linux)

```bash
# Install dependencies
sudo apt install libavcodec-dev libavformat-dev libavutil-dev libavdevice-dev \
                 libswscale-dev v4l2loopback-dkms libclang-dev

# Load v4l2loopback
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1

# Build Android app
cd android && ./gradlew installDebug

# Build desktop client
cd desktop && cargo build --release
```

## Basic Usage

```bash
# 1. Start Android app, tap "Start Streaming", note IP address

# 2. Run desktop client (replace PHONE_IP with your phone's IP)
./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10

# 3. Use /dev/video10 in any app!
```

## Common Commands

### Desktop Client

```bash
# Basic usage
monkeysee-client --url rtsp://192.168.1.100:8554/camera

# Different device
monkeysee-client --url rtsp://192.168.1.100:8554/camera --device /dev/video20

# Different resolution
monkeysee-client --url rtsp://... --width 1920 --height 1080

# Verbose logging
monkeysee-client --url rtsp://... --verbose

# All options
monkeysee-client --help
```

### Test Stream

```bash
# With VLC
vlc rtsp://PHONE_IP:8554/camera

# With ffplay
ffplay rtsp://PHONE_IP:8554/camera

# Test virtual camera
ffplay /dev/video10
```

### v4l2loopback

```bash
# Load module
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1

# Check if loaded
lsmod | grep v4l2loopback

# List devices
v4l2-ctl --list-devices

# Unload module
sudo modprobe -r v4l2loopback

# Load on boot
echo "v4l2loopback" | sudo tee /etc/modules-load.d/v4l2loopback.conf
echo "options v4l2loopback devices=1 video_nr=10 card_label=\"MonkeySee\"" | \
    sudo tee /etc/modprobe.d/v4l2loopback.conf
```

## Android

```bash
# Build and install
cd android && ./gradlew installDebug

# View logs
adb logcat -s MonkeySee

# View all logs
adb logcat | grep monkeysee

# Uninstall
adb uninstall com.monkeysee.app
```

## Troubleshooting Quick Fixes

```bash
# "Device not found"
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee"

# "Permission denied" on /dev/video10
sudo chmod 666 /dev/video10
# OR (permanent)
sudo usermod -a -G video $USER

# "libclang not found"
sudo apt install libclang-dev

# "Connection refused"
# - Check phone and PC on same WiFi
# - Verify IP address
# - Check firewall
ping PHONE_IP

# "FFmpeg not found"
sudo apt install libavcodec-dev libavformat-dev libavutil-dev
```

## File Locations

```
Android APK:      android/app/build/outputs/apk/debug/app-debug.apk
Desktop Binary:   desktop/target/release/monkeysee-client
Virtual Camera:   /dev/video10 (or configured device)
```

## Default Values

| Setting | Default | Notes |
|---------|---------|-------|
| RTSP Port | 8554 | Android server |
| RTP Port | 5000 | Client receives on this port |
| Video Device | /dev/video10 | Linux virtual camera |
| Resolution | 1280x720 | From camera |
| FPS | 30 | Fixed |
| Bitrate | 2 Mbps | Configurable in code |
| Codec | H.264 Baseline | Level 3.1 |

## Network Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 8554 | TCP | RTSP control |
| 5000 | UDP | RTP video data |

## Keyboard Shortcuts

| Keys | Action |
|------|--------|
| Ctrl+C | Stop client |

## Useful Links

- Setup Guide: [SETUP.md](SETUP.md)
- Android Details: [android/README.md](android/README.md)
- Desktop Details: [desktop/README.md](desktop/README.md)
- Implementation: [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)

## Quick Architecture

```
Android → CameraX → MediaCodec (H.264) → RTP → Network
   ↓
Network → RTP → FFmpeg (decode) → YUV420 → v4l2loopback → Apps
```

## Performance Tips

1. Use 5GHz WiFi (not 2.4GHz)
2. Keep phone and PC in same room
3. Close other apps on phone
4. Use lower resolution if choppy (--width 640 --height 480)
5. Keep phone plugged in

## Common Applications

### Chrome/Firefox
1. Visit webcamtests.com
2. Select "MonkeySee" or /dev/video10
3. Works!

### Zoom
1. Settings → Video
2. Select MonkeySee camera
3. Works!

### OBS
1. Add Source → Video Capture Device
2. Select /dev/video10
3. Works!

### Discord
1. Settings → Voice & Video
2. Select MonkeySee
3. Works!

## Building from Source

```bash
# Clone
git clone https://github.com/yourusername/monkeysee
cd monkeysee

# Android
cd android
./gradlew assembleDebug

# Desktop
cd desktop
cargo build --release
```

## System Requirements

**Android:**
- Android 7.0+ (API 24+)
- Camera
- WiFi

**Desktop:**
- Linux (Ubuntu 20.04+, Arch, Fedora, etc.)
- v4l2loopback kernel module
- FFmpeg libraries
- Rust 1.70+

## Status Indicators

**Android App:**
- "Stopped" → Not streaming
- "Streaming" → Active stream
- IP shown → Server is running

**Desktop Client:**
```
INFO Connected to RTSP stream → Connection OK
INFO Streaming frame 30 → Video flowing
WARN Decode error → Check network
ERROR Connection failed → Check IP/WiFi
```

## Getting Help

1. Check [SETUP.md](SETUP.md) troubleshooting
2. Run with `--verbose`
3. Check logs: `adb logcat -s MonkeySee`
4. Open GitHub issue with error details

---

**Need more details?** See [README.md](README.md) or [SETUP.md](SETUP.md)
