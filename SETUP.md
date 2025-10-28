# MonkeySee Setup Guide

Complete guide to setting up MonkeySee on your system.

## Prerequisites

### For Android App

- Android Studio or Android SDK command-line tools
- Android device running Android 7.0 (API 24) or higher
- USB cable or wireless debugging enabled

### For Desktop Client (Linux)

- Rust toolchain (1.70+)
- FFmpeg development libraries
- v4l2loopback kernel module
- libclang (for Rust bindings)

## Step 1: Install System Dependencies

### Ubuntu/Debian

```bash
# Install FFmpeg libraries
sudo apt update
sudo apt install libavcodec-dev libavformat-dev libavutil-dev libavfilter-dev libavdevice-dev \
                 libswscale-dev libswresample-dev pkg-config

# Install ALSA libraries (for audio playback)
sudo apt install libasound2-dev

# Install v4l2loopback
sudo apt install v4l2loopback-dkms

# Install libclang for Rust bindings
sudo apt install libclang-dev

# Install Rust (if not already installed)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```

### Arch Linux

```bash
# Install FFmpeg
sudo pacman -S ffmpeg

# Install ALSA libraries (for audio playback)
sudo pacman -S alsa-lib

# Install v4l2loopback
sudo pacman -S v4l2loopback-dkms

# Install clang
sudo pacman -S clang

# Install Rust
sudo pacman -S rust
```

### Fedora/RHEL

```bash
# Install FFmpeg
sudo dnf install ffmpeg-devel

# Install ALSA libraries (for audio playback)
sudo dnf install alsa-lib-devel

# Install v4l2loopback
sudo dnf install v4l2loopback kmod-v4l2loopback

# Install clang
sudo dnf install clang-devel

# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

## Step 2: Set Up v4l2loopback

### Load the module temporarily

```bash
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

Verify it's loaded:

```bash
v4l2-ctl --list-devices
# Should show "MonkeySee (platform:v4l2loopback-010)"

ls -l /dev/video10
# Should show the device file
```

### Load automatically on boot (optional)

```bash
# Add to modules to load
echo "v4l2loopback" | sudo tee /etc/modules-load.d/v4l2loopback.conf

# Add module options
echo "options v4l2loopback devices=1 video_nr=10 card_label=\"MonkeySee\" exclusive_caps=1" | \
    sudo tee /etc/modprobe.d/v4l2loopback.conf

# Rebuild initramfs (Debian/Ubuntu)
sudo update-initramfs -u

# Or on Arch
sudo mkinitcpio -P

# Or on Fedora
sudo dracut --force
```

## Step 3: Build the Android App

### Option A: Using Android Studio

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to `monkeysee/android`
4. Wait for Gradle sync to complete
5. Connect your Android device via USB
6. Enable USB debugging on the device
7. Click Run (green play button) or `Shift+F10`

### Option B: Command Line

```bash
cd monkeysee/android

# Build debug APK
./gradlew assembleDebug

# Install to connected device
./gradlew installDebug

# Or build and install in one command
./gradlew installDebug

# The APK will also be at: app/build/outputs/apk/debug/app-debug.apk
```

## Step 4: Build the Desktop Client

```bash
cd monkeysee/desktop

# Build in release mode (optimized)
cargo build --release

# The binary will be at: target/release/monkeysee-client

# Optional: Install system-wide
sudo cp target/release/monkeysee-client /usr/local/bin/
```

## Step 5: Run MonkeySee

### On Android

1. Open the MonkeySee app on your phone
2. Grant camera permission when prompted
3. The app will display the RTSP URL (e.g., `rtsp://192.168.1.100:8554/camera`)
4. Tap "Start Streaming"
5. Keep the app in the foreground while streaming

### On Desktop

Make sure your phone and computer are on the same WiFi network!

```bash
# Replace PHONE_IP with the IP shown in the Android app
./target/release/monkeysee-client --url rtsp://PHONE_IP:8554/camera --device /dev/video10 --verbose
```

You should see output like:
```
INFO MonkeySee Client starting...
INFO RTSP URL: rtsp://192.168.1.100:8554/camera
INFO Virtual device: /dev/video10
INFO Virtual camera initialized
INFO Connected to RTSP stream
INFO H.264 decoder initialized
INFO Streaming... Press Ctrl+C to stop
INFO Streaming frame 30 (1280x720)
INFO Streaming frame 60 (1280x720)
```

### Enable Audio Playback (Optional)

To enable audio streaming from your phone's microphone:

```bash
./target/release/monkeysee-client \
    --url rtsp://PHONE_IP:8554/camera \
    --device /dev/video10 \
    --audio
```

With audio enabled, you'll see:
```
INFO Audio playback initialized: 48kHz stereo
INFO Audio playback enabled
INFO Streaming... Press Ctrl+C to stop
```

**Note**: Audio will play through your computer's default audio output device (speakers/headphones). The audio is for monitoring only - applications using the virtual camera will not receive the audio stream (only video).

**Requirements for audio**:
- Android app must have microphone permission granted
- Desktop client needs ALSA libraries installed (see Step 1)
- Both audio and video are streamed, but only video goes to the virtual camera

## Step 6: Use in Applications

### Test with ffplay

```bash
ffplay /dev/video10
```

### Use in Chrome/Firefox

1. Open a website that uses webcam (e.g., https://webcamtests.com)
2. Grant camera permission
3. Select "MonkeySee" or `/dev/video10` from the camera dropdown
4. Your phone's camera should appear!

### Use in Zoom/Teams/Discord

1. Open Settings → Video
2. Select "MonkeySee" or the v4l2loopback device as your camera
3. Your phone's camera will be used

### Use in OBS Studio

1. Add a new source → Video Capture Device (V4L2)
2. Select "MonkeySee" or `/dev/video10`
3. Your phone camera appears as a source

## Troubleshooting

### Android App Issues

**"Camera permission denied"**
- Go to Settings → Apps → MonkeySee → Permissions
- Enable Camera permission

**"Microphone permission denied"** (for audio)
- Go to Settings → Apps → MonkeySee → Permissions
- Enable Microphone permission
- Restart the app

**"Cannot bind to port"**
- Make sure no other RTSP server is running on port 8554
- Try restarting the app

**"No IP address shown"**
- Make sure WiFi is enabled
- Check that you're connected to a network
- Try toggling WiFi off/on

### Desktop Client Issues

**"libclang not found" during build**
```bash
sudo apt install libclang-dev  # Ubuntu/Debian
sudo pacman -S clang           # Arch
sudo dnf install clang-devel   # Fedora
```

**"Device not found: /dev/video10"**
```bash
# Check if v4l2loopback is loaded
lsmod | grep v4l2loopback

# If not, load it
sudo modprobe v4l2loopback devices=1 video_nr=10 card_label="MonkeySee" exclusive_caps=1
```

**"Permission denied" on /dev/video10**
```bash
# Add your user to the video group
sudo usermod -a -G video $USER

# Log out and log back in for changes to take effect

# Or temporarily:
sudo chmod 666 /dev/video10
```

**"Connection timeout" or "Connection refused"**
- Verify phone and computer are on the same WiFi network
- Check firewall isn't blocking port 8554
- Verify the IP address matches what's shown in the Android app
- Try pinging the phone: `ping PHONE_IP`

**"RTSP connection successful but no video"**
- Check that FFmpeg is properly installed: `ffmpeg -version`
- Enable verbose logging: `--verbose`
- Check that the Android app shows "Streaming" status

### Audio Issues

**"Failed to initialize audio playback" or "Could not run pkg-config"**
```bash
# Install ALSA development libraries
sudo apt install libasound2-dev  # Ubuntu/Debian
sudo pacman -S alsa-lib          # Arch
sudo dnf install alsa-lib-devel  # Fedora
```

**"No audio" when using --audio flag**
- Verify microphone permission is granted on Android
- Check audio device is working: `speaker-test -c 2`
- Enable verbose logging to see audio frame stats: `--verbose`
- Check Android app is actually capturing audio (look for audio waveform in UI)

**Audio stuttering or crackling**
- This is usually a network issue (WiFi congestion)
- Try moving closer to WiFi router
- Use 5GHz WiFi instead of 2.4GHz
- Close other apps using bandwidth

### Video Quality Issues

**Choppy or laggy video**
- Phone and computer should be on 5GHz WiFi, not 2.4GHz
- Make sure phone isn't in power saving mode
- Close other apps on the phone
- Reduce resolution in the client: `--width 640 --height 480`

**Video is rotated**
- The orientation is determined by how you hold the phone
- Hold the phone in landscape mode for correct orientation

## Advanced Configuration

### Change video resolution

Desktop client:
```bash
./target/release/monkeysee-client \
    --url rtsp://PHONE_IP:8554/camera \
    --device /dev/video10 \
    --width 1920 \
    --height 1080
```

Android app: Currently uses camera's default resolution (typically 1280x720). To change, modify `VideoEncoder` parameters in code.

### Change bitrate

Edit `android/app/src/main/kotlin/com/monkeysee/app/codec/VideoEncoder.kt`:

```kotlin
private val bitrate: Int = 4_000_000  // 4 Mbps instead of default 2 Mbps
```

Rebuild the app.

### Use different virtual camera device

```bash
# Create v4l2loopback at different device number
sudo modprobe v4l2loopback devices=1 video_nr=20

# Use in client
./target/release/monkeysee-client --url rtsp://... --device /dev/video20
```

### Multiple virtual cameras

```bash
# Create 2 devices
sudo modprobe v4l2loopback devices=2 video_nr=10,11

# Use different phones for each
./target/release/monkeysee-client --url rtsp://PHONE1_IP:8554/camera --device /dev/video10 &
./target/release/monkeysee-client --url rtsp://PHONE2_IP:8554/camera --device /dev/video11 &
```

## Performance Tips

1. **Use 5GHz WiFi** - Much better bandwidth and latency
2. **Keep apps close** - Phone and computer in same room reduces latency
3. **Close background apps** - Frees up phone resources
4. **Use lower resolution** - If you don't need 720p, use 480p for better performance
5. **Keep phone plugged in** - Video encoding uses significant battery

## Next Steps

- See [Android README](android/README.md) for Android development details
- See [Desktop README](desktop/README.md) for desktop development details
- Read the main [README](README.md) for architecture and contributing info

## Getting Help

If you encounter issues:

1. Check the troubleshooting section above
2. Enable verbose logging with `--verbose`
3. Check logs with `adb logcat -s MonkeySee` (Android)
4. Open an issue on GitHub with:
   - Your OS and version
   - Error messages
   - Steps to reproduce

Happy streaming! 📹
